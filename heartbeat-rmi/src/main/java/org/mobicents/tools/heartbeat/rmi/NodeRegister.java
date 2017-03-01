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
package org.mobicents.tools.heartbeat.rmi;

import java.util.ArrayList;

import org.mobicents.tools.heartbeat.api.Node;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public interface NodeRegister {
	
	public Node getNextNode() throws IndexOutOfBoundsException;

	public Node stickSessionToNode(String callID, Node node);
	
	public Node getGluedNode(String callID);
	
	public Node[] getAllNodes();

	public void unStickSessionFromNode(String callID);
	
	public void handlePingInRegister(ArrayList<Node> ping);
	public void forceRemovalInRegister(ArrayList<Node> ping);

	public boolean isNodePresent(String host, int port, String transportParam, String version);
	
	public Node getNode(String host, int port, String transportParam, String version);
	
	public void jvmRouteSwitchover(String fromJvmRoute, String toJvmRoute);
	
	public String getLatestVersion();
	
}