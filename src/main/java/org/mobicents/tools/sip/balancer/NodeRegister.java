package org.mobicents.tools.sip.balancer;

import java.rmi.Remote;
import java.util.ArrayList;

public interface NodeRegister extends Remote{

	public SIPNode getNextNode() throws IndexOutOfBoundsException;

	public SIPNode stickSessionToNode(String callID);
	
	public SIPNode getGluedNode(String callID);

	public void unStickSessionFromNode(String callID);
	
	public void handlePingInRegister(ArrayList<SIPNode> ping);
	
}
