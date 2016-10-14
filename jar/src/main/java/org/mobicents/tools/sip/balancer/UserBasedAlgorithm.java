/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.ResponseExt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sip.ListeningPoint;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.sip.header.HeaderAddress;
import javax.sip.address.SipURI;
import javax.sip.address.TelURL;
import javax.sip.address.URI;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.log4j.Logger;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class UserBasedAlgorithm extends DefaultBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(UserBasedAlgorithm.class.getCanonicalName());

	protected String headerName = "To";
	protected ConcurrentHashMap<String, SIPNode> userToMap = new ConcurrentHashMap<String, SIPNode>();
	protected ConcurrentHashMap<String, Long> headerToTimestamps = new ConcurrentHashMap<String, Long>();
	protected AtomicInteger nextNodeCounter = new AtomicInteger(0);
	protected int maxCallIdleTime = 500;
	protected boolean groupedFailover = false;
	protected Timer cacheEvictionTimer = new Timer();
	
	@Override
	public void processInternalRequest(Request request) {
		logger.debug("internal request");
	}
	
	@Override
	public void processInternalResponse(Response response,Boolean isIpV6) {
		Via via = (Via) response.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		String host = via.getHost();
		Integer port = via.getPort();
		boolean found = false;

		SIPNode senderNode = (SIPNode) ((ResponseExt)response).getApplicationData();
		if(senderNode != null&&invocationContext.sipNodeMap(isIpV6).containsValue(senderNode))
			found = true;
		else if	(invocationContext.sipNodeMap(isIpV6).containsKey(new KeySip(host, port)))
			found = true;
		
//		for(SIPNode node : invocationContext.nodes) {
//			if(logger.isDebugEnabled()) {
//				logger.debug("internal response checking sendernode " + senderNode + " against node " + node + " or Via host:port " + host + ":" + port);
//			} 
//			// https://github.com/RestComm/load-balancer/issues/45 Checking sender node against list of nodes to ensure it's still available
//			// not checking it was making the B2BUA use case fail and route back the 180 Ringing to one of the nodes instead of the sender. 
//			if(senderNode != null && senderNode.equals(node)) {
//				found = true;
//			} else if (node.getIp().equals(host)) {
//				if(port.equals(node.getProperties().get(transport+"Port"))) {
//					found = true;
//				}
//			}
//		}
		if(logger.isDebugEnabled()) {
			logger.debug("internal response node found ? " + found);
		}
		if(!found) {
			//String callId = ((SIPHeader) response.getHeader(headerName)).getValue();
			URI currURI=((HeaderAddress)response.getHeader(headerName)).getAddress().getURI();
			String user;
			if(currURI.isSipURI())
				user = ((SipURI)currURI).getUser();
			else
				user = ((TelURL)currURI).getPhoneNumber();
			
			
			SIPNode node = userToMap.get(user);
			//if(node == null || !invocationContext.nodes.contains(node)) {
			if(node == null || !invocationContext.sipNodeMap(isIpV6).containsValue(node)) {
				node = selectNewNode(node, user);
				String transportProperty = transport + "Port";
				port = (Integer) node.getProperties().get(transportProperty);
				if(port == null) throw new RuntimeException("No transport found for node " + node + " " + transportProperty);
				if(logger.isDebugEnabled()) {
					logger.debug("changing via " + via + "setting new values " + node.getIp() + ":" + port);
				}
				try {
					via.setHost(node.getIp());
					via.setPort(port);
				} catch (Exception e) {
					throw new RuntimeException("Error setting new values " + node.getIp() + ":" + port + " on via " + via, e);
				}
				// need to reset the rport for reliable transports
				if(!ListeningPoint.UDP.equalsIgnoreCase(transport)) {
					via.setRPort();
				}				
			}
		}
	}
	
	@Override
	public SIPNode processExternalRequest(Request request,Boolean isIpV6) {
		URI currURI=((HeaderAddress)request.getHeader(headerName)).getAddress().getURI();
		String user;
		if(currURI.isSipURI())
			user = ((SipURI)currURI).getUser();
		else
			user = ((TelURL)currURI).getPhoneNumber();
		
		SIPNode node;
		node = userToMap.get(user);
		headerToTimestamps.put(user, System.currentTimeMillis());

		if(node == null) { //
			node = nextAvailableNode(isIpV6);
			if(node == null) return null;
			userToMap.put(user, node);
			if(logger.isDebugEnabled()) {
	    		logger.debug("No node found in the affinity map. It is null. We select new node: " + node);
	    	}
		} else {
			//if(!invocationContext.nodes.contains(node)) { // If the assigned node is now dead
			if(!invocationContext.sipNodeMap(isIpV6).containsValue(node)) { // If the assigned node is now dead
				node = selectNewNode(node, user);
			} else { // ..else it's alive and we can route there
				//.. and we just leave it like that
				if(logger.isDebugEnabled()) {
		    		logger.debug("The assigned node in the affinity map is still alive: " + node);
		    	}
			}
		}
		
		return node;
	}
	
	@Override
	public void processExternalResponse(Response response,Boolean isIpV6) {
		Via via = (Via) response.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		String host = via.getHost();
		Integer port = via.getPort();
		boolean found = false;
//		for(SIPNode node : invocationContext.nodes) {
//			if(node.getIp().equals(host)) {
//				if(port.equals(node.getProperties().get(transport+"Port"))) {
//					found = true;
//				}
//			}
//		}
		if(invocationContext.sipNodeMap(isIpV6).containsKey(new KeySip(host, port)))
			found = true;
		if(logger.isDebugEnabled()) {
			logger.debug("external response node found ? " + found);
		}
		if(!found) {
			//String callId = ((SIPHeader) response.getHeader(headerName)).getValue();
			URI currURI=((HeaderAddress)response.getHeader(headerName)).getAddress().getURI();
			String user;
			if(currURI.isSipURI())
				user = ((SipURI)currURI).getUser();
			else
				user = ((TelURL)currURI).getPhoneNumber();
			
			SIPNode node = userToMap.get(user);
			//if(node == null || !invocationContext.nodes.contains(node)) {
			if(node == null || !invocationContext.sipNodeMap(isIpV6).containsValue(node)) {
				node = selectNewNode(node, user);
				String transportProperty = transport + "Port";
				port = (Integer) node.getProperties().get(transportProperty);
				if(port == null) throw new RuntimeException("No transport found for node " + node + " " + transportProperty);
				if(logger.isDebugEnabled()) {
					logger.debug("changing via " + via + "setting new values " + node.getIp() + ":" + port);
				}
				try {
					via.setHost(node.getIp());
					via.setPort(port);
				} catch (Exception e) {
					throw new RuntimeException("Error setting new values " + node.getIp() + ":" + port + " on via " + via, e);
				}
				// need to reset the rport for reliable transports
				if(!ListeningPoint.UDP.equalsIgnoreCase(transport)) {
					via.setRPort();
				}
				
			} else {
				String transportProperty = transport + "Port";
				port = (Integer) node.getProperties().get(transportProperty);
				if(via.getHost().equalsIgnoreCase(node.getIp()) || via.getPort() != port) {
					if(logger.isDebugEnabled()) {
						logger.debug("changing retransmission via " + via + "setting new values " + node.getIp() + ":" + port);
					}
					try {
						via.setHost(node.getIp());
						via.setPort(port);
						via.removeParameter("rport");
						via.removeParameter("received");
					} catch (Exception e) {
						throw new RuntimeException("Error setting new values " + node.getIp() + ":" + port + " on via " + via, e);
					}
					// need to reset the rport for reliable transports
					if(!ListeningPoint.UDP.equalsIgnoreCase(transport)) {
						via.setRPort();
					}
				}
			}
		}
	}

	@Override
	public void init() {
		if(getConfiguration() != null) {
			Integer maxTimeInCacheString = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getCallIdAffinityMaxTimeInCache();
			if(maxTimeInCacheString != null) {
				this.maxCallIdleTime = maxTimeInCacheString;
			}
		}
		logger.info("Call Idle Time is " + this.maxCallIdleTime + " seconds. Inactive calls will be evicted.");

		final UserBasedAlgorithm thisAlgorithm = this;
		this.cacheEvictionTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				try {
					synchronized (thisAlgorithm) {
						ArrayList<String> oldCalls = new ArrayList<String>();
						Iterator<String> keys = headerToTimestamps.keySet().iterator();
						while(keys.hasNext()) {
							String key = keys.next();
							long time = headerToTimestamps.get(key);
							if(System.currentTimeMillis() - time > 1000*maxCallIdleTime) {
								oldCalls.add(key);
							}
						}
						for(String key : oldCalls) {
							userToMap.remove(key);
							headerToTimestamps.remove(key);
						}
						if(oldCalls.size()>0) {
							logger.info("Reaping idle calls... Evicted " + oldCalls.size() + " calls.");
						}}
				} catch (Exception e) {
					logger.warn("Failed to clean up old calls. If you continue to se this message frequestly and the memory is growing, report this problem.", e);
				}

			}
		}, 0, 6000);

		if(getConfiguration() != null) {
			this.groupedFailover = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().isCallIdAffinityGroupFailover();
		}
		logger.info("Grouped failover is set to " + this.groupedFailover);
		
	}
	
	@Override
	public void stop() {
		this.cacheEvictionTimer.cancel();
	}
	
	@Override
	public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute) {
		try {
			SIPNode oldNode = getBalancerContext().jvmRouteToSipNode.get(fromJvmRoute);
			SIPNode newNode = getBalancerContext().jvmRouteToSipNode.get(toJvmRoute);
			if(oldNode != null && newNode != null) {
				int updatedRoutes = 0;
				for(String key : userToMap.keySet()) {
					SIPNode n = userToMap.get(key);
					if(n.equals(oldNode)) {
						userToMap.replace(key, newNode);
						updatedRoutes++;
					}
				}
				if(logger.isInfoEnabled()) {
					logger.info("Switchover occured where fromJvmRoute=" + fromJvmRoute + " and toJvmRoute=" + toJvmRoute + " with " + 
							updatedRoutes + " updated routes.");
				}
			} else {
				if(logger.isInfoEnabled()) {
					logger.info("Switchover failed where fromJvmRoute=" + fromJvmRoute + " and toJvmRoute=" + toJvmRoute);
				}
			}
		} catch (Throwable t) {
			if(logger.isInfoEnabled()) {
				logger.info("Switchover failed where fromJvmRoute=" + fromJvmRoute + " and toJvmRoute=" + toJvmRoute);
				logger.info("This is not a fatal failure, logging the reason for the failure ", t);
			}
		}
	}
	
	protected synchronized SIPNode nextAvailableNode(Boolean isIpV6) {
//		if(invocationContext.nodes.size() == 0) return null;
//		int nextNode = nextNodeCounter.incrementAndGet();
//		nextNode %= invocationContext.nodes.size();
//		return invocationContext.nodes.get(nextNode);
		if(invocationContext.sipNodeMap(isIpV6).size() == 0) return null;
		if(it==null)
			it = invocationContext.sipNodeMap(isIpV6).entrySet().iterator();
		Map.Entry pair = null;
		if(it.hasNext())
		{
			pair = (Map.Entry)it.next();
			if(!it.hasNext())
				it = invocationContext.sipNodeMap(isIpV6).entrySet().iterator();
		}
		else
		{
			it = invocationContext.sipNodeMap(isIpV6).entrySet().iterator();
		}
		return (SIPNode) pair.getValue();
	}
	
	protected SIPNode selectNewNode(SIPNode node, String user) {
		if(logger.isDebugEnabled()) {
    		logger.debug("The assigned node has died. This is the dead node: " + node);
    	}
		if(groupedFailover) {
			// This will occur very rarely because we re-assign all calls from the dead node in
			// a single operation
			SIPNode oldNode = node;
			node = leastBusyTargetNode(oldNode);
			if(node == null) return null;
			groupedFailover(oldNode, node);
		} else {
			Boolean isIpV6=InetAddressValidator.getInstance().isValidInet6Address(node.getIp());        	            							
			node = nextAvailableNode(isIpV6);
			if(node == null) {
				if(logger.isDebugEnabled()) {
		    		logger.debug("no nodes available return null");
		    	}
				return null;
			}
			userToMap.put(user, node);
		}
		
		if(logger.isDebugEnabled()) {
    		logger.debug("So, we must select new node: " + node);
    	}
		return node;
	}
	
	protected synchronized SIPNode leastBusyTargetNode(SIPNode deadNode) {
		HashMap<SIPNode, Integer> nodeUtilization = new HashMap<SIPNode, Integer>();
		for(SIPNode node : userToMap.values()) {
			Integer n = nodeUtilization.get(node);
			if(n == null) {
				nodeUtilization.put(node, 0);
			} else {
				nodeUtilization.put(node, n+1);
			}
		}
		int minUtil = Integer.MAX_VALUE;
		SIPNode minUtilNode = null;
		for(SIPNode node : nodeUtilization.keySet()) {
			Integer util = nodeUtilization.get(node);
			if(!node.equals(deadNode) && (util < minUtil)) {
				minUtil = util;
				minUtilNode = node;
			}
		}

		logger.info("Least busy node selected " + minUtilNode + " with " + minUtil + " calls");
		
		return minUtilNode;
	}
	
	@Override
	public void assignToNode(String id, SIPNode node) {
		userToMap.put(id, node);
		headerToTimestamps.put(id, System.currentTimeMillis());
	}
	
	synchronized public void groupedFailover(SIPNode oldNode, SIPNode newNode) {
		try {
			if(oldNode != null && newNode != null) {
				int updatedRoutes = 0;
				for(String key : userToMap.keySet()) {
					SIPNode n = userToMap.get(key);
					if(n.equals(oldNode)) {
						userToMap.replace(key, newNode);
						updatedRoutes++;
					}
				}
				if(logger.isInfoEnabled()) {
					logger.info("Switchover occured where oldNode=" + oldNode + " and newNode=" + newNode + " with " + 
							updatedRoutes + " updated routes.");
				}
			} else {
				if(logger.isInfoEnabled()) {
					logger.info("Switchover failed where fromJvmRoute=" + oldNode + " and toJvmRoute=" + newNode);
				}
			}
		} catch (Throwable t) {
			if(logger.isInfoEnabled()) {
				logger.info("Switchover failed where fromJvmRoute=" + oldNode + " and toJvmRoute=" + newNode);
				logger.info("This is not a fatal failure, logging the reason for the failure ", t);
			}
		}
	}
	
	@Override
	public void configurationChanged() {
		this.cacheEvictionTimer.cancel();
		this.cacheEvictionTimer = new Timer();
		init();
	}

}
