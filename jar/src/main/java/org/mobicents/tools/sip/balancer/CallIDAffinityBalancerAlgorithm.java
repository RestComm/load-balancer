package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.SIPHeader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.RouteHeader;
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
		logger.fine("internal request");
	}
	
	public void processInternalResponse(Response request) {
		logger.fine("internal response");
	}
	
	public void processExternalResponse(Response request) {
		logger.fine("external response");
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
				if(logger.isLoggable(Level.FINEST)) {
		    		logger.finest("The assigned node has died. This is the dead node: " + node);
		    	}
				if(request.getMethod().equals(Request.ACK)) {
					// Just drop the ACK. If we send it to a new node it will just 
					// make an error and extra network traffic
					if(logger.isLoggable(Level.FINEST)) {
			    		logger.finest("ACK after failure. We will drop it. It won't be recognized " +
			    				"in the app server and we avoid the unneeded network traffic" + node);
			    	}
					return NullServerNode.nullServerNode;
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
			} else { // ..else it's alive and we can route there
				//.. and we just leave it like that
				if(logger.isLoggable(Level.FINEST)) {
		    		logger.finest("The assigned node in the affinity map is still alive: " + node);
		    	}
			}
		}
		if(node == null) {
			return null;
		}
		
// Don't try to be smart here, the retransmissions of BYE will come and will not know where to go.
//		if(request.getMethod().equals("BYE")) {
//			callIdMap.remove(callId);
//			callIdTimestamps.remove(callId);
//		}
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
