package org.mobicents.tools.sip.balancer;

public class RouteHeaderHints {
	public SIPNode serverAssignedNode;
	public boolean subsequentRequest;
	public RouteHeaderHints(SIPNode node, boolean subsequent) {
		this.serverAssignedNode = node;
		this.subsequentRequest = subsequent;
	}
}
