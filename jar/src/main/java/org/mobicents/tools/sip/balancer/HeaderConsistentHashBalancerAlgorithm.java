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

import org.apache.commons.validator.routines.InetAddressValidator;
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
	private SortedSet<SIPNode> nodesV4 = (SortedSet<SIPNode>) Collections.synchronizedSortedSet(new TreeSet<SIPNode>());
	private SortedSet<SIPNode> nodesV6 = (SortedSet<SIPNode>) Collections.synchronizedSortedSet(new TreeSet<SIPNode>());
	// And we also keep a copy in the array because it is faster to query by index
	protected Object[] nodesArrayV4;
	protected Object[] nodesArrayV6;
	
	protected boolean nodesAreDirty = true;
	
	public HeaderConsistentHashBalancerAlgorithm() {
	}
	
	private SortedSet<SIPNode> nodes(Boolean isIpV6)
	{
		if(isIpV6)
			return nodesV6;
		else
			return nodesV4;
	}
	
	protected Object[] nodesArray(Boolean isIpV6)
	{
		if(isIpV6)
			return nodesArrayV6;
		else
			return nodesArrayV4;
	}
	
	public HeaderConsistentHashBalancerAlgorithm(String headerName) {
		if(headerName == null) {
			this.sipHeaderAffinityKey = "Call-ID";
		} else {
			this.sipHeaderAffinityKey = headerName;
		}
	}

	public SIPNode processExternalRequest(Request request,Boolean isIpV6) {
		if(nodesAreDirty) { // for testing only where nodes are not removed, just start advertising new version while alive
			synchronized(this) {
				syncNodes(isIpV6);
			}
		}
		Integer nodeIndex = hashHeader(request,isIpV6);
		if(nodeIndex<0) {
			return null;
		} else {
			try {
				SIPNode node = (SIPNode) nodesArray(isIpV6)[nodeIndex];
				return node;
			} catch (Exception e) {
				return null;
			}
		}
	}

	@Override
	public synchronized void nodeAdded(SIPNode node) {
		Boolean isIpV6=InetAddressValidator.getInstance().isValidInet6Address(node.getIp());		
		nodes(isIpV6).add(node);
		
		if(isIpV6)
			nodesArrayV6 = nodes(true).toArray(new Object[]{});
		else
			nodesArrayV4 = nodes(false).toArray(new Object[]{});
		
		nodesAreDirty = false;
	}

	@Override
	public synchronized void nodeRemoved(SIPNode node) {
		Boolean isIpV6=InetAddressValidator.getInstance().isValidInet6Address(node.getIp());				
		nodes(isIpV6).remove(node);
		
		if(isIpV6)
			nodesArrayV6 = nodes(true).toArray(new Object[]{});
		else
			nodesArrayV4 = nodes(false).toArray(new Object[]{});
		
		nodesAreDirty = false;
	}
	
	protected Integer hashHeader(Message message,Boolean isIpV6) {
		String headerValue = null;
		if(sipHeaderAffinityKey.equals("From")) {
			headerValue = ((SipURI)((FromHeader) message.getHeader(FromHeader.NAME))
					.getAddress().getURI()).getUser();
		} else if(sipHeaderAffinityKey.equals("To")) {
			headerValue = ((SipURI)((ToHeader) message.getHeader(ToHeader.NAME))
			.getAddress().getURI()).getUser();
		} else {
			headerValue = ((SIPHeader) message.getHeader(sipHeaderAffinityKey))
			.getValue();
		}

		if(nodesArray(isIpV6).length == 0) {
			throw new RuntimeException("No Application Servers registered. All servers are dead.");
		}
		
		int nodeIndex = hashAffinityKeyword(headerValue,isIpV6);
		
		if(isAlive((SIPNode)nodesArray(isIpV6)[nodeIndex])) {
			return nodeIndex;
		} else {
			return -1;
		}
	}
	
	protected boolean isAlive(SIPNode node) {
		//if(invocationContext.nodes.contains(node)) return true;
		Boolean isIpV6=InetAddressValidator.getInstance().isValidInet6Address(node.getIp());        	            						
		if(invocationContext.sipNodeMap(isIpV6).containsValue(node)) return true;
		return false;
	}
	
	public SIPNode processHttpRequest(HttpRequest request, InvocationContext context) {
		String affinityKeyword = getUrlParameters(request.getUri()).get(this.httpAffinityKey);
		if(affinityKeyword == null) {
			return super.processHttpRequest(request);
		}
		return (SIPNode) nodesArrayV4[hashAffinityKeyword(affinityKeyword,false)];
	}
	
	protected int hashAffinityKeyword(String keyword,Boolean isIpV6) {
		int nodeIndex = Math.abs(keyword.hashCode()) % nodesArray(isIpV6).length;

		SIPNode computedNode = (SIPNode) nodesArray(isIpV6)[nodeIndex];
		
		if(!isAlive(computedNode)) {
			// If the computed node is dead, find a new one
			for(int q = 0; q<nodesArray(isIpV6).length; q++) {
				nodeIndex = (nodeIndex + 1) % nodesArray(isIpV6).length;
				if(isAlive(((SIPNode)nodesArray(isIpV6)[nodeIndex]))) {
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
    	this.httpAffinityKey = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getHttpAffinityKey();
    	this.sipHeaderAffinityKey = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getSipHeaderAffinityKey();
    	logger.info("SIP affinity key = " + sipHeaderAffinityKey + " HTTP key = " + httpAffinityKey);
    }
    
	public void configurationChanged() {
		logger.info("Configuration changed");
		this.httpAffinityKey = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getHttpAffinityKey();
		this.sipHeaderAffinityKey = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getSipHeaderAffinityKey();
	}
	
	@Override
	public void processExternalResponse(Response response,Boolean isIpV6){
		this.processExternalResponse(response, this.invocationContext,isIpV6);
	}
	
	public void processExternalResponse(Response response, InvocationContext context,Boolean isIpV6) {
		Via via = (Via) response.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		Integer nodeIndex = hashHeader(response,isIpV6);
		String host = via.getHost();
		Integer port = via.getPort();		
		Boolean found = false;
//		for(SIPNode node : context.nodes) {
//			if(node.getIp().equals(host)) {
//				if(port.equals(node.getProperties().get(transport+"Port"))) {
//					found = true;
//				}
//			}
//		}
		if(context.sipNodeMap(isIpV6).containsKey(new KeySip(host, port)))
			found = true;
		if(logger.isDebugEnabled()) {
			logger.debug("external response node found ? " + found);
		}
		if(!found) {
			if(nodesAreDirty) {
				synchronized(this) {
					syncNodes(isIpV6);
				}
			}
			try {
				SIPNode node = (SIPNode) nodesArray(isIpV6)[nodeIndex];
				//if(node == null || !context.nodes.contains(node)) {
				if(node == null || !context.sipNodeMap(isIpV6).containsValue(node)) {
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
	protected void syncNodes(Boolean isIpV6) {
		nodes(isIpV6).clear();
		//nodes.addAll(invocationContext.nodes);
		nodes(isIpV6).addAll(invocationContext.sipNodeMap(isIpV6).values());
		
		if(isIpV6)
			nodesArrayV6 = nodes(true).toArray(new Object[]{});
		else
			nodesArrayV4 = nodes(false).toArray(new Object[]{});
		
		nodesAreDirty = false;
	}
}
