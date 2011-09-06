/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
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
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

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
	
	public SIPNode[] getAllNodes();

	public void unStickSessionFromNode(String callID);
	
	public void handlePingInRegister(ArrayList<SIPNode> ping);
	public void forceRemovalInRegister(ArrayList<SIPNode> ping);

	public boolean isSIPNodePresent(String host, int port, String transportParam, String version);
	
	public SIPNode getNode(String host, int port, String transportParam, String version);
	
	public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute);
	
	public String getLatestVersion();
	
}
