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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheFactory;
import org.jboss.cache.DefaultCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.annotation.ViewChanged;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.notifications.event.ViewChangedEvent;

/**
 * Persistent Consistent Hash algorithm - see http://docs.google.com/present/view?id=dc5jp5vx_89cxdvtxcm Example algorithms section
 * @author vralev
 *
 */
@CacheListener
public class PersistentConsistentHashBalancerAlgorithm extends HeaderConsistentHashBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(PersistentConsistentHashBalancerAlgorithm.class.getCanonicalName());
	
	
	protected Cache cache;
	
	public PersistentConsistentHashBalancerAlgorithm() {
	}
	
	public PersistentConsistentHashBalancerAlgorithm(String headerName) {
		this.sipHeaderAffinityKey = headerName;
	}
	
	@NodeModified
	public void modified(Event event) {
		logger.debug(event.toString());
	}

	public synchronized void nodeAdded(SIPNode node) {
		addNode(node);
		syncNodes();
	}
	
	private void addNode(SIPNode node) {
		Fqn nodes = Fqn.fromString("/BALANCER" + invocationContext.version + "/NODES");
		cache.put(nodes, node, "");
		dumpNodes();
	}

	public synchronized void nodeRemoved(SIPNode node) {
		dumpNodes();
	}
	
	private void dumpNodes() {
		String nodes = "I am " + getBalancerContext().externalHost + ". I see the following nodes are in cache right now (" + nodesArray.length + "):\n";
		
		for(Object object : nodesArray) {
			SIPNode node = (SIPNode) object;
			nodes += node.toString() + " [ALIVE:" + isAlive(node) + "]\n";
		}
		logger.info(nodes);
	}
	

	
	@ViewChanged
	public void viewChanged(ViewChangedEvent event) {
		logger.info(event.toString());
	}
	
	public void init() {
		CacheFactory cacheFactory = new DefaultCacheFactory();
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

		Cache cache = cacheFactory.createCache(configurationInputStream);
		cache.addCacheListener(this);
		cache.create();
		cache.start();
		this.cache = cache;
		/*
		for (SIPNode node : getBalancerContext().nodes) {
			addNode(node);
		}
		syncNodes(context);*/

		this.httpAffinityKey = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getHttpAffinityKey();
		this.sipHeaderAffinityKey = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getSipHeaderAffinityKey();
	}
	
	@Override
	protected void syncNodes() {
		Set nodes = cache.getKeys("/BALANCER" + invocationContext.version + "/NODES");
		if(nodes != null) {
			ArrayList nodeList = new ArrayList();
			nodeList.addAll(nodes);
			Collections.sort(nodeList);
			this.nodesArray = nodeList.toArray();
		}
		dumpNodes();
	}

}
