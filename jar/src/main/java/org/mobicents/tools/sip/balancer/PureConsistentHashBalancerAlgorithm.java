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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;
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

/**
 * Pure Consistent Hash algorithm - see http://docs.google.com/present/view?id=dc5jp5vx_89cxdvtxcm Example algorithms section
 * @author vralev
 *
 */

public class PureConsistentHashBalancerAlgorithm extends DefaultBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(PureConsistentHashBalancerAlgorithm.class.getCanonicalName());
	
	protected String sipHeaderAffinityKey;
	protected String httpAffinityKey;
	
	MessageDigest md5;
	
	HashMap<Integer, SIPNode> hashToNode = new HashMap<Integer, SIPNode>();
	HashMap<SIPNode, Integer> nodeToHash = new HashMap<SIPNode, Integer>();
	
	// And we also keep a copy in the array because it is faster to query by index
	private Object[] nodesArray = new Object[]{};
	
	private TreeSet<SIPNode> tmpNodes = new TreeSet<SIPNode>();
	
	public PureConsistentHashBalancerAlgorithm() {
		this("Call-ID");
	}
	
	public PureConsistentHashBalancerAlgorithm(String headerName) {
		this.sipHeaderAffinityKey = headerName;
		try {
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}

	public SIPNode processExternalRequest(Request request) {
		Integer nodeIndex = hashHeader(request);
		if(nodeIndex<0) {
			return null;
		} else {
			BalancerContext balancerContext = getBalancerContext();
			try {
				SIPNode node = (SIPNode) nodesArray[nodeIndex];
				return node;
			} catch (Exception e) {
				return null;
			}
		}
	}

	public synchronized void nodeAdded(SIPNode node) {
		addNode(node);
		syncNodes();
	}
	
	private synchronized void addNode(SIPNode node) {
		tmpNodes.add(node);
		syncNodes();
		dumpNodes();
	}

	public synchronized void nodeRemoved(SIPNode node) {
		tmpNodes.remove(node);
		syncNodes();
		dumpNodes();

	}
	
	public int digest(String string) {
		md5.update(string.getBytes());
		byte[] digest = md5.digest();
		int result = 0;
		for(int q=0; q< digest.length; q++) {
			result = result ^ digest[q]<<((3-(q%4))<<3);
		}
		return result;
	}
	
	public int absDigest(String string) {
		return Math.abs(digest(string));
	}
	
	
	private void dumpNodes() {
		String nodes = "I am " + getBalancerContext().externalHost + ":" + getBalancerContext().externalPort + ". I see the following nodes are in cache right now (" + nodesArray.length + "):\n";
		
		for(Object object : nodesArray) {
			SIPNode node = (SIPNode) object;
			nodes += node.toString() + " [ALIVE:" + isAlive(node) + "]" + " [HASH:" + absDigest(node.toStringWithoutJvmroute()) + "]"+ "\n";
		}
		logger.info(nodes);
	}
	
	private boolean isAlive(SIPNode node) {
		if(getBalancerContext().nodes.contains(node)) return true;
		return false;
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

		if(nodesArray.length == 0) throw new RuntimeException("No Application Servers registered. All servers are dead.");
		
		int nodeIndex = hashAffinityKeyword(headerValue);
		
		if(isAlive((SIPNode)nodesArray[nodeIndex])) {
			return nodeIndex;
		} else {
			return -1;
		}
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
    
	public SIPNode processHttpRequest(HttpRequest request) {
		String affinityKeyword = getUrlParameters(request.getUri()).get(this.httpAffinityKey);
		if(affinityKeyword == null) {
			return super.processHttpRequest(request);
		}
		return (SIPNode) nodesArray[hashAffinityKeyword(affinityKeyword)];
	}
	
	protected int hashAffinityKeyword(String keyword) {
		int hashCode = Math.abs(keyword.hashCode());
		Object[] nodes = nodesArray; // take a copy to avoid inconsistent reads
		int lastNodeWithLowerHash = 0;
		for(int q=0;q<nodes.length;q++) {
			SIPNode node = (SIPNode) nodes[q];
			Integer nodeHash = nodeToHash.get(node);
			if(nodeHash == null) nodeHash = 0;
			
			if(hashCode>nodeHash) {
				lastNodeWithLowerHash = q;
			} else {
				return q;
			}
		}
		return lastNodeWithLowerHash; // the request hash exceeds all node hashes
	}
	
	public void init() {
		InputStream configurationInputStream = null;
		String configFile = getProperties().getProperty("persistentConsistentHashCacheConfiguration");
		if(configFile != null) {
			logger.info("Try to use cache configuration from " + configFile);
			try {
				configurationInputStream = new FileInputStream(configFile);
			} catch (FileNotFoundException e1) {
				logger.log(Level.SEVERE, "File not found", e1);
				throw new RuntimeException(e1);
			}
		} else {
			logger.info("Using default cache settings");
			configurationInputStream = this.getClass().getClassLoader().getResourceAsStream("META-INF/PHA-balancer-cache.xml");
			if(configurationInputStream == null) throw new RuntimeException("Problem loading resource META-INF/PHA-balancer-cache.xml");
		}
		
		for (SIPNode node : getBalancerContext().nodes) {
			addNode(node);
		}
		syncNodes();

		this.httpAffinityKey = getProperties().getProperty("httpAffinityKey", "appsession");
		this.sipHeaderAffinityKey = getProperties().getProperty("sipHeaderAffinityKey", "Call-ID");
	}
	
	private synchronized void syncNodes() {
		Set<SIPNode> nodes = tmpNodes;
		if(nodes != null) {
			ArrayList<SIPNode> nodeList = new ArrayList<SIPNode>();
			nodeList.addAll(nodes);
			Collections.sort(nodeList, new Comparator<SIPNode>() {

				public int compare(SIPNode o1, SIPNode o2) {
					int a = absDigest(o1.toStringWithoutJvmroute());
					int b = absDigest(o2.toStringWithoutJvmroute());
					if(a==b) return 0;
					if(a<b) return -1;
					return 1;
				}
				
			});
			HashMap<Integer, SIPNode> tmpHashToNode = new HashMap<Integer, SIPNode>();
			for(SIPNode node:nodeList) tmpHashToNode.put(absDigest(node.toStringWithoutJvmroute()), node);
			this.hashToNode = tmpHashToNode;
			HashMap<SIPNode, Integer> tmpNodeToHash = new HashMap<SIPNode, Integer>();
			for(SIPNode node:nodeList) tmpNodeToHash.put(node, absDigest(node.toStringWithoutJvmroute()));
			this.nodeToHash = tmpNodeToHash;
			this.nodesArray = nodeList.toArray();
		}
		
		dumpNodes();
	}
	
	public void configurationChanged() {
		logger.info("Configuration changed");
		this.httpAffinityKey = getProperties().getProperty("httpAffinityKey", "appsession");
		this.sipHeaderAffinityKey = getProperties().getProperty("sipHeaderAffinityKey", "Call-ID");
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
