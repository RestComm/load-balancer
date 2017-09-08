/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
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
package org.mobicents.tools.mgcp.balancer;

import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.heartbeat.api.Protocol;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class KeyMgcp {

	private String hostName;
	
	public KeyMgcp(Node node)
	{
		this.hostName = node.getIp() + ":" + node.getProperties().get(Protocol.MGCP_PORT);
	}
	
	public String getHostName() {
		return hostName;
	}

	@Override
    public int hashCode()
    { 
		return hostName.hashCode();
    }
	
	@Override
	public boolean equals(Object obj) 
	{
		if ((obj instanceof KeyMgcp) && (this.hostName.equals(((KeyMgcp) obj).getHostName()))) 
			return true;
		else 
			return false;
	}
	
	@Override
	public String toString()
	{
		return hostName;
	}
}
