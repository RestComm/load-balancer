package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.Via;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sip.ListeningPoint;
import javax.sip.address.SipURI;
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
	public synchronized SIPNode getNode(String tx) {
		return txToNode.get(tx);
	}
	public synchronized void setNode(String tx, SIPNode node) {
		txToNode.put(tx, node);
		txTimestamps.put(tx, System.currentTimeMillis());
	}
	public SIPNode processAssignedExternalRequest(Request request,
			SIPNode assignedNode) {
		String callId = ((SIPHeader) request.getHeader(headerName)).getValue();
		if(callIdMap.get(callId) != null) {
			assignedNode = callIdMap.get(callId);
		}
		ViaHeader via = (ViaHeader) request.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		String tx = via.getBranch();
		SIPNode seenNode = getNode(tx);
		if(seenNode != null) return seenNode;
		if(!earlyDialogWorstCase) {
			if(request.getMethod().contains("ACK")) return assignedNode;
		}
		RouteHeader route = (RouteHeader) request.getHeader(RouteHeader.NAME);
		SipURI uri = null;
		if(route != null) {
			uri = (SipURI) route.getAddress().getURI();
		} else {
			uri = (SipURI) request.getRequestURI();
		}
		try {
			for(SIPNode node:getBalancerContext().nodes) {
				if(!node.equals(assignedNode)) {
					uri.setHost(node.getIp());
					Integer port = (Integer) node.getProperties().get(transport + "Port");
					uri.setPort(port);
					
					
					callIdMap.put(callId, node);
					setNode(tx, node);
					
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
				}
			}
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
		logger.fine("internal request");
	}
	
	public void processInternalResponse(Response request) {
		logger.fine("internal response");
	}
	
	public void processExternalResponse(Response response) {
		BalancerContext balancerContext = getBalancerContext();
		Via via = (Via) response.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		String host = via.getHost();
		boolean found = false;
		for(SIPNode node : BalancerContext.balancerContext.nodes) {
			if(node.getIp().equals(host)) found = true;
		}
		if(!found) {
			String callId = ((SIPHeader) response.getHeader(headerName))
			.getValue();
			SIPNode node = callIdMap.get(callId);
			if(node == null || !balancerContext.nodes.contains(node)) {
				node = selectNewNode(node, callId);
				try {
					via.setHost(node.getIp());
					String transportProperty = transport + "Port";
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
					String transportProperty = transport + "Port";
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
		node = callIdMap.get(callId);
		callIdTimestamps.put(callId, System.currentTimeMillis());

		BalancerContext balancerContext = getBalancerContext();

		if(node == null) { //
			node = nextAvailableNode();
			if(node == null) return null;
			callIdMap.put(callId, node);
			if(logger.isLoggable(Level.FINEST)) {
	    		logger.finest("No node found in the affinity map. It is null. We select new node: " + node);
	    	}
		} else {
			if(!balancerContext.nodes.contains(node)) { // If the assigned node is now dead
				node = selectNewNode(node, callId);
			} else { // ..else it's alive and we can route there
				//.. and we just leave it like that
				if(logger.isLoggable(Level.FINEST)) {
		    		logger.finest("The assigned node in the affinity map is still alive: " + node);
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
		if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("The assigned node has died. This is the dead node: " + node);
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
		
		if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("So, we must select new node: " + node);
    	}
		return node;
	}
	
	protected synchronized SIPNode nextAvailableNode() {
		BalancerContext balancerContext = getBalancerContext();
		if(balancerContext.nodes.size() == 0) return null;
		int nextNode = nextNodeCounter.incrementAndGet();
		nextNode %= balancerContext.nodes.size();
		return balancerContext.nodes.get(nextNode);
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
		String maxTimeInCacheString = getProperties().getProperty("callIdAffinityMaxTimeInCache");
		if(maxTimeInCacheString != null) {
			this.maxCallIdleTime = Integer.parseInt(maxTimeInCacheString);
		}
		logger.info("Call Idle Time is " + this.maxCallIdleTime + " seconds. Inactive calls will be evicted.");
		String earlyDialogWorstCaseString = getProperties().getProperty("earlyDialogWorstCase");
		if(earlyDialogWorstCaseString != null) {
			earlyDialogWorstCase = Boolean.parseBoolean(earlyDialogWorstCaseString);
		}
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
					logger.log(Level.WARNING, "Failed to clean up old calls. If you continue to se this message frequestly and the memory is growing, report this problem.", e);
				}

			}
		}, 0, 6000);
		
		String groupFailoverProperty = getProperties().getProperty("callIdAffinityGroupFailover");
		if(groupFailoverProperty != null) {
			this.groupedFailover = Boolean.parseBoolean(groupFailoverProperty);
		}
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
				if(logger.isLoggable(Level.INFO)) {
					logger.info("Switchover occured where fromJvmRoute=" + fromJvmRoute + " and toJvmRoute=" + toJvmRoute + " with " + 
							updatedRoutes + " updated routes.");
				}
			} else {
				if(logger.isLoggable(Level.INFO)) {
					logger.info("Switchover failed where fromJvmRoute=" + fromJvmRoute + " and toJvmRoute=" + toJvmRoute);
				}
			}
		} catch (Throwable t) {
			if(logger.isLoggable(Level.INFO)) {
				logger.info("Switchover failed where fromJvmRoute=" + fromJvmRoute + " and toJvmRoute=" + toJvmRoute);
				logger.log(Level.INFO, "This is not a fatal failure, logging the reason for the failure ", t);
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
				if(logger.isLoggable(Level.INFO)) {
					logger.info("Switchover occured where oldNode=" + oldNode + " and newNode=" + newNode + " with " + 
							updatedRoutes + " updated routes.");
				}
			} else {
				if(logger.isLoggable(Level.INFO)) {
					logger.info("Switchover failed where fromJvmRoute=" + oldNode + " and toJvmRoute=" + newNode);
				}
			}
		} catch (Throwable t) {
			if(logger.isLoggable(Level.INFO)) {
				logger.info("Switchover failed where fromJvmRoute=" + oldNode + " and toJvmRoute=" + newNode);
				logger.log(Level.INFO, "This is not a fatal failure, logging the reason for the failure ", t);
			}
		}
	}
	
}
