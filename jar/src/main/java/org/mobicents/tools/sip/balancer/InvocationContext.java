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

import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.mobicents.tools.heartbeat.impl.Node;
import org.mobicents.tools.smpp.multiplexer.DefaultSmppAlgorithm;
import org.mobicents.tools.smpp.multiplexer.MServerConnectionImpl;

public class InvocationContext {
	Logger logger = Logger.getLogger(InvocationContext.class.getCanonicalName());
	public DefaultBalancerAlgorithm balancerAlgorithm;
	public DefaultSmppAlgorithm smppToNodeBalancerAlgorithm;
	public DefaultSmppAlgorithm smppToProviderBalancerAlgorithm;
	
	@SuppressWarnings("rawtypes")
	public InvocationContext(String version, BalancerContext balancerContext) {
		this.version = version;
		try {
			//SIP algorithm init
			Class clazz = Class.forName(balancerContext.algorithmClassName);
			balancerAlgorithm = (DefaultBalancerAlgorithm) clazz.newInstance();
			balancerAlgorithm.balancerContext = balancerContext;
			balancerAlgorithm.setConfiguration(balancerContext.lbConfig);
			balancerAlgorithm.setInvocationContext(this);
			logger.info("Balancer algorithm " + balancerContext.algorithmClassName + " loaded succesfully" +
					" for cluster version = " + version);
			balancerAlgorithm.init();
			//SMPP algorithm to Node init
			if(balancerContext.smppToNodeAlgorithmClassName!=null)
			{
				clazz = Class.forName(balancerContext.smppToNodeAlgorithmClassName);
				smppToNodeBalancerAlgorithm = (DefaultSmppAlgorithm) clazz.newInstance();
				smppToNodeBalancerAlgorithm.setBalancerContext(balancerContext);
				smppToNodeBalancerAlgorithm.setConfiguration(balancerContext.lbConfig);
				smppToNodeBalancerAlgorithm.setInvocationContext(this);
				logger.info("SMPP algorithm to Node side " + balancerContext.smppToNodeAlgorithmClassName + " loaded succesfully");
				smppToNodeBalancerAlgorithm.init();
			}
			//SMPP algorithm to Provider init
			if(balancerContext.smppToProviderAlgorithmClassName!=null)
			{
				clazz = Class.forName(balancerContext.smppToProviderAlgorithmClassName);
				smppToProviderBalancerAlgorithm = (DefaultSmppAlgorithm) clazz.newInstance();
				smppToProviderBalancerAlgorithm.setBalancerContext(balancerContext);
				smppToProviderBalancerAlgorithm.setConfiguration(balancerContext.lbConfig);
				smppToProviderBalancerAlgorithm.setInvocationContext(this);
				logger.info("SMPP algorithm to provider side " + balancerContext.smppToProviderAlgorithmClassName + " loaded succesfully");
				smppToProviderBalancerAlgorithm.init();
			}
			
		} catch (Exception e) {
			throw new RuntimeException("Error loading the algorithm class: " + balancerContext.algorithmClassName, e);
		}
	}
	
	public void stop()
	{
		balancerAlgorithm.stop();
		smppToNodeBalancerAlgorithm.stop();
		smppToProviderBalancerAlgorithm.stop();
	}
	
	private ConcurrentHashMap<KeySip, Node> sipNodeMap = new ConcurrentHashMap<KeySip, Node>();
	private ConcurrentHashMap<KeySip, Node> sipNodeMapV6 = new ConcurrentHashMap<KeySip, Node>();
	
	private ConcurrentHashMap<KeySession, Node> sessionNodeMap = new ConcurrentHashMap<KeySession, Node>();
	private ConcurrentHashMap<KeySession, Node> sessionNodeMapV6 = new ConcurrentHashMap<KeySession, Node>();
	
	public ConcurrentHashMap<Long, MServerConnectionImpl> customers;
	
	public ConcurrentHashMap<KeyHttp, Node> httpNodeMap = new ConcurrentHashMap<KeyHttp, Node>();
	public ConcurrentHashMap<KeySmpp, Node> smppNodeMap = new ConcurrentHashMap<KeySmpp, Node>();
	
	public KeySmpp activeNodeKey = null;
	
	public String version;
	private ConcurrentHashMap<String, Object> attribs = new ConcurrentHashMap<String, Object>();
	public Object getAttribute(String name) {
		return attribs.get(name);
	}
	public void setAttribute(String name, Object val) {
		attribs.put(name, val);
	}
	public void removeAttribute(String name) {
		attribs.remove(name);
	}
	
	public ConcurrentHashMap<KeySip, Node> sipNodeMap(Boolean isIpV6)
	{
		if(isIpV6)
			return sipNodeMapV6;
		else
			return sipNodeMap;
	}
	
	public ConcurrentHashMap<KeySession, Node> sessionNodeMap(Boolean isIpV6)
	{
		if(isIpV6)
			return sessionNodeMapV6;
		else
			return sessionNodeMap;
	}
}