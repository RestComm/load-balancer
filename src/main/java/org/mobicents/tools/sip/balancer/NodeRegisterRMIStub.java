package org.mobicents.tools.sip.balancer;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface NodeRegisterRMIStub extends Remote {
	/**
	 * Method for nodes to send keep alives to the load balancer
	 * @param ping list of SIPNode to add to the load balancer's list of availables nodes to dispatch requests to
	 * @throws RemoteException if anything goes wrong during the RMI call
	 */
	public void handlePing(ArrayList<SIPNode> ping) throws RemoteException;
	// Force removal added for Issue 308 (http://code.google.com/p/mobicents/issues/detail?id=308)
	/**
	 * Method for nodes to force their removal from the load balancer.
	 * Useful if a node is shutdown cleanly (and the keepalive timeout is long) and can inform the load balancer of its stop 
	 * @param ping list of SIPNode to remove from the load balancer's list of availables nodes to dispatch requests to
	 * @throws RemoteException if anything goes wrong during the RMI call
	 */
	public void forceRemoval(ArrayList<SIPNode> ping) throws RemoteException;
	
}
