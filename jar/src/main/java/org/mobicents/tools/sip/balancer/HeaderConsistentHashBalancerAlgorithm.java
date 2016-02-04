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

import java.util.Collections;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TreeSet;
import java.util.logging.Level;

import org.apache.log4j.Logger;

import javax.sip.ListeningPoint;
import javax.sip.address.SipURI;
import javax.sip.header.ExtensionHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.jboss.netty.handler.codec.http.HttpRequest;

public class HeaderConsistentHashBalancerAlgorithm extends DefaultBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(HeaderConsistentHashBalancerAlgorithm.class.getName());
	protected String sipHeaderAffinityKey;
	protected String httpAffinityKey;
	
	// We will maintain a sorted list of the nodes so all SIP LBs will see them in the same order
	// no matter at what order the events arrived
	private SortedSet<SIPNode> nodes = (SortedSet<SIPNode>) Collections.synchronizedSortedSet(new TreeSet<SIPNode>());
	// And we also keep a copy in the array because it is faster to query by index
	protected Object[] nodesArray;
	
	protected boolean nodesAreDirty = true;
	
	public HeaderConsistentHashBalancerAlgorithm() {
	}
	
	public HeaderConsistentHashBalancerAlgorithm(String headerName) {
		if(headerName == null) {
			this.sipHeaderAffinityKey = "Call-ID";
		} else {
			this.sipHeaderAffinityKey = headerName;
		}
	}

	public SIPNode processExternalRequest(Request request) {
		if(nodesAreDirty) { // for testing only where nodes are not removed, just start advertising new version while alive
			synchronized(this) {
				syncNodes();
			}
		}
		Integer nodeIndex = hashHeader(request);
		if(nodeIndex<0) {
			return null;
		} else {
			try {
				SIPNode node = (SIPNode) nodesArray[nodeIndex];
				return node;
			} catch (Exception e) {
				return null;
			}
		}
	}

	@Override
	public synchronized void nodeAdded(SIPNode node) {
		nodes.add(node);
		nodesArray = nodes.toArray(new Object[]{});
		nodesAreDirty = false;
	}

	@Override
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
	
	protected Integer hashHeader(Message message) {
		String headerValue = null;
		if(sipHeaderAffinityKey.equals("from.user")) {
			headerValue = ((SipURI)((FromHeader) message.getHeader(FromHeader.NAME))
					.getAddress().getURI()).getUser();
		} else if(sipHeaderAffinityKey.equals("to.user")) {
			headerValue = ((SipURI)((ToHeader) message.getHeader(ToHeader.NAME))
			.getAddress().getURI()).getUser();
		} else {
			headerValue = ((SIPHeader) message.getHeader(sipHeaderAffinityKey))
			.getValue();
		}

		if(nodesArray.length == 0) {
			throw new RuntimeException("No Application Servers registered. All servers are dead.");
		}
		
		int nodeIndex = hashAffinityKeyword(headerValue);
		
		if(isAlive((SIPNode)nodesArray[nodeIndex])) {
			return nodeIndex;
		} else {
			return -1;
		}
	}
	
	protected boolean isAlive(SIPNode node) {
		if(invocationContext.nodes.contains(node)) return true;
		return false;
	}
	
	public SIPNode processHttpRequest(HttpRequest request, InvocationContext context) {
		String affinityKeyword = getUrlParameters(request.getUri()).get(this.httpAffinityKey);
		if(affinityKeyword == null) {
			return super.processHttpRequest(request);
		}
		return (SIPNode) nodesArray[hashAffinityKeyword(affinityKeyword)];
	}
	
	protected int hashAffinityKeyword(String keyword) {
		int nodeIndex = Math.abs(keyword.hashCode()) % nodesArray.length;

		SIPNode computedNode = (SIPNode) nodesArray[nodeIndex];
		
		if(!isAlive(computedNode)) {
			// If the computed node is dead, find a new one
			for(int q = 0; q<nodesArray.length; q++) {
				nodeIndex = (nodeIndex + 1) % nodesArray.length;
				if(isAlive(((SIPNode)nodesArray[nodeIndex]))) {
					break;
				}
			}
		}
		return nodeIndex;
	}


    HashMap<String,String> getUrlParameters(String url) {
    	HashMap<String,String> parameters = new HashMap<String, String>();
    	int start = url.lastIndexOf('?');
    	if(start>0 && url.length() > start +1) {
    		url = url.substring(start + 1);
    	} else {
    		return parameters;
    	}
    	String[] tokens = url.split("&");
    	for(String token : tokens) {
    		String[] params = token.split("=");
    		if(params.length<2) {
    			parameters.put(token, "");
    		} else {
    			parameters.put(params[0], params[1]);
    		}
    	}
    	return parameters;
    }

    public void init() {
    	this.httpAffinityKey = getProperties().getProperty("httpAffinityKey", "appsession");
    	this.sipHeaderAffinityKey = getProperties().getProperty("sipHeaderAffinityKey", "Call-ID");
    	logger.info("SIP affinity key = " + sipHeaderAffinityKey + " HTTP key = " + httpAffinityKey);
    }
    
	public void configurationChanged() {
		logger.info("Configuration changed");
		this.httpAffinityKey = getProperties().getProperty("httpAffinityKey", "appsession");
		this.sipHeaderAffinityKey = getProperties().getProperty("sipHeaderAffinityKey", "Call-ID");
	}
	
	@Override
	public void processExternalResponse(Response response){
		this.processExternalResponse(response, this.invocationContext);
	}
	
	public void processExternalResponse(Response response, InvocationContext context) {
		Via via = (Via) response.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		Integer nodeIndex = hashHeader(response);
		String host = via.getHost();
		Integer port = via.getPort();		
		Boolean found = false;
		for(SIPNode node : context.nodes) {
			if(node.getIp().equals(host)) {
				if(port.equals(node.getProperties().get(transport+"Port"))) {
					found = true;
				}
			}
		}
		if(logger.isDebugEnabled()) {
			logger.debug("external response node found ? " + found);
		}
		if(!found) {
			if(nodesAreDirty) {
				synchronized(this) {
					syncNodes();
				}
			}
			try {
				SIPNode node = (SIPNode) nodesArray[nodeIndex];
				if(node == null || !context.nodes.contains(node)) {
					if(logger.isDebugEnabled()) {
						logger.debug("No node to handle " + via);
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
						} catch (Exception e) {
							throw new RuntimeException("Error setting new values " + node.getIp() + ":" + port + " on via " + via, e);
						}
						// need to reset the rport for reliable transports
						if(!ListeningPoint.UDP.equalsIgnoreCase(transport)) {
							via.setRPort();
						}
					}
				}
			} catch (Exception e) {
			}
		}
	}
	protected void syncNodes() {
		nodes.clear();
		nodes.addAll(invocationContext.nodes);
		nodesArray = nodes.toArray(new Object[]{});
		nodesAreDirty = false;
	}
}
