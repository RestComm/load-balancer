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
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

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
	protected ConcurrentHashMap<String, SIPNode> txToNode = new ConcurrentHashMap<String, SIPNode>();
	protected ConcurrentHashMap<String, Long> txTimestamps = new ConcurrentHashMap<String, Long>();
	protected boolean earlyDialogWorstCase = false;
	public synchronized SIPNode getNodeA(String tx) {
		return txToNode.get(tx);
	}
	public synchronized void setNodeA(String tx, SIPNode node) {
		txToNode.put(tx, node);
		txTimestamps.put(tx, System.currentTimeMillis());
	}
	static int y =0;
	public SIPNode processAssignedExternalRequest(Request request,
			SIPNode assignedNode) {
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
			SIPNode node;
			if(!request.getMethod().equalsIgnoreCase("ACK")) {
				//Gvag: new transaction should go to a new node
				SIPNode newNode = nextAvailableNode();//getNodeA(callId+cseq);
				if(newNode == null) {
					//for(SIPNode currNode:invocationContext.nodes) {
					for(SIPNode currNode:invocationContext.sipNodeMap.values()) {
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
            if(balancerContext.terminateTLSTraffic)
                if(transport.equalsIgnoreCase(ListeningPoint.TLS))
                    transport=ListeningPoint.TCP.toLowerCase();
                    else if (transport.equalsIgnoreCase(ListeningPointExt.WSS))
                        transport=ListeningPointExt.WS.toLowerCase();
                        
			Integer port = (Integer) node.getProperties().get(transport + "Port");
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
	protected ConcurrentHashMap<String, SIPNode> callIdMap = new ConcurrentHashMap<String, SIPNode>();
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
	
	public void processExternalResponse(Response response) {
		Via via = (Via) response.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		String host = via.getHost();
		boolean found = false;
		//for(SIPNode node : invocationContext.nodes) {
		for(SIPNode node : invocationContext.sipNodeMap.values()) {
			if(node.getIp().equals(host)) found = true;
		}
		if(!found) {
			String callId = ((SIPHeader) response.getHeader(headerName))
			.getValue();
			SIPNode node = callIdMap.get(callId);
			//if(node == null || !invocationContext.nodes.contains(node)) {
			if(node == null || !invocationContext.sipNodeMap.containsValue(node)) {
				node = selectNewNode(node, callId);
				try {
					via.setHost(node.getIp());
					String transportProperty = transport.toLowerCase() + "Port";
					Integer port = (Integer) node.getProperties().get(transportProperty);
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
				SIPNode node = callIdMap.get(callId);
				for(int q=0; q<3; q++) {
					SIPNode other = selectNewNode(node, callId);
					if(other!= null && !other.equals(node)) {
						node = other; break;
					}
				}
				try {
					via.setHost(node.getIp());
					String transportProperty = transport.toLowerCase() + "Port";
					Integer port = (Integer) node.getProperties().get(transportProperty);
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
	
	public SIPNode processExternalRequest(Request request) {
		String callId = ((SIPHeader) request.getHeader(headerName))
		.getValue();
		SIPNode node;
		CSeqHeader cs = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
		long cseq = cs.getSeqNumber();
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
			//if(!invocationContext.nodes.contains(node)) { // If the assigned node is now dead
			if(!invocationContext.sipNodeMap.containsValue(node)) { // If the assigned node is now dead
				node = selectNewNode(node, callId);
			} else { // ..else it's alive and we can route there
				//.. and we just leave it like that
				if(logger.isDebugEnabled()) {
		    		logger.debug("The assigned node in the affinity map is still alive: " + node);
		    	}
				if(!request.getMethod().equals("ACK")) {
					//for(SIPNode n:invocationContext.nodes) {
					for(SIPNode n:invocationContext.sipNodeMap.values()) {
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
			if(node == null) return null;
			callIdMap.put(callId, node);
		}
		
		if(logger.isDebugEnabled()) {
    		logger.debug("So, we must select new node: " + node);
    	}
		return node;
	}
	
	protected synchronized SIPNode nextAvailableNode() {
		BalancerContext balancerContext = getBalancerContext();
//		if(invocationContext.nodes.size() == 0) return null;
//		int nextNode = nextNodeCounter.incrementAndGet();
//		nextNode %= invocationContext.nodes.size();
//		return invocationContext.nodes.get(nextNode);
		if(invocationContext.sipNodeMap.size() == 0) return null;
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
		BalancerContext balancerContext = getBalancerContext();
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
