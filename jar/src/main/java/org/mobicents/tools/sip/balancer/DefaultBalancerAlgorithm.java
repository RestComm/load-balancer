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
import java.util.Properties;
import java.util.Set;

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
	
	public SIPNode processHttpRequest(HttpRequest request) {
		if(invocationContext.nodes.size()>0) {
			String callSid = getUrlParameters(request.getUri()).get("CallSid");
			if(callSid!=null)
			{
				String instanceId = getUrlParameters(request.getUri()).get("instanceId");
				if(instanceId!=null)
				{
					for(SIPNode node : invocationContext.nodes)
					{
						if(instanceId.equals(node.getProperties().get("instanceId").toString()))
						{
							return node;
						}
					}
				}
				else
				{
					logger.warn("CallSID parameter exists in HTTP request but instanceId doesn't. LB will send request to node 0");
					return invocationContext.nodes.get(0);
				}
					//if callSID exists but has't match than return node 0
					logger.warn("CallSID parameter exists in HTTP request but has't match to any node. LB will send request to node 0");
					return invocationContext.nodes.get(0);
			}
			else
			{

			String httpSessionId = null;
			httpSessionId = getUrlParameters(request.getUri()).get("jsessionid");
			if(httpSessionId == null) {
				CookieDecoder cookieDocoder = new CookieDecoder();
				String cookieString = request.getHeader("Cookie");
				if(cookieString != null) {
					Set<Cookie> cookies = cookieDocoder.decode(cookieString);
					Iterator<Cookie> cookieIterator = cookies.iterator();
					while(cookieIterator.hasNext()) {
						Cookie c = cookieIterator.next();
						if(c.getName().equalsIgnoreCase("jsessionid")) {
							httpSessionId = c.getValue();
						}
					}
				}
			}
			if(httpSessionId != null) {
				int indexOfDot = httpSessionId.lastIndexOf('.');
				if(indexOfDot>0 && indexOfDot<httpSessionId.length()) {
					//String sessionIdWithoutJvmRoute = httpSessionId.substring(0, indexOfDot);
					String jvmRoute = httpSessionId.substring(indexOfDot + 1);
					SIPNode node = balancerContext.jvmRouteToSipNode.get(jvmRoute);
					
					if(node != null) {
						if(invocationContext.nodes.contains(node)) {
							return node;
						}
					}
				}
				
				// As a failsafe if there is no jvmRoute, just hash the sessionId
				int nodeId = Math.abs(httpSessionId.hashCode()%invocationContext.nodes.size());
				return invocationContext.nodes.get(nodeId);
				
			}
			//if request doesn't have jsessionid (very first request), we choose next node using round robin algorithm
			balancerContext.numberHttpRequest.compareAndSet(Integer.MAX_VALUE, 0);
			return invocationContext.nodes.get(balancerContext.numberHttpRequest.getAndIncrement() % invocationContext.nodes.size());
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
}
