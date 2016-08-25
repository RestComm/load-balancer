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

package org.mobicents.tools.smpp.balancer.impl;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.SIPNode;
import org.mobicents.tools.smpp.balancer.api.ClientConnection;
import org.mobicents.tools.smpp.balancer.api.ServerConnection;
import org.mobicents.tools.smpp.balancer.impl.ClientConnectionImpl.ClientState;

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.pdu.Pdu;
import com.cloudhopper.smpp.pdu.Unbind;
import com.cloudhopper.smpp.tlv.Tlv;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class BinderRunnable implements Runnable {

	private int index;
	private Pdu packet;
	private ClientConnectionImpl client;
	private Map<Long, ServerConnection> serverSessions;
	private Map<Long, ClientConnection> clientSessions;
	private Long sessionId;
	private SIPNode firstNode;
	private CopyOnWriteArrayList<SIPNode> nodes;

	public BinderRunnable(Long sessionId, Pdu packet, Map<Long, ServerConnection> serverSessions, Map<Long, ClientConnection> clientSessions, SIPNode node, BalancerRunner balancerRunner)
	{
		this.sessionId = sessionId;
		this.packet = packet;
		this.client = (ClientConnectionImpl) clientSessions.get(sessionId);
		this.firstNode = node;
		//this.nodes = balancerRunner.getLatestInvocationContext().nodes;
		this.nodes = (CopyOnWriteArrayList<SIPNode>) balancerRunner.getLatestInvocationContext().sipNodeMap.values();
		this.index = this.nodes.indexOf(node);
		this.serverSessions = serverSessions;
		this.clientSessions = clientSessions;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void run() {
		boolean connectSuccesful = true;
		while (!client.connect()) {
			index ++;
			if (index == nodes.size())	index = 0;
			if (index == nodes.indexOf(firstNode)) {
				connectSuccesful = false;
				break;
			}

			client.getConfig().setHost(nodes.get(index).getIp());
			if(!client.getConfig().isUseSsl())
				client.getConfig().setPort((Integer) nodes.get(index).getProperties().get("smppPort"));
			else
				client.getConfig().setPort((Integer) nodes.get(index).getProperties().get("smppSslPort"));
		}
		
		if (connectSuccesful) 
		{
			client.bind();
		} else {

			if (client.getClientState() == ClientState.INITIAL) 
			{
				BaseBindResp bindResponse = (BaseBindResp) ((BaseBind) packet).createResponse();
				bindResponse.setCommandStatus(SmppConstants.STATUS_SYSERR);
				bindResponse.setSystemId(client.getConfig().getSystemId());
				if (client.getConfig().getInterfaceVersion() >= SmppConstants.VERSION_3_4 && ((BaseBind) packet).getInterfaceVersion() >= SmppConstants.VERSION_3_4) 
				{
					Tlv scInterfaceVersion = new Tlv(SmppConstants.TAG_SC_INTERFACE_VERSION, new byte[] { client.getConfig().getInterfaceVersion() });
					bindResponse.addOptionalParameter(scInterfaceVersion);
				}
				serverSessions.get(sessionId).sendBindResponse(bindResponse);
				client.setClientState(ClientState.CLOSED);
				clientSessions.remove(sessionId);
				serverSessions.remove(sessionId);
			} else 
			{
				serverSessions.get(sessionId).sendUnbindRequest(new Unbind());
				clientSessions.remove(sessionId);

			}
		}
	}
}
