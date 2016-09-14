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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;

import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.mobicents.tools.http.balancer.HttpBalancerForwarder;

public abstract class DefaultBalancerAlgorithm implements BalancerAlgorithm {
	
	private static final Logger logger = Logger.getLogger(DefaultBalancerAlgorithm.class.getCanonicalName());
	protected Properties properties;
	protected BalancerContext balancerContext;
	protected InvocationContext invocationContext;
	protected Iterator<Entry<KeySip, SIPNode>> it = null;
	protected Iterator<SIPNode> httpRequestIterator = null;
	protected Iterator <SIPNode> instanceIdIterator = null;

	public void setProperties(Properties properties) {
		this.properties = properties;
	}
	
	public void setInvocationContext(InvocationContext ctx) {
		this.invocationContext = ctx;
	}
	
	public InvocationContext getInvocationContext() {
		return invocationContext;
	}

	public BalancerContext getBalancerContext() {
		return balancerContext;
	}

	public Properties getProperties() {
		return properties;
	}
	
	public void processInternalRequest(Request request) {
		
	}
	public void configurationChanged() {
		
	}
	
	public synchronized SIPNode processHttpRequest(HttpRequest request) {
		//if(invocationContext.nodes.size()>0) {
		if(invocationContext.sipNodeMap.size()>0) {
			String instanceId = getInstanceId(request.getUri());
			if(instanceId!=null)
				return getNodeByInstanceId(instanceId);
			
			String httpSessionId = null;
			httpSessionId = getUrlParameters(request.getUri()).get("jsessionid");
			
			if(httpSessionId == null) 
				httpSessionId = getParameterFromCookie(request, "jsessionid");
				
			if(httpSessionId != null) {
				int indexOfDot = httpSessionId.lastIndexOf('.');
				if(indexOfDot>0 && indexOfDot<httpSessionId.length()) {
					//String sessionIdWithoutJvmRoute = httpSessionId.substring(0, indexOfDot);
					String jvmRoute = httpSessionId.substring(indexOfDot + 1);
					SIPNode node = balancerContext.jvmRouteToSipNode.get(jvmRoute);
					
					if(node != null) {
						//if(invocationContext.nodes.contains(node)) {
						if(invocationContext.sipNodeMap.containsValue(node)) {
							return node;
						}
					}
				}
				
				// As a failsafe if there is no jvmRoute, just hash the sessionId
				//int nodeId = Math.abs(httpSessionId.hashCode()%invocationContext.nodes.size());
				//return invocationContext.nodes.get(nodeId);
				logger.warn("As a failsafe if there is no jvmRoute. LB will send request to node accordingly RR algorithm");
				if(httpRequestIterator==null)
					httpRequestIterator = invocationContext.sipNodeMap.values().iterator();
				if(httpRequestIterator.hasNext())
				{
					return httpRequestIterator.next();
				}
				else
				{
					httpRequestIterator = invocationContext.sipNodeMap.values().iterator();
					if(httpRequestIterator.hasNext())
					{
						return httpRequestIterator.next();
					}
					else
						return null;
				}
				
			}
			//if request doesn't have jsessionid (very first request), we choose next node using round robin algorithm
//			balancerContext.numberHttpRequest.compareAndSet(Integer.MAX_VALUE, 0);
//			return invocationContext.nodes.get(balancerContext.numberHttpRequest.getAndIncrement() % invocationContext.nodes.size());
			if(httpRequestIterator==null)
				httpRequestIterator = invocationContext.sipNodeMap.values().iterator();
			if(httpRequestIterator.hasNext())
				return httpRequestIterator.next();
			else
			{
				httpRequestIterator = invocationContext.sipNodeMap.values().iterator();
				if(httpRequestIterator.hasNext())
					return httpRequestIterator.next();
				else
					return null;
			}
		} else {
			String unavailaleHost = getProperties().getProperty("unavailableHost");
			if(unavailaleHost != null) {
				SIPNode node = new SIPNode(unavailaleHost, unavailaleHost);
				node.getProperties().put("httpPort", 80);
				return node;
			} else {
				return null;
			}
		}
	}
	
	@Override
	public void proxyMessage(ChannelHandlerContext ctx, MessageEvent e){}
	
	@Override
	public boolean blockInternalRequest(Request request){
	    return false;
	}
	
	public SIPNode processAssignedExternalRequest(Request request, SIPNode assignedNode) {
		return assignedNode;
	}

	private HashMap<String,String> getUrlParameters(String url) {
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
	
	private String getInstanceId(String url)
	{
		String[] tokens = url.split("/");
		if(tokens.length>6)
		{
			if(tokens[6].split("-").length>1)
				return tokens[6].split("-")[0];
			else
				return null;
		}	
		return null;
	}
	
	private String getParameterFromCookie(HttpRequest request, String parameter){
		
			CookieDecoder cookieDocoder = new CookieDecoder();
			String cookieString = request.getHeader("Cookie");
			if(cookieString != null) {
				Set<Cookie> cookies = cookieDocoder.decode(cookieString);
				Iterator<Cookie> cookieIterator = cookies.iterator();
				while(cookieIterator.hasNext()) {
					Cookie c = cookieIterator.next();
					if(c.getName().equalsIgnoreCase("jsessionid")) {
						return c.getValue();
					}
				}
			}
		return null;
	}
	
	public void processInternalResponse(Response response) {
		
	}
	
	public void processExternalResponse(Response response) {
		
	}
	
	public void start() {
		
	}
	
	public void stop() {
		
	}
	
	public void nodeAdded(SIPNode node) {
		
	}

	public void nodeRemoved(SIPNode node) {
		
	}
	
	public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute) {
		
	}
	
	public void assignToNode(String id, SIPNode node) {
		
	}	
	
	private SIPNode getNodeByInstanceId(String instanceId)
	{
		if(logger.isDebugEnabled())
			logger.debug("Node by instanceId("+instanceId+") getting");
		SIPNode node = invocationContext.httpNodeMap.get(new KeyHttp(Integer.parseInt(instanceId)));
		if(node!=null)
		{
			return node;
		}
		else
		{
			if(instanceIdIterator==null)
				instanceIdIterator = invocationContext.httpNodeMap.values().iterator();
			logger.warn("instanceId exists in HTTP request but doesn't match to any node. LB will send request to node accordingly RR algorithm");
			if(instanceIdIterator.hasNext())
				return instanceIdIterator.next();
			else
			{
				instanceIdIterator = invocationContext.httpNodeMap.values().iterator();
				if(instanceIdIterator.hasNext())
					return instanceIdIterator.next();
				else
					return null;
			}
		}
	}
}
