package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.SIPHeader;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.RouteHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;

public class HeaderConsistentHashBalancerAlgorithm extends DefaultBalancerAlgorithm {
	
	protected String headerName;
	
	// We will maintain a sorted list of the nodes so all SIP LBs will see them in the same order
	// no matter at what order the events arrived
	private SortedSet nodes = Collections.synchronizedSortedSet(new TreeSet<SIPNode>());
	
	// And we also keep a copy in the array because it is faster to query by index
	private Object[] nodesArray;
	
	private boolean nodesAreDirty = true;
	
	public HeaderConsistentHashBalancerAlgorithm() {
			this.headerName = "Call-ID";
	}
	
	public HeaderConsistentHashBalancerAlgorithm(String headerName) {
		this.headerName = headerName;
	}

	public SIPNode processRequest(SipProvider sipProvider, Request request) {
		Integer nodeIndex = hashHeader(request);
		if(nodeIndex<0) {
			return null;
		} else {
			BalancerContext balancerContext = getBalancerContext();
			if(nodesAreDirty) {
				synchronized(this) {
					nodes.clear();
					nodes.add(balancerContext.nodes);
					nodesArray = nodes.toArray(new Object[]{});
					nodesAreDirty = false;
				}
			}
			try {
				SIPNode node = (SIPNode) nodesArray[nodeIndex];
				if(node != null) {
					//Adding Route Header pointing to the node the sip balancer wants to forward to
					SipURI routeSipUri;
					try {
						routeSipUri = balancerContext.addressFactory
						.createSipURI(null, node.getIp());

						routeSipUri.setPort(node.getPort());
						routeSipUri.setLrParam();
						final RouteHeader route = balancerContext.headerFactory.createRouteHeader(
								balancerContext.addressFactory.createAddress(routeSipUri));
						request.addFirst(route);
					} catch (Exception e) {
						throw new RuntimeException("Error adding route header", e);
					}
				}
				return node;
			} catch (Exception e) {
				return null;
			}
		}
	}

	public synchronized void nodeAdded(SIPNode node) {
		nodes.add(node);
		nodesArray = nodes.toArray(new Object[]{});
		nodesAreDirty = false;
	}

	public synchronized void nodeRemoved(SIPNode node) {
		nodes.remove(node);
		nodesArray = nodes.toArray(new Object[]{});
		nodesAreDirty = false;
	}
	
	private void dumpNodes() {
		System.out.println("0----------------------------------------------------0");
		for(Object object : nodesArray) {
			SIPNode node = (SIPNode) object;
			System.out.println(node);
		}
	}
	
	private Integer hashHeader(Message message) {
		String headerValue = ((SIPHeader) message.getHeader(headerName))
		.getValue();

		CopyOnWriteArrayList<SIPNode> nodes = getBalancerContext().nodes;

		if(nodes.size() == 0) throw new RuntimeException("No Application Servers registered. All servers are dead.");
		
		int nodeIndex = Math.abs(headerValue.hashCode()) % nodes.size();

		/*
		// Persistent hash (requires replication across LBs)
		if(nodes.get(nodeIndex).isDead()) {
			int numberOfDeadNodes = 0;
			for(SIPNode node : nodes) {
				if(node.isDead()) numberOfDeadNodes++;
			}
			
			if(numberOfDeadNodes == nodes.size()) {
				return -1; // all nodes are dead
			}
			
			if(nodes.size()>3) {
				if(numberOfDeadNodes > nodes.size()/2) {
					for(SIPNode node : nodes) {
						if(node.isDead()) nodes.remove(node);
					}
				}
			}
			
			for(int q = 0; q<nodes.size(); q++) {
				nodeIndex = (nodeIndex + 1)%nodes.size();
				if(!nodes.get(nodeIndex).isDead()) {
					break;
				}
			}
		} */
		return nodeIndex;
		
	}

	public void init() {
		String headerName = getProperties().getProperty("CONSISTENT_HASH_AFFINITY_HEADER");
		if(headerName != null) {
			this.headerName = headerName;
		}
	}

	public void stop() {
		// TODO Auto-generated method stub
		
	}

}
