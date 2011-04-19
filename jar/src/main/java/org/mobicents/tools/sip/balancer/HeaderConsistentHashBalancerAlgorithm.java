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
import java.util.logging.Logger;

import javax.sip.ListeningPoint;
import javax.sip.address.SipURI;
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
	private SortedSet nodes = Collections.synchronizedSortedSet(new TreeSet<SIPNode>());
	
	// And we also keep a copy in the array because it is faster to query by index
	private Object[] nodesArray;
	
	private boolean nodesAreDirty = true;
	
	public HeaderConsistentHashBalancerAlgorithm() {
	}
	
	public HeaderConsistentHashBalancerAlgorithm(String headerName) {
		this.sipHeaderAffinityKey = headerName;
	}

	public SIPNode processExternalRequest(Request request) {
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

		if(nodes.size() == 0) return -1;
		
		int nodeIndex = hashAffinityKeyword(headerValue);

		return nodeIndex;
		
	}
	
	public SIPNode processHttpRequest(HttpRequest request) {
		String affinityKeyword = getUrlParameters(request.getUri()).get(this.httpAffinityKey);
		if(affinityKeyword == null) {
			return super.processHttpRequest(request);
		}
		return (SIPNode) nodesArray[hashAffinityKeyword(affinityKeyword)];
	}
	
	protected int hashAffinityKeyword(String keyword) {
		int nodeIndex = Math.abs(keyword.hashCode()) % nodes.size();
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
    }
    
	public void configurationChanged() {
		logger.info("Configuration changed");
		init();
	}
	
	public void processExternalResponse(Response response) {
		
		Integer nodeIndex = hashHeader(response);
		BalancerContext balancerContext = getBalancerContext();
		Via via = (Via) response.getHeader(Via.NAME);
		String host = via.getHost();
		Integer port = via.getPort();
		String transport = via.getTransport().toLowerCase();
		boolean found = false;
		for(SIPNode node : balancerContext.nodes) {
			if(node.getIp().equals(host)) {
				if(port.equals(node.getProperties().get(transport+"Port"))) {
					found = true;
				}
			}
		}
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("external response node found ? " + found);
		}
		if(!found) {
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
				if(node == null || !balancerContext.nodes.contains(node)) {
					if(logger.isLoggable(Level.FINEST)) {
						logger.finest("No node to handle " + via);
					}
					
				} else {
					String transportProperty = transport + "Port";
					port = (Integer) node.getProperties().get(transportProperty);
					if(via.getHost().equalsIgnoreCase(node.getIp()) || via.getPort() != port) {
						if(logger.isLoggable(Level.FINEST)) {
							logger.finest("changing retransmission via " + via + "setting new values " + node.getIp() + ":" + port);
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
}
