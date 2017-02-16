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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.mobicents.tools.heartbeat.impl.Node;

public class ClusterSubdomainAffinityAlgorithm extends CallIDAffinityBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(ClusterSubdomainAffinityAlgorithm.class.getCanonicalName());
	
	protected ConcurrentHashMap<String, List<String>> nodeToNodeGroup = new ConcurrentHashMap<String, List<String>>();
	
	protected Node selectNewNode(Node node, String callId,Boolean isIpV6) {
		if(logger.isDebugEnabled()) {
    		logger.debug("The assigned node has died. This is the dead node: " + node);
    	}
		Node oldNode = node;
		List<String> alternativeNodes = nodeToNodeGroup.get(oldNode.getIp());
		//for(Node check : invocationContext.nodes)  { 
		for(Node check : invocationContext.sipNodeMap(isIpV6).values())  {
			for(String alt : alternativeNodes)
				if(check.getIp().equals(alt)) {
					groupedFailover(oldNode, check);
					logger.info("Grouped failover to partner node from " + oldNode + " to " + check);
					return check;
			}
		}
		logger.info("No alternatives found for " + oldNode + " from " + alternativeNodes);
		
		return super.selectNewNode(oldNode, callId, isIpV6);
	}

	public void init() {
		super.init();
		String subclusterMap = getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getSubclusterMap();
		logger.info("Subcluster map: " + subclusterMap);
		loadSubclusters(subclusterMap);
		logger.info("Grouped failover is set to " + this.groupedFailover);
	}
	
	public void loadSubclusters(String subclustersString) {
		ConcurrentHashMap<String, List<String>> map = new ConcurrentHashMap<String, List<String>>();
		if(subclustersString != null) {
			subclustersString = subclustersString.replaceAll(" ", "");
			String[] groups = subclustersString.split("\\)\\(");
			for(int q=0; q<groups.length; q++) {
				String group = groups[q];
				group = group.replaceAll("\\(", "").replaceAll("\\)","");
				String[] hosts = group.split(",");
				LinkedList<String> hostGroupList = new LinkedList<String>();
				for(String host:hosts) {
					if(host.length()>0) {
						if(hostGroupList.contains(host)) {
							throw new RuntimeException("Duplicate host " + host + " in " + hosts);
						}
						hostGroupList.add(host);
					}
				}
				for(String host:hosts) {
					List<String> tmp = new LinkedList<String>(hostGroupList);
					tmp.remove(host);
					map.put(host, tmp);
				}
			}
		}
		nodeToNodeGroup = map;
	}
	
	public String dumpSubcluster() {
		String result = "";
		for(String host:nodeToNodeGroup.keySet()) {
			String mapped = host + ": " + nodeToNodeGroup.get(host);
			result += mapped + "\n";
		}
		return result;
	}
	
	public void configurationChanged() {
		super.configurationChanged();
		try {
			loadSubclusters(getConfiguration().getSipConfiguration().getAlgorithmConfiguration().getSubclusterMap());
			logger.info("Subclusters reloaded. The groups are as follows:" + dumpSubcluster());
		} catch (Exception e) {
			logger.error("Subcluster changes were unsuccesful", e);
		}
	}

}
