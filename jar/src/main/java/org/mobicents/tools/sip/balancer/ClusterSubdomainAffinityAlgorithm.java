package org.mobicents.tools.sip.balancer;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClusterSubdomainAffinityAlgorithm extends CallIDAffinityBalancerAlgorithm {
	private static Logger logger = Logger.getLogger(ClusterSubdomainAffinityAlgorithm.class.getCanonicalName());
	
	protected ConcurrentHashMap<String, List<String>> nodeToNodeGroup = new ConcurrentHashMap<String, List<String>>();
	
	protected SIPNode selectNewNode(SIPNode node, String callId) {
		if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("The assigned node has died. This is the dead node: " + node);
    	}
		SIPNode oldNode = node;
		List<String> alternativeNodes = nodeToNodeGroup.get(oldNode.getIp());
		for(SIPNode check : getBalancerContext().nodes)  {
			for(String alt : alternativeNodes)
			if(check.getIp().equals(alt)) {
				groupedFailover(oldNode, check);
				logger.info("Grouped failover to partner node from " + oldNode + " to " + check);
				return check;
			}
		}
		logger.fine("No alternatives found for " + oldNode + " from " + alternativeNodes);
		
		return super.selectNewNode(oldNode, callId);
	}

	public void init() {
		super.init();
		String subclusterMap = getProperties().getProperty("subclusterMap");
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
			loadSubclusters(getProperties().getProperty("subclusterMap"));
			logger.info("Subclusters reloaded. The groups are as follows:" + dumpSubcluster());
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Subcluster changes were unsuccesful", e);
		}
	}

}
