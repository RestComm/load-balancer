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

import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.log4j.Logger;
import org.mobicents.tools.heartbeat.api.Node;

import javax.sip.message.Request;

/**
 * Pure Consistent Hash algorithm - see http://docs.google.com/present/view?id=dc5jp5vx_89cxdvtxcm Example algorithms section
 * @author vralev
 *
 */

public class PureConsistentHashBalancerAlgorithm extends HeaderConsistentHashBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(PureConsistentHashBalancerAlgorithm.class.getCanonicalName());
	
	MessageDigest md5;
	
	HashMap<Integer, Node> hashToNode = new HashMap<Integer, Node>();
	HashMap<Node, Integer> nodeToHash = new HashMap<Node, Integer>();
	
	
	private TreeSet<Node> tmpNodes = new TreeSet<Node>();
	
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

	public Node processExternalRequest(Request request,Boolean isIpV6) {
		Integer nodeIndex = hashHeader(request,isIpV6);
		if(nodeIndex<0) {
			return null;
		} else {
			try {
				Node node = (Node) nodesArray(isIpV6)[nodeIndex];
				if(!invocationContext.sipNodeMap(isIpV6).get(new KeySip(node)).isGracefulShutdown())
					return node;
				else
					return null;
			} catch (Exception e) {
				return null;
			}
		}
	}

	@Override
	public synchronized void nodeAdded(Node node){
		this.nodeAdded(node, invocationContext);
	}
	
	public synchronized void nodeAdded(Node node, InvocationContext context) {
		Boolean isIpV6=LbUtils.isValidInet6Address(node.getIp());        	            			
		addNode(node);
		syncNodes(isIpV6);
	}
	
	private synchronized void addNode(Node node) {
		Boolean isIpV6=LbUtils.isValidInet6Address(node.getIp());        	            			
		tmpNodes.add(node);
		syncNodes(isIpV6);
		dumpNodes();
	}

	public synchronized void nodeRemoved(Node node) {
		Boolean isIpV6=LbUtils.isValidInet6Address(node.getIp());        	            			
		tmpNodes.remove(node);
		syncNodes(isIpV6);
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
		String nodes = null;
		if(nodesArrayV6!=null)
			nodes = "I am " + getBalancerContext().externalHost + ". I see the following nodes are in cache right now (" + (nodesArrayV4.length + nodesArrayV6.length) + "):\n";
		else
			nodes = "I am " + getBalancerContext().externalHost + ". I see the following nodes are in cache right now (" + (nodesArrayV4.length) + "):\n";
		
		for(Object object : nodesArrayV4) {
			Node node = (Node) object;
			nodes += node.toString() + " [ALIVE:" + isAlive(node) + "]" + " [HASH:" + absDigest(node.toStringWithoutJvmroute()) + "]"+ "\n";
		}
		if(nodesArrayV6!=null)
		for(Object object : nodesArrayV6) {
			Node node = (Node) object;
			nodes += node.toString() + " [ALIVE:" + isAlive(node) + "]" + " [HASH:" + absDigest(node.toStringWithoutJvmroute()) + "]"+ "\n";
		}
		logger.info(nodes);
	}
	
	@Override
	protected int hashAffinityKeyword(String keyword,Boolean isIpV6) {
		int hashCode = Math.abs(keyword.hashCode());
		Object[] nodes = nodesArray(isIpV6); // take a copy to avoid inconsistent reads
		int lastNodeWithLowerHash = 0;
		for(int q=0;q<nodes.length;q++) {
			Node node = (Node) nodes[q];
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
		String configFile = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getPersistentConsistentHashCacheConfiguration();
		if(configFile != null) {
			logger.info("Try to use cache configuration from " + configFile);
			try {
				configurationInputStream = new FileInputStream(configFile);
			} catch (FileNotFoundException e1) {
				logger.error("File not found", e1);
				throw new RuntimeException(e1);
			}
		} else {
			logger.info("Using default cache settings");
			configurationInputStream = this.getClass().getClassLoader().getResourceAsStream("META-INF/PHA-balancer-cache.xml");
			if(configurationInputStream == null) throw new RuntimeException("Problem loading resource META-INF/PHA-balancer-cache.xml");
		}
		

		this.httpAffinityKey = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getHttpAffinityKey();
		this.sipHeaderAffinityKey = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getSipHeaderAffinityKey();
	}
	
	@Override
	public synchronized void syncNodes(Boolean isIpV6) {
		Set<Node> nodes = tmpNodes;
		if(nodes != null) {
			ArrayList<Node> nodeList = new ArrayList<Node>();
			nodeList.addAll(nodes);
			Collections.sort(nodeList, new Comparator<Node>() {

				public int compare(Node o1, Node o2) {
					int a = absDigest(o1.toStringWithoutJvmroute());
					int b = absDigest(o2.toStringWithoutJvmroute());
					if(a==b) return 0;
					if(a<b) return -1;
					return 1;
				}
				
			});
			HashMap<Integer, Node> tmpHashToNode = new HashMap<Integer, Node>();
			for(Node node:nodeList) tmpHashToNode.put(absDigest(node.toStringWithoutJvmroute()), node);
			this.hashToNode = tmpHashToNode;
			HashMap<Node, Integer> tmpNodeToHash = new HashMap<Node, Integer>();
			for(Node node:nodeList) tmpNodeToHash.put(node, absDigest(node.toStringWithoutJvmroute()));
			this.nodeToHash = tmpNodeToHash;
			
			if(isIpV6)
				this.nodesArrayV6 = nodeList.toArray();
			else
				this.nodesArrayV4 = nodeList.toArray();
		}
		
		dumpNodes();
	}

}
