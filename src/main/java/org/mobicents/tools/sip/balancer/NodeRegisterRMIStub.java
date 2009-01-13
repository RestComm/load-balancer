/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.tools.sip.balancer;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 * 
 * @author jean.deruelle@gmail.com
 *
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
	
}
