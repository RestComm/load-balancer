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

import java.util.concurrent.CopyOnWriteArrayList;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class MgwHost {
	
	private String address;
	private int port; 
	private CopyOnWriteArrayList<String> calls = new CopyOnWriteArrayList<>();
	
	public MgwHost(String address, int port)
	{
		this.address = address;
		this.port = port;
	}

	public String getAddress() {
		return address;
	}

	public int getPort() {
		return port;
	}

	public CopyOnWriteArrayList<String> getCalls() {
		return calls;
	}
	@Override
    public int hashCode()
    { 
		return (address+port).hashCode();
    }
	
	@Override
	public boolean equals(Object obj) 
	{
		if ((obj instanceof MgwHost) 
				&& (this.address.equals(((MgwHost) obj).getAddress()))
				&&this.port==((MgwHost)obj).getPort())
			
			return true;
		else 
			return false;
	}
	
	@Override
    public String toString() {
        return "MGWHost[" + address +":"+port+"] has calls :" + calls;
    }

}
