/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package org.mobicents.tools.sip.balancer;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
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
	
	/**
	 * Migrate from one jvmRoute to another.
	 * 
	 * @param fromJvmRoute
	 * @param toJvmRoute
	 * @throws RemoteException 
	 */
	public void switchover(String fromJvmRoute, String toJvmRoute) throws RemoteException;
	
}
