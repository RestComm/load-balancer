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

import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;

import java.util.concurrent.ConcurrentHashMap;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class Endpoint {
	
	EndpointIdentifier endpointId;
	NotifiedEntity notifiedEntity;
	ConcurrentHashMap <String, Connection> connections = new ConcurrentHashMap<>();
	
	public Endpoint (EndpointIdentifier endpointId,NotifiedEntity notifiedEntity)
	{
		this.endpointId = endpointId;
		this.notifiedEntity=notifiedEntity;
	}
	public Endpoint (EndpointIdentifier endpointId)
	{
		this.endpointId = endpointId;
	}
	
	public EndpointIdentifier getEndpointId() {
		return endpointId;
	}

	public NotifiedEntity getNotifiedEntity() {
		return notifiedEntity;
	}
	
	public ConcurrentHashMap<String, Connection> getConnections() {
		return connections;
	}

	@Override
    public int hashCode()
    { 
		return (endpointId.getLocalEndpointName()+endpointId.getDomainName()).hashCode();
    }
	
	@Override
	public boolean equals(Object obj) 
	{
		if ((obj instanceof Endpoint) && 
				(this.endpointId.getLocalEndpointName().equals(((Endpoint) obj).getEndpointId().getLocalEndpointName()))
				&&(this.endpointId.getDomainName().equals(((Endpoint) obj).getEndpointId().getDomainName()))) 
			return true;
		else 
			return false;
	}
	
	@Override
    public String toString() {
        return "endpoint [" + endpointId + "] with connections : " + connections;
    }

}
