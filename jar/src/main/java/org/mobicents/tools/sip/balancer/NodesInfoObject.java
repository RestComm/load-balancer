package org.mobicents.tools.sip.balancer;

import java.util.ArrayList;

import org.mobicents.tools.heartbeat.api.Node;

public class NodesInfoObject 
{
	private ArrayList<Node> nodesIPv4;
	private ArrayList<Node> nodesIPv6;
	private ArrayList<Node> smppRemoteServers;

	
	public NodesInfoObject(BalancerRunner balancerRunner)
	{
		this.nodesIPv4 = new ArrayList<Node>(balancerRunner.getLatestInvocationContext().sipNodeMap(false).values());
		this.nodesIPv6 = new ArrayList<Node>(balancerRunner.getLatestInvocationContext().sipNodeMap(true).values());
		this.smppRemoteServers = new ArrayList<Node>(balancerRunner.getLatestInvocationContext().smppNodeMap.values());
	}

	public ArrayList<Node> getNodesIPv4() {
		return nodesIPv4;
	}

	public void setNodesIPv4(ArrayList<Node> nodesIPv4) {
		this.nodesIPv4 = nodesIPv4;
	}

	public ArrayList<Node> getNodesIPv6() {
		return nodesIPv6;
	}

	public void setNodesIPv6(ArrayList<Node> nodesIPv6) {
		this.nodesIPv6 = nodesIPv6;
	}

	public ArrayList<Node> getSmppRemoteServers() {
		return smppRemoteServers;
	}

	public void setSmppRemoteServers(ArrayList<Node> smppRemoteServers) {
		this.smppRemoteServers = smppRemoteServers;
	}

}

