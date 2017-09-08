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

import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class Connection {

	private String connectionId;
	private String host;
	private NotifiedEntity notifiedEntity;
	private Call call;
	private Endpoint endpoint;
	
	
	
	public Connection (String connectionId, String host,NotifiedEntity notifiedEntity)
	{
		this.connectionId = connectionId;
		this.host = host;
		this.notifiedEntity=notifiedEntity;
	}


	public Call getCall() {
		return call;
	}


	public void setCall(Call call) {
		this.call = call;
	}


	public Endpoint getEndpoint() {
		return endpoint;
	}


	public void setEndpoint(Endpoint endpoint) {
		this.endpoint = endpoint;
	}


	public String getConnectionId() {
		return connectionId;
	}


	public String getHost() {
		return host;
	}


	public NotifiedEntity getNotifiedEntity() {
		return notifiedEntity;
	}


	public void setNotifiedEntity(NotifiedEntity notifiedEntity) {
		this.notifiedEntity = notifiedEntity;
	}


	@Override
    public int hashCode()
    { 
		return (connectionId+host).hashCode();
    }
	
	
	@Override
	public boolean equals(Object obj) 
	{
		if ((obj instanceof Connection) && 
				(this.connectionId.equals(((Connection) obj).getConnectionId()))
				&&(this.host.equals(((Connection) obj).getHost()))) 
			return true;
		else 
			return false;
	}
	
	@Override
    public String toString() {
        return "Connection [" + connectionId+"] [" + host + "]" + " has callId : "+ call.getCallId() +" and endpoint : " + endpoint.getEndpointId();
    }
}
