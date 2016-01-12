/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015-2016, Red Hat, Inc. and individual contributors
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

package org.mobicents.tools.smpp.balancer.impl;

import java.util.Map;

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
	private RemoteServer[] remoteServers;
	private Map<Long, ServerConnectionImpl> serverSessions;
	private Map<Long, ClientConnectionImpl> clientSessions;
	private Long sessionId;
	private int firstServer;

	public BinderRunnable(Long sessionId, Pdu packet,
			Map<Long, ServerConnectionImpl> serverSessions,
			Map<Long, ClientConnectionImpl> clientSessions, int serverIndex,
			RemoteServer[] remoteServers) {
		this.sessionId = sessionId;
		this.packet = packet;
		this.client = clientSessions.get(sessionId);
		this.firstServer = serverIndex;
		this.index = serverIndex;
		this.remoteServers = remoteServers;
		this.serverSessions = serverSessions;
		this.clientSessions = clientSessions;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void run() {
		boolean connectSuccesful = true;
		while (!client.connect()) {
			index ++;
			if (index == remoteServers.length)	index = 0;
			if (index == firstServer) {
				connectSuccesful = false;
				break;
			}

			client.getConfig().setHost(remoteServers[index].getIP());
			client.getConfig().setPort(remoteServers[index].getPort());
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
				if (client.getConfig().getInterfaceVersion() >= SmppConstants.VERSION_3_4 && ((BaseBind) packet).getInterfaceVersion() >= SmppConstants.VERSION_3_4) {
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
