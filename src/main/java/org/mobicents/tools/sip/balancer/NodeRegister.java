package org.mobicents.tools.sip.balancer;

import java.rmi.Remote;
import java.util.ArrayList;

/**
 * <p>
 * </p>
 * 
 * @author M. Ranganathan
 * @author baranowb 
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A> 
 *
 */
public interface NodeRegister extends Remote {

	public SIPNode getNextNode() throws IndexOutOfBoundsException;

	public SIPNode stickSessionToNode(String callID, SIPNode node);
	
	public SIPNode getGluedNode(String callID);

	public void unStickSessionFromNode(String callID);
	
	public void handlePingInRegister(ArrayList<SIPNode> ping);
	public void forceRemovalInRegister(ArrayList<SIPNode> ping);

	public boolean isSIPNodePresent(String host, int port, String transportParam);
	
	public SIPNode getNode(String host, int port, String transportParam);
	
}
