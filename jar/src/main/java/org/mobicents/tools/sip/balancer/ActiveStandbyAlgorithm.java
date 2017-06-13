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

import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.ResponseExt;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.log4j.Logger;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.mobicents.tools.heartbeat.api.Node;

import javax.sip.ListeningPoint;
import javax.sip.message.Request;
import javax.sip.message.Response;

public class ActiveStandbyAlgorithm extends DefaultBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(ActiveStandbyAlgorithm.class.getCanonicalName());
	
	protected AtomicInteger nextNodeCounter = new AtomicInteger(0);
	protected AtomicReference<Node> currNode=new AtomicReference<Node>();	
	private Semaphore selectionSemaphore=new Semaphore(1);
	
	public void processInternalRequest(Request request) 
	{
		if(logger.isDebugEnabled())
			logger.debug("internal request");
	}
	
	public void processInternalResponse(Response response)
	{
		Via via = (Via) response.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		String host = via.getHost();
		Integer port = via.getPort();
		boolean found = false;
		
		Node senderNode = (Node) ((ResponseExt)response).getApplicationData();

		if(logger.isDebugEnabled())
			logger.debug("internal response checking sendernode " + senderNode + " or Via host:port " + host + ":" + port);
		 
		Boolean isIpV6=LbUtils.isValidInet6Address(senderNode.getIp());        	            	
		if(senderNode != null&&invocationContext.sipNodeMap(isIpV6).containsValue(senderNode))
			found = true;
		else if	(invocationContext.sipNodeMap(isIpV6).containsKey(new KeySip(host, port, isIpV6)))
			found = true;

		if(logger.isDebugEnabled())
			logger.debug("internal response node found ? " + found);

		if(!found) {
			Node node = selectNewNode(isIpV6);
			String transportProperty = transport + "Port";
			port = Integer.parseInt(node.getProperties().get(transportProperty));
			if(port == null) throw new RuntimeException("No transport found for node " + node + " " + transportProperty);
			if(logger.isDebugEnabled())
				logger.debug("changing via " + via + "setting new values " + node.getIp() + ":" + port);
			
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
	
	public void processExternalResponse(Response response,Boolean isIpV6) {
		Via via = (Via) response.getHeader(Via.NAME);
		String transport = via.getTransport().toLowerCase();
		Node node = selectNewNode(isIpV6);
		String transportProperty = transport + "Port";
		Integer port = Integer.parseInt(node.getProperties().get(transportProperty));
		if(port == null) throw new RuntimeException("No transport found for node " + node + " " + transportProperty);
		if(logger.isDebugEnabled())
			logger.debug("changing via " + via + "setting new values " + node.getIp() + ":" + port);

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
	
	public Node processExternalRequest(Request request,Boolean isIpV6) 
	{
		Node node = selectNewNode(isIpV6);
		if(node == null) return null;
		if(logger.isDebugEnabled())
    		logger.debug("No node found in the affinity map. It is null. We select new node: " + node);
		
		return node;
		
	}
	
	@Override
	public synchronized Node processHttpRequest(HttpRequest request)
	{
		Node node = selectNewNode(false);
		if(node == null) return null;
		if(logger.isDebugEnabled())
    		logger.debug("No node found in the affinity map. It is null. We select new node: " + node);
		return node;
	}
	
	protected Node selectNewNode(Boolean isIpV6)
	{		
		Node node = currNode.get();		
		//Boolean isIpV6=LbUtils.isValidInet6Address(node.getIp());
		if(node!=null)
		{
			KeySip keyNode = new KeySip(node,isIpV6);
			if(invocationContext.sipNodeMap(isIpV6).containsKey(keyNode)
					&&!invocationContext.sipNodeMap(isIpV6).get(keyNode).isGracefulShutdown()
					&&!invocationContext.sipNodeMap(isIpV6).get(keyNode).isBad())
				return node;
		}
		try
		{
			selectionSemaphore.acquire();
		}
		catch(InterruptedException ex)
		{
			
		}
		
		node=nextAvailableNode(false);		
		if(node == null) 
		{
			if(logger.isDebugEnabled())
	    		logger.debug("no nodes available return null");

			return null;
		}

		currNode.set(node);
		selectionSemaphore.release();
		return node;
	}
	
	protected Node nextAvailableNode(Boolean isIpV6)
	{
		Iterator<Entry<KeySip, Node>> currIt = null; 
		if(isIpV6)
			currIt = ipv6It;
		else
			currIt = ipv4It;
		if(currIt==null)
		{
			currIt = invocationContext.sipNodeMap(isIpV6).entrySet().iterator();
			if(isIpV6)
				ipv6It = currIt;
			else
				ipv4It = currIt;
		}
		Entry<KeySip, Node> pair = null;
		while(currIt.hasNext())
		{
			pair = currIt.next();
			if(invocationContext.sipNodeMap(isIpV6).containsKey(pair.getKey()))
				return pair.getValue();
		}
		currIt = invocationContext.sipNodeMap(isIpV6).entrySet().iterator();
		if(isIpV6)
			 ipv6It = currIt;
		else
			 ipv4It = currIt;
		if(currIt.hasNext())
		{
			pair = currIt.next();
			return pair.getValue();
		}
		else
			return null;
			 
	}

	@Override
	public void init() 
	{
	}
	
}