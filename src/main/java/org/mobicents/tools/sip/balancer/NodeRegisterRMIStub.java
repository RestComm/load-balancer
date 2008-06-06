package org.mobicents.tools.sip.balancer;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface NodeRegisterRMIStub extends Remote {

	public void handlePing(ArrayList<SIPNode> ping) throws RemoteException;
	
}
