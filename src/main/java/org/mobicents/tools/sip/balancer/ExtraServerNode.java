package org.mobicents.tools.sip.balancer;

public class ExtraServerNode extends SIPNode {
	public ExtraServerNode() {
		super(null,null,0,null,null);
	}
	
	public static ExtraServerNode extraServerNode = new ExtraServerNode();

}
