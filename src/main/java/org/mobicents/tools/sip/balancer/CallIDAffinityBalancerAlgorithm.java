package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.SIPHeader;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sip.address.SipURI;
import javax.sip.header.RouteHeader;
import javax.sip.message.Request;

public class CallIDAffinityBalancerAlgorithm extends DefaultBalancerAlgorithm {
	
	ConcurrentHashMap<String, SIPNode> callIdMap = new ConcurrentHashMap<String, SIPNode>();
	AtomicInteger nextNodeCounter = new AtomicInteger(0);

	public void nodeAdded(SIPNode node) {
		// DONT CARE
		
	}

	public void nodeRemoved(SIPNode node) {
		// DONT CARE
		
	}

	public SIPNode processRequest(Request request) {
		String callId = ((SIPHeader) request.getHeader("Call-ID"))
			.getValue();
		SIPNode node = callIdMap.get(callId);
		BalancerContext balancerContext = getBalancerContext();
		if(node == null) { //
			node = nextAvailableNode();
			if(node == null) return null;
			callIdMap.put(callId, node);
		} else {
			if(!balancerContext.nodes.contains(node)) { // If the assigned node is now dead
				node = nextAvailableNode();
				if(node == null) return null;
				callIdMap.put(callId, node);
			} else { // ..else it's alive and we can route there
				//.. and we just leave it like that
			}
		}
		if(node == null) {
			return null;
		}

		//Adding Route Header pointing to the node the sip balancer wants to forward to
		SipURI routeSipUri;
		try {
			routeSipUri = balancerContext.addressFactory
			.createSipURI(null, node.getIp());

			routeSipUri.setPort(node.getPort());
			routeSipUri.setLrParam();
			final RouteHeader route = balancerContext.headerFactory.createRouteHeader(balancerContext.addressFactory.createAddress(routeSipUri));
			request.addFirst(route);
		} catch (Exception e) {
			throw new RuntimeException("Error adding route header", e);
		}
		
		if(request.getMethod().equals("BYE")) {
			callIdMap.remove(callId);
		}
		return node;
		
	}
	
	private synchronized SIPNode nextAvailableNode() {
		BalancerContext balancerContext = getBalancerContext();
		if(balancerContext.nodes.size() == 0) return null;
		int nextNode = nextNodeCounter.incrementAndGet();
		nextNode %= balancerContext.nodes.size();
		return balancerContext.nodes.get(nextNode);
	}

	public void init() {
		// DONT CARE
		
	}

	public void stop() {
		// DONT CARE
		
	}
	
}
