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

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.smpp.balancer.api.Dispatcher;

import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.Pdu;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class MBalancerDispatcher extends Dispatcher implements MLbServerListener {

	private ConcurrentHashMap<String, UserSpace> userSpaces = new ConcurrentHashMap<String, UserSpace>();
	private AtomicInteger notBindClients = new AtomicInteger(0);
	private AtomicInteger notRespondedPackets = new AtomicInteger(0);
	private Node [] nodes;
	private BalancerRunner balancerRunner;
	private ScheduledExecutorService monitorExecutor;
	
	public MBalancerDispatcher(BalancerRunner balancerRunner, ScheduledExecutorService monitorExecutor)
	{
		this.balancerRunner = balancerRunner;
		this.monitorExecutor = monitorExecutor;
		String [] s = balancerRunner.balancerContext.lbConfig.getSmppConfiguration().getRemoteServers().split(",");
		this.nodes = new Node[s.length];
		String [] sTmp = new String[2];
		for(int i = 0; i < s.length; i++)
		{
			sTmp = s[i].split(":");
			this.nodes[i] = new Node("SMPP server " + i, sTmp[0].trim());
			this.nodes[i].getProperties().put("smppPort", sTmp[1].trim());
		}
	}
	
	@Override
	public UserSpace bindRequested(Long sessionId, MServerConnectionImpl customer, Pdu packet) 
	{
		balancerRunner.balancerContext.smppRequestsToServer.getAndIncrement();
		balancerRunner.incMessages();
		balancerRunner.balancerContext.smppRequestsProcessedById.get(packet.getCommandId()).incrementAndGet();
		//only first bind sends to server we not add it to statistic
		//balancerRunner.balancerContext.smppBytesToServer.addAndGet(packet.getCommandLength());
		UserSpace userSpace = userSpaces.get(((BaseBind)packet).getSystemId());
		if(userSpace==null)
		{
			userSpace = new UserSpace(((BaseBind)packet).getSystemId(),((BaseBind)packet).getPassword(), nodes, this.balancerRunner, monitorExecutor, this);
			UserSpace oldSpace=userSpaces.putIfAbsent(((BaseBind)packet).getSystemId(), userSpace);
			if(oldSpace!=null)
				userSpace=oldSpace;				
		}
		
		return userSpace;
	}

	public AtomicInteger getNotBindClients() {
		return notBindClients;
	}

	public Map<String, UserSpace> getUserSpaces() {
		return userSpaces;
	}
	public AtomicInteger getNotRespondedPackets() 
	{
		return notRespondedPackets;
	}
	
	public void remoteServersUpdated()
	{
		for(Entry<String, UserSpace> entry : userSpaces.entrySet())
			entry.getValue().remoteServersUpdated();
	}
}