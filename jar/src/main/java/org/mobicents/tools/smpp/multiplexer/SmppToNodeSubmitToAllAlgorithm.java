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
package org.mobicents.tools.smpp.multiplexer;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.mobicents.tools.heartbeat.impl.Node;

import com.cloudhopper.smpp.pdu.Pdu;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class SmppToNodeSubmitToAllAlgorithm extends DefaultSmppAlgorithm{

	protected Iterator<Entry<Long, MClientConnectionImpl>> connectionToProviderIterator = null;
	
	@Override
	public void processSubmitToNode(ConcurrentHashMap<Long, MServerConnectionImpl> customers, Long serverSessionId, Pdu packet) 
	{
		for(Long clientSessionID : customers.keySet())
			customers.get(clientSessionID).sendRequest(serverSessionId,packet);
	}

	@Override
	public void processSubmitToProvider(ConcurrentHashMap<Long, MClientConnectionImpl> connectionsToServers,Long sessionId, Pdu packet) 
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void init() 
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stop() 
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void configurationChanged() 
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public Node processBindToProvider() {
		// TODO Auto-generated method stub
		return null;
	}
	

}
