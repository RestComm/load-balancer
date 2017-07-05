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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.sip.address.SipURI;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;

import org.apache.log4j.Logger;
import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.event.Event;
import org.infinispan.notifications.cachemanagerlistener.annotation.ViewChanged;
import org.infinispan.notifications.cachemanagerlistener.event.ViewChangedEvent;
import org.mobicents.tools.heartbeat.api.Node;


/**
 * Persistent Consistent Hash algorithm - see http://docs.google.com/present/view?id=dc5jp5vx_89cxdvtxcm Example algorithms section
 * @author vralev
 *
 */
@Listener
public class PersistentConsistentHashBalancerAlgorithm extends HeaderConsistentHashBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(PersistentConsistentHashBalancerAlgorithm.class.getCanonicalName());
	
	


	protected Cache<String, Node> cache;
	protected ConcurrentHashMap<String, Long> cacheTimestamps = new ConcurrentHashMap<String, Long>();
	protected Timer cacheEvictionTimer = new Timer();
	protected int maxCallIdleTime = 600;
	
	public PersistentConsistentHashBalancerAlgorithm() {
	}
	
	public PersistentConsistentHashBalancerAlgorithm(String headerName) {
		this.sipHeaderAffinityKey = headerName;
	}
	
	@CacheEntryModified
	public void modified(Event <Node,String> event) {
		logger.debug(event.toString());
	}
	
	public Node processExternalRequest(Request request,Boolean isIpV6) {

		Integer nodeIndex = null;
		String headerValue = null;
		if(sipHeaderAffinityKey.equals("From")) 
			headerValue = ((SipURI)((FromHeader) request.getHeader(FromHeader.NAME)).getAddress().getURI()).getUser();
		else if(sipHeaderAffinityKey.equals("To")) 
			headerValue = ((SipURI)((ToHeader) request.getHeader(ToHeader.NAME)).getAddress().getURI()).getUser();
		else
			headerValue = ((SIPHeader) request.getHeader(sipHeaderAffinityKey)).getValue();

		Node cachedNode = cache.get(headerValue);
		if(cachedNode!=null&&isAlive(cachedNode))
			return cachedNode;
		
		if(nodesArray(isIpV6).length == 0)
			throw new RuntimeException("No Application Servers registered. All servers are dead.");
		
		int currNodeIndex = hashAffinityKeyword(headerValue,isIpV6);
		
		if(isAlive((Node)nodesArray(isIpV6)[currNodeIndex])) 
			nodeIndex = currNodeIndex;
		else 
			nodeIndex = -1;

		if(nodeIndex<0) {
			return null;
		} else 
		{
			try {
				Node node = (Node) nodesArray(isIpV6)[nodeIndex];
				if(!invocationContext.sipNodeMap(isIpV6).get(new KeySip(node,isIpV6)).isGracefulShutdown()
						&&!invocationContext.sipNodeMap(isIpV6).get(new KeySip(node,isIpV6)).isBad())
				{
					cache.put(headerValue, node);
					
					if(logger.isDebugEnabled())
			    		logger.debug("No node found in the cache. Put to cache : headerValue=" + headerValue + " node="+node);
					
					cacheTimestamps.put(headerValue, System.currentTimeMillis());
					return node;
				}
				else
					return null;
			} catch (Exception e) {
				return null;
			}
		}
	}
	
	@ViewChanged
	public void viewChanged(ViewChangedEvent event) {
		logger.info(event.toString());
	}
	
	public void init() {
		EmbeddedCacheManager manager = null;
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

		try {
			manager = new DefaultCacheManager(configurationInputStream);
		} catch (IOException e) 
		{
			logger.error("Error while creating cache manager ");
		}
		cache = manager.getCache();
		cache.addListener(this);
		cache.start();

		this.httpAffinityKey = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getHttpAffinityKey();
		this.sipHeaderAffinityKey = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getSipHeaderAffinityKey();
		
		if(getConfiguration() != null) {
			Integer maxTimeInCache = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getCallIdAffinityMaxTimeInCache();
			if(maxTimeInCache != null) {
				this.maxCallIdleTime = maxTimeInCache;
			}
		}
		logger.info("Call Idle Time is " + this.maxCallIdleTime + " seconds. Inactive calls will be evicted.");

		final PersistentConsistentHashBalancerAlgorithm thisAlgorithm = this;
		this.cacheEvictionTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				try {
					synchronized (thisAlgorithm) {
						ArrayList<String> oldCalls = new ArrayList<String>();
						Iterator<String> keys = cacheTimestamps.keySet().iterator();
						while(keys.hasNext()) {
							String key = keys.next();
							long time = cacheTimestamps.get(key);
							if(System.currentTimeMillis() - time > 1000*maxCallIdleTime) {
								oldCalls.add(key);
							}
						}
						for(String key : oldCalls) {
							Node remNode = cache.remove(key);
							if(logger.isDebugEnabled())
					    		logger.debug("Remove from cache : header(key)=" + key + " node="+remNode);
							cacheTimestamps.remove(key);
						}
						if(oldCalls.size()>0) {
							logger.info("Reaping idle calls... Evicted " + oldCalls.size() + " calls.");
						}}
				} catch (Exception e) {
					logger.warn("Failed to clean up old calls. If you continue to se this message frequestly and the memory is growing, report this problem.", e);
				}
			}
		}, 0, 6000);
		
	}
	
	public void assignToNode(String id, Node node) {
		cache.put(id, node);
		cacheTimestamps.put(id, System.currentTimeMillis());
	}
	
	@Override
	public void stop() {
		cacheEvictionTimer.cancel();
		cache.stop();
		logger.info("Cached stopped in algorithm");
	}

}