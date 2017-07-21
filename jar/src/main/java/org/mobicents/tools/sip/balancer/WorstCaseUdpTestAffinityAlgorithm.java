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

import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.Via;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.log4j.Logger;
import org.mobicents.tools.heartbeat.api.Node;

import javax.sip.ListeningPoint;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * This algorthim will send each new transaction to a new node triggering all replication corner cases
 * 
 * @author vladimirralev
 *
 */
public class WorstCaseUdpTestAffinityAlgorithm extends DefaultBalancerAlgorithm {
	protected ConcurrentHashMap<String, Node> txToNode = new ConcurrentHashMap<String, Node>();
	protected ConcurrentHashMap<String, Long> txTimestamps = new ConcurrentHashMap<String, Long>();
	protected boolean earlyDialogWorstCase = false;
	public synchronized Node getNodeA(String tx) {
		return txToNode.get(tx);
	}
	public synchronized void setNodeA(String tx, Node node) {
		txToNode.put(tx, node);
		txTimestamps.put(tx, System.currentTimeMillis());
	}
	static int y =0;
	public Node processAssignedExternalRequest(Request request,
			Node assignedNode) {
		
		Boolean isIpV6=LbUtils.isValidInet6Address(assignedNode.getIp());        	            				
		//if((y++)%2==0) if(request.getHeader("CSeq").toString().contains("1")) return assignedNode;
		String callId = ((SIPHeader) request.getHeader(headerName)).getValue();
		CSeqHeader cs = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
		long cseq = cs.getSeqNumber();
		if(callIdMap.get(callId) != null) {			
			assignedNode = callIdMap.get(callId);			
		}
		ViaHeader via = (ViaHeader) request.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		
		RouteHeader route = (RouteHeader) request.getHeader(RouteHeader.NAME);
		SipURI uri = null;
		if(route != null) {
			uri = (SipURI) route.getAddress().getURI();
		} else {
			uri = (SipURI) request.getRequestURI();
		}
		try {
			Node node;
			if(!request.getMethod().equalsIgnoreCase("ACK")) {
				//Gvag: new transaction should go to a new node
				Node newNode = nextAvailableNode(isIpV6);//getNodeA(callId+cseq);
				if(newNode == null) {
					//for(Node currNode:invocationContext.nodes) {
					for(Node currNode:invocationContext.sipNodeMap(isIpV6).values()) {
						if(!currNode.equals(assignedNode)) {
							newNode = currNode;
						}
					}
				}
				node = newNode;
			}
			else
				node=assignedNode;
			
			uri.setHost(node.getIp());
			if(balancerContext.internalTransport!=null)
			{
				transport = balancerContext.internalTransport.toLowerCase();
			}else if(balancerContext.terminateTLSTraffic)
                if(transport.equalsIgnoreCase(ListeningPoint.TLS))
                    transport=ListeningPoint.TCP.toLowerCase();
                    else if (transport.equalsIgnoreCase(ListeningPointExt.WSS))
                        transport=ListeningPointExt.WS.toLowerCase();
                        
			Integer port = Integer.parseInt(node.getProperties().get(transport + "Port"));
			uri.setPort(port);


			callIdMap.put(callId, node);
			setNodeA(callId + cseq, node);

			// For http://code.google.com/p/mobicents/issues/detail?id=2132

			if(request.getRequestURI().isSipURI()) {
				SipURI ruri = (SipURI) request.getRequestURI();
				String rurihostid = ruri.getHost() + ruri.getPort();
				String originalhostid = assignedNode.getIp() + assignedNode.getProperties().get(transport + "Port");
				if(rurihostid.equals(originalhostid)) {
					ruri.setPort(port);
					ruri.setHost(node.getIp());
				}
			}
		
			return node;


		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return assignedNode;
	}

	private static Logger logger = Logger.getLogger(WorstCaseUdpTestAffinityAlgorithm.class.getCanonicalName());

	protected String headerName = "Call-ID";
	protected ConcurrentHashMap<String, Node> callIdMap = new ConcurrentHashMap<String, Node>();
	protected ConcurrentHashMap<String, Long> callIdTimestamps = new ConcurrentHashMap<String, Long>();
	protected AtomicInteger nextNodeCounter = new AtomicInteger(0);
	protected int maxCallIdleTime = 500;
	protected boolean groupedFailover = false;
	
	protected Timer cacheEvictionTimer = new Timer();
	
	public void processInternalRequest(Request request) {
		logger.debug("internal request");
	}
	
	public void processInternalResponse(Response request) {
		logger.debug("internal response");
	}
	
	public void processExternalResponse(Response response,Boolean isIpV6) {
		Via via = (Via) response.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		String host = via.getHost();
		boolean found = false;
		//for(Node node : invocationContext.nodes) {
		for(Node node : invocationContext.sipNodeMap(isIpV6).values()) {
			if(node.getIp().equals(host)) found = true;
		}
		if(!found) {
			String callId = ((SIPHeader) response.getHeader(headerName))
			.getValue();
			Node node = callIdMap.get(callId);
			//if(node == null || !invocationContext.nodes.contains(node)) {
			if(node == null || !invocationContext.sipNodeMap(isIpV6).containsValue(node)) {
				node = selectNewNode(node, callId);
				try {
					via.setHost(node.getIp());
					String transportProperty = transport.toLowerCase() + "Port";
					Integer port = Integer.parseInt(node.getProperties().get(transportProperty));
					if(port == null) throw new RuntimeException("No transport found for node " + node + " " + transportProperty);
					via.setPort(port);
				} catch (Exception e) {
					throw new RuntimeException("Error", e);
				}
				if(!ListeningPoint.UDP.equalsIgnoreCase(transport)) {
					via.setRPort();
				}
			}
		} else {
			if(earlyDialogWorstCase && response.getStatusCode()>100) {
				String callId = ((SIPHeader) response.getHeader(headerName))
				.getValue();
				Node node = callIdMap.get(callId);
				for(int q=0; q<3; q++) {
					Node other = selectNewNode(node, callId);
					if(other!= null && !other.equals(node)) {
						node = other; break;
					}
				}
				try {
					via.setHost(node.getIp());
					String transportProperty = transport.toLowerCase() + "Port";
					Integer port = Integer.parseInt(node.getProperties().get(transportProperty));
					if(port == null) throw new RuntimeException("No transport found for node " + node + " " + transportProperty);
					via.setPort(port);
				} catch (Exception e) {
					throw new RuntimeException("Error", e);
				}
				if(!ListeningPoint.UDP.equalsIgnoreCase(transport)) {
					via.setRPort();
				}
			}
		}
	}	
	
	public Node processExternalRequest(Request request,Boolean isIpV6) {
		String callId = ((SIPHeader) request.getHeader(headerName))
		.getValue();
		Node node;
		CSeqHeader cs = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
		long cseq = cs.getSeqNumber();
		node = callIdMap.get(callId);
		callIdTimestamps.put(callId, System.currentTimeMillis());

		if(node == null) { //
			node = nextAvailableNode(isIpV6);
			if(node == null) return null;
			callIdMap.put(callId, node);
			if(logger.isDebugEnabled()) {
	    		logger.debug("No node found in the affinity map. It is null. We select new node: " + node);
	    	}
		} else {
			//if(!invocationContext.nodes.contains(node)) { // If the assigned node is now dead
			if(!invocationContext.sipNodeMap(isIpV6).containsValue(node)) { // If the assigned node is now dead
				node = selectNewNode(node, callId);
			} else { // ..else it's alive and we can route there
				//.. and we just leave it like that
				if(logger.isDebugEnabled()) {
		    		logger.debug("The assigned node in the affinity map is still alive: " + node);
		    	}
				if(!request.getMethod().equals("ACK")) {
					//for(Node n:invocationContext.nodes) {
					for(Node n:invocationContext.sipNodeMap(isIpV6).values()) {
						if(!n.equals(node)) node = n;
						break;
					}
				}
			}
		}
		
		setNodeA(callId+cseq,node);
		callIdMap.put(callId, node);
		
// Don't try to be smart here, the retransmissions of BYE will come and will not know where to go.
//		if(request.getMethod().equals("BYE")) {
//			callIdMap.remove(callId);
//			callIdTimestamps.remove(callId);
//		}
		return node;
		
	}
	
	protected Node selectNewNode(Node node, String callId) {
		if(logger.isDebugEnabled()) {
    		logger.debug("The assigned node has died. This is the dead node: " + node);
    	}
		if(groupedFailover) {
			// This will occur very rarely because we re-assign all calls from the dead node in
			// a single operation
			Node oldNode = node;
			node = leastBusyTargetNode(oldNode);
			if(node == null) return null;
			groupedFailover(oldNode, node);
		} else {
			Boolean isIpV6=LbUtils.isValidInet6Address(node.getIp());        	            							
			node = nextAvailableNode(isIpV6);
			if(node == null) return null;
			callIdMap.put(callId, node);
		}
		
		if(logger.isDebugEnabled()) {
    		logger.debug("So, we must select new node: " + node);
    	}
		return node;
	}
	
	protected synchronized Node nextAvailableNode(Boolean isIpV6) {		
//		if(invocationContext.nodes.size() == 0) return null;
//		int nextNode = nextNodeCounter.incrementAndGet();
//		nextNode %= invocationContext.nodes.size();
//		return invocationContext.nodes.get(nextNode);
		if(invocationContext.sipNodeMap(isIpV6).size() == 0) return null;
		Iterator<Entry<KeySip, Node>> currIt = null; 
		if(isIpV6)
			currIt = ipv6It;
		else
			currIt = ipv4It;
		if(currIt==null)
		{
			currIt = invocationContext.sipNodeMap(isIpV6).entrySet().iterator();
			if(isIpV6)
				ipv6It = currIt;
			else
				ipv4It = currIt;
		}
		Entry<KeySip, Node> pair = null;
		if(currIt.hasNext())
		{
			pair = currIt.next();
			if(!currIt.hasNext())
				currIt = invocationContext.sipNodeMap(isIpV6).entrySet().iterator();
			if(isIpV6)
				 ipv6It = currIt;
			else
				 ipv4It = currIt;
			
		}
		else
		{
			currIt = invocationContext.sipNodeMap(isIpV6).entrySet().iterator();
			if(isIpV6)
				 ipv6It = currIt;
			else
				 ipv4It = currIt;
		}
		return pair.getValue();
		
	}
	
	protected synchronized Node leastBusyTargetNode(Node deadNode) {
		HashMap<Node, Integer> nodeUtilization = new HashMap<Node, Integer>();
		for(Node node : callIdMap.values()) {
			Integer n = nodeUtilization.get(node);
			if(n == null) {
				nodeUtilization.put(node, 0);
			} else {
				nodeUtilization.put(node, n+1);
			}
		}
		
		int minUtil = Integer.MAX_VALUE;
		Node minUtilNode = null;
		for(Node node : nodeUtilization.keySet()) {
			Integer util = nodeUtilization.get(node);
			if(!node.equals(deadNode) && (util < minUtil)) {
				minUtil = util;
				minUtilNode = node;
			}
		}

		logger.info("Least busy node selected " + minUtilNode + " with " + minUtil + " calls");
		
		return minUtilNode;
	}

	public void init() {
		Integer maxTimeInCacheString = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getCallIdAffinityMaxTimeInCache();
		if(maxTimeInCacheString != null) {
			this.maxCallIdleTime = maxTimeInCacheString;
		}
		logger.info("Call Idle Time is " + this.maxCallIdleTime + " seconds. Inactive calls will be evicted.");
		
		earlyDialogWorstCase = lbConfig.getSipConfiguration().getAlgorithmConfiguration().isEarlyDialogWorstCase();
		
		logger.info("Early dialog worst case is " + this.earlyDialogWorstCase);
		final WorstCaseUdpTestAffinityAlgorithm thisAlgorithm = this;
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
						}

						// tx
						
						oldCalls = new ArrayList<String>();
						keys = txTimestamps.keySet().iterator();
						while(keys.hasNext()) {
							String key = keys.next();
							long time = txTimestamps.get(key);
							if(System.currentTimeMillis() - time > 1000*maxCallIdleTime) {
								oldCalls.add(key);
							}
						}
						for(String key : oldCalls) {
							txToNode.remove(key);
							txTimestamps.remove(key);
						}
						if(oldCalls.size()>0) {
							logger.info("Reaping idle transactions... Evicted " + oldCalls.size() + " calls.");
						}}
				} catch (Exception e) {
					logger.warn("Failed to clean up old calls. If you continue to se this message frequestly and the memory is growing, report this problem.", e);
				}

			}
		}, 0, 6000);
		
		this.groupedFailover = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().isCallIdAffinityGroupFailover();
		logger.info("Grouped failover is set to " + this.groupedFailover);
	}
	
	public void assignToNode(String id, Node node) {
		callIdMap.put(id, node);
		callIdTimestamps.put(id, System.currentTimeMillis());
	}

	@Override
	public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute) {
		try {
			Node oldNode = getBalancerContext().jvmRouteToSipNode.get(fromJvmRoute);
			Node newNode = getBalancerContext().jvmRouteToSipNode.get(toJvmRoute);
			if(oldNode != null && newNode != null) {
				int updatedRoutes = 0;
				for(String key : callIdMap.keySet()) {
					Node n = callIdMap.get(key);
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

	synchronized public void groupedFailover(Node oldNode, Node newNode) {
		try {
			if(oldNode != null && newNode != null) {
				int updatedRoutes = 0;
				for(String key : callIdMap.keySet()) {
					Node n = callIdMap.get(key);
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
	@Override
	public Integer getNumberOfActiveCalls() {
		return callIdMap.size();
	}
	
}
