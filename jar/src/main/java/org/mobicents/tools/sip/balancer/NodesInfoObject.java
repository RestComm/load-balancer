package org.mobicents.tools.sip.balancer;

import java.util.ArrayList;

public class NodesInfoObject 
{
	private ArrayList<SIPNode> nodesIPv4;
	private ArrayList<SIPNode> nodesIPv6;
	private ArrayList<SIPNode> badSipNodesIPv4;
	private ArrayList<SIPNode> badSipNodesIPv6;
	private ArrayList<SIPNode> smppRemoteServers;
	private ArrayList<SIPNode> gracefulShutdownNodesIPv4;
	private ArrayList<SIPNode> gracefulShutdownNodesIPv6;
	
	public NodesInfoObject(BalancerRunner balancerRunner)
	{
		this.nodesIPv4 = new ArrayList<SIPNode>(balancerRunner.getLatestInvocationContext().sipNodeMap(false).values());
		this.nodesIPv6 = new ArrayList<SIPNode>(balancerRunner.getLatestInvocationContext().sipNodeMap(true).values());
		this.badSipNodesIPv4 = new ArrayList<SIPNode>(balancerRunner.getLatestInvocationContext().badSipNodeMap(false).values());
		this.badSipNodesIPv6 = new ArrayList<SIPNode>(balancerRunner.getLatestInvocationContext().badSipNodeMap(true).values());
		this.gracefulShutdownNodesIPv4 = new ArrayList<SIPNode>(balancerRunner.getLatestInvocationContext().gracefulShutdownSipNodeMap(false).values());
		this.gracefulShutdownNodesIPv6 = new ArrayList<SIPNode>(balancerRunner.getLatestInvocationContext().gracefulShutdownSipNodeMap(true).values());
		this.smppRemoteServers = new ArrayList<SIPNode>(balancerRunner.getLatestInvocationContext().smppNodeMap.values());
	}

	public ArrayList<SIPNode> getNodesIPv4() {
		return nodesIPv4;
	}

	public void setNodesIPv4(ArrayList<SIPNode> nodesIPv4) {
		this.nodesIPv4 = nodesIPv4;
	}

	public ArrayList<SIPNode> getNodesIPv6() {
		return nodesIPv6;
	}

	public void setNodesIPv6(ArrayList<SIPNode> nodesIPv6) {
		this.nodesIPv6 = nodesIPv6;
	}

	public ArrayList<SIPNode> getSmppRemoteServers() {
		return smppRemoteServers;
	}

	public void setSmppRemoteServers(ArrayList<SIPNode> smppRemoteServers) {
		this.smppRemoteServers = smppRemoteServers;
	}

	public ArrayList<SIPNode> getBadSipNodesIPv4() {
		return badSipNodesIPv4;
	}

	public void setBadSipNodesIPv4(ArrayList<SIPNode> badSipNodesIPv4) {
		this.badSipNodesIPv4 = badSipNodesIPv4;
	}

	public ArrayList<SIPNode> getBadSipNodesIPv6() {
		return badSipNodesIPv6;
	}

	public void setBadSipNodesIPv6(ArrayList<SIPNode> badSipNodesIPv6) {
		this.badSipNodesIPv6 = badSipNodesIPv6;
	}

	public ArrayList<SIPNode> getGracefulShutdownNodesIPv4() {
		return gracefulShutdownNodesIPv4;
	}

	public void setGracefulShutdownNodesIPv4(ArrayList<SIPNode> gracefulShutdownNodesIPv4) {
		this.gracefulShutdownNodesIPv4 = gracefulShutdownNodesIPv4;
	}

	public ArrayList<SIPNode> getGracefulShutdownNodesIPv6() {
		return gracefulShutdownNodesIPv6;
	}

	public void setGracefulShutdownNodesIPv6(ArrayList<SIPNode> gracefulShutdownNodesIPv6) {
		this.gracefulShutdownNodesIPv6 = gracefulShutdownNodesIPv6;
	}

}

