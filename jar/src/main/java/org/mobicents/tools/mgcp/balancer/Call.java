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

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class Call {
	
	private String callId;
	private String host;
	private String notifiedEntity;
	private ConcurrentHashMap <String, Connection> connections = new ConcurrentHashMap <>();

	public Call(String callId, String notifiedEntity)
	{
		this.callId = callId;
		this.notifiedEntity = notifiedEntity;
	}

	public ConcurrentHashMap<String, Connection> getConnections() {
		return connections;
	}
	
	public String getCallId() {
		return callId;
	}

	public String getNotifiedEntity() {
		return notifiedEntity;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	@Override
    public int hashCode()
    { 
		return callId.hashCode();
    }
	
	
	@Override
	public boolean equals(Object obj) 
	{
		if ((obj instanceof Call) && 
				(this.callId.equals(((Call) obj).getCallId()))) 
			return true;
		else 
			return false;
	}
	
	@Override
    public String toString() {
        return "call[" + callId+"] [" + notifiedEntity + "] has connections : "+ connections;
    }
}
