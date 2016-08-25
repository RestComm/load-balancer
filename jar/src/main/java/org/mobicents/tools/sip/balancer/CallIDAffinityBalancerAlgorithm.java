/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.SIPHeader;
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

import org.apache.log4j.Logger;

import javax.sip.ListeningPoint;
import javax.sip.message.Request;
import javax.sip.message.Response;

public class CallIDAffinityBalancerAlgorithm extends DefaultBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(CallIDAffinityBalancerAlgorithm.class.getCanonicalName());
	
	protected String headerName = "Call-ID";
	protected ConcurrentHashMap<String, SIPNode> callIdMap = new ConcurrentHashMap<String, SIPNode>();
	protected ConcurrentHashMap<String, Long> callIdTimestamps = new ConcurrentHashMap<String, Long>();
	protected AtomicInteger nextNodeCounter = new AtomicInteger(0);
	protected int maxCallIdleTime = 500;
	protected boolean groupedFailover = false;
	
	protected Timer cacheEvictionTimer = new Timer();
	
	public void processInternalRequest(Request request) {
		logger.debug("internal request");
	}
	
	public void processInternalResponse(Response response) {
		Via via = (Via) response.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		String host = via.getHost();
		Integer port = via.getPort();
		boolean found = false;
		
		SIPNode senderNode = (SIPNode) ((ResponseExt)response).getApplicationData();

		if(logger.isDebugEnabled()) {
			logger.debug("internal response checking sendernode " + senderNode + " or Via host:port " + host + ":" + port);
		} 
		if(senderNode != null&&invocationContext.sipNodeMap.containsValue(senderNode))
			found = true;
		else if	(invocationContext.sipNodeMap.containsKey(new KeySip(host, port)))
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
			String callId = ((SIPHeader) response.getHeader(headerName)).getValue();
			SIPNode node = callIdMap.get(callId);
			//if(node == null || !invocationContext.nodes.contains(node)) {
			if(node == null || !invocationContext.sipNodeMap.containsValue(node)) {
				node = selectNewNode(node, callId);
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
	
	public void processExternalResponse(Response response) {
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
		
		if(invocationContext.sipNodeMap.containsKey(new KeySip(host, port)))
			found = true;
		
		
		if(logger.isDebugEnabled()) {
			logger.debug("external response node found ? " + found);
		}
		if(!found) {
			String callId = ((SIPHeader) response.getHeader(headerName)).getValue();
			SIPNode node = callIdMap.get(callId);
			//if(node == null || !invocationContext.nodes.contains(node)) {
			if(node == null || !invocationContext.sipNodeMap.containsValue(node)) {
				node = selectNewNode(node, callId);
				String transportProperty = transport + "Port";
				System.out.println(transportProperty);
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
	
	public SIPNode processExternalRequest(Request request) {
		String callId = ((SIPHeader) request.getHeader(headerName))
		.getValue();
		SIPNode node;
		node = callIdMap.get(callId);
		callIdTimestamps.put(callId, System.currentTimeMillis());

		if(node == null) { //
			node = nextAvailableNode();
			if(node == null) return null;
			callIdMap.put(callId, node);
			if(logger.isDebugEnabled()) {
	    		logger.debug("No node found in the affinity map. It is null. We select new node: " + node);
	    	}
		} else {
			if(!invocationContext.sipNodeMap.containsValue(node)) { // If the assigned node is now dead
			//if(!invocationContext.nodes.contains(node)) { // If the assigned node is now dead
				node = selectNewNode(node, callId);
			} else { // ..else it's alive and we can route there
				//.. and we just leave it like that
				if(logger.isDebugEnabled()) {
		    		logger.debug("The assigned node in the affinity map is still alive: " + node);
		    	}
			}
		}
		
// Don't try to be smart here, the retransmissions of BYE will come and will not know where to go.
//		if(request.getMethod().equals("BYE")) {
//			callIdMap.remove(callId);
//			callIdTimestamps.remove(callId);
//		}
		return node;
		
	}
	
	protected SIPNode selectNewNode(SIPNode node, String callId) {
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
			node = nextAvailableNode();
			if(node == null) {
				if(logger.isDebugEnabled()) {
		    		logger.debug("no nodes available return null");
		    	}
				return null;
			}
			callIdMap.put(callId, node);
		}
		
		if(logger.isDebugEnabled()) {
    		logger.debug("So, we must select new node: " + node);
    	}
		return node;
	}
	
	protected synchronized SIPNode nextAvailableNode() {
		//if(invocationContext.nodes.size() == 0) return null;
		if(invocationContext.sipNodeMap.size() == 0) return null;
		//int nextNode = nextNodeCounter.incrementAndGet();
		//nextNode %= invocationContext.nodes.size();
		//return invocationContext.nodes.get(nextNode);
		if(it==null)
			it = invocationContext.sipNodeMap.entrySet().iterator();
		Map.Entry pair = null;
		if(it.hasNext())
		{
			pair = (Map.Entry)it.next();
			if(!it.hasNext())
				it = invocationContext.sipNodeMap.entrySet().iterator();
		}
		else
		{
			it = invocationContext.sipNodeMap.entrySet().iterator();
		}
		return (SIPNode) pair.getValue();
			 
	}
	
	protected synchronized SIPNode leastBusyTargetNode(SIPNode deadNode) {
		HashMap<SIPNode, Integer> nodeUtilization = new HashMap<SIPNode, Integer>();
		for(SIPNode node : callIdMap.values()) {
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
		if(logger.isDebugEnabled()) {
			logger.debug("Least busy node selected " + minUtilNode + " with " + minUtil + " calls");
		}
		
		return minUtilNode;
	}

	public void stop() {
		this.cacheEvictionTimer.cancel();
	}
	
	public void init() {
		if(getProperties() != null) {
			String maxTimeInCacheString = getProperties().getProperty("callIdAffinityMaxTimeInCache");
			if(maxTimeInCacheString != null) {
				this.maxCallIdleTime = Integer.parseInt(maxTimeInCacheString);
			}
		}
		logger.info("Call Idle Time is " + this.maxCallIdleTime + " seconds. Inactive calls will be evicted.");

		final CallIDAffinityBalancerAlgorithm thisAlgorithm = this;
		this.cacheEvictionTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				try {
					synchronized (thisAlgorithm) {
						ArrayList<String> oldCalls = new ArrayList<String>();
						Iterator<String> keys = callIdTimestamps.keySet().iterator();
						while(keys.hasNext()) {
							String key = keys.next();
							long time = callIdTimestamps.get(key);
							if(System.currentTimeMillis() - time > 1000*maxCallIdleTime) {
								oldCalls.add(key);
							}
						}
						for(String key : oldCalls) {
							callIdMap.remove(key);
							callIdTimestamps.remove(key);
						}
						if(oldCalls.size()>0) {
							logger.info("Reaping idle calls... Evicted " + oldCalls.size() + " calls.");
						}}
				} catch (Exception e) {
					logger.warn("Failed to clean up old calls. If you continue to se this message frequestly and the memory is growing, report this problem.", e);
				}

			}
		}, 0, 6000);

		if(getProperties() != null) {
			String groupFailoverProperty = getProperties().getProperty("callIdAffinityGroupFailover");
			if(groupFailoverProperty != null) {
				this.groupedFailover = Boolean.parseBoolean(groupFailoverProperty);
			}
		}
		logger.info("Grouped failover is set to " + this.groupedFailover);
	}
	public void configurationChanged() {
		this.cacheEvictionTimer.cancel();
		this.cacheEvictionTimer = new Timer();
		init();
	}
	
	public void assignToNode(String id, SIPNode node) {
		callIdMap.put(id, node);
		callIdTimestamps.put(id, System.currentTimeMillis());
	}

	@Override
	public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute) {
		try {
			SIPNode oldNode = getBalancerContext().jvmRouteToSipNode.get(fromJvmRoute);
			SIPNode newNode = getBalancerContext().jvmRouteToSipNode.get(toJvmRoute);
			if(oldNode != null && newNode != null) {
				int updatedRoutes = 0;
				for(String key : callIdMap.keySet()) {
					SIPNode n = callIdMap.get(key);
					if(n.equals(oldNode)) {
						callIdMap.replace(key, newNode);
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

	synchronized public void groupedFailover(SIPNode oldNode, SIPNode newNode) {
		try {
			if(oldNode != null && newNode != null) {
				int updatedRoutes = 0;
				for(String key : callIdMap.keySet()) {
					SIPNode n = callIdMap.get(key);
					if(n.equals(oldNode)) {
						callIdMap.replace(key, newNode);
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
	
}
