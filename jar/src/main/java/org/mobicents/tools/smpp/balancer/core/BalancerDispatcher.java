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

package org.mobicents.tools.smpp.balancer.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.smpp.balancer.api.ClientConnection;
import org.mobicents.tools.smpp.balancer.api.LbClientListener;
import org.mobicents.tools.smpp.balancer.api.LbServerListener;
import org.mobicents.tools.smpp.balancer.api.ServerConnection;
import org.mobicents.tools.smpp.balancer.impl.BinderRunnable;
import org.mobicents.tools.smpp.balancer.impl.ClientConnectionImpl;
import org.mobicents.tools.smpp.balancer.impl.RemoteServer;
import org.mobicents.tools.smpp.balancer.impl.ServerConnectionImpl;

import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.pdu.Pdu;
import com.cloudhopper.smpp.ssl.SslConfiguration;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class BalancerDispatcher implements LbClientListener, LbServerListener {
	

	private Map<Long, ServerConnection> serverSessions = new ConcurrentHashMap<Long, ServerConnection>();
	private Map<Long, ClientConnection> clientSessions = new ConcurrentHashMap<Long, ClientConnection>();
	private AtomicInteger notBindClients = new AtomicInteger(0);
	private AtomicInteger notRespondedPackets = new AtomicInteger(0);
	private RemoteServer [] remoteServers;
	private AtomicInteger i = new AtomicInteger(0);
	private ScheduledExecutorService monitorExecutor; 
	private ExecutorService handlerService = Executors.newCachedThreadPool();
	private long reconnectPeriod;
	private BalancerRunner balancerRunner;

	public BalancerDispatcher(BalancerRunner balancerRunner, ScheduledExecutorService monitorExecutor)
	{
		this.balancerRunner = balancerRunner;
		this.reconnectPeriod = Long.parseLong(balancerRunner.balancerContext.properties.getProperty("reconnectPeriod"));
		this.monitorExecutor = monitorExecutor;
		String [] s = balancerRunner.balancerContext.properties.getProperty("remoteServers").split(",");
		this.remoteServers = new RemoteServer[s.length];
		String [] sTmp = new String[2];
		for(int i = 0; i < s.length; i++)
		{
			sTmp = s[i].split(":");
			this.remoteServers[i] = new RemoteServer(sTmp[0].trim(),Integer.parseInt(sTmp[1].trim()));
		}
	}
	
	public AtomicInteger getNotBindClients() 
	{
		return notBindClients;
	}
	public AtomicInteger getNotRespondedPackets() 
	{
		return notRespondedPackets;
	}
	
	public Map<Long, ServerConnection> getServerSessions() 
	{
		return serverSessions;
	}
	
	public Map<Long, ClientConnection> getClientSessions() 
	{
		return clientSessions;
	}

	@Override
	public void bindRequested(Long sessionId, ServerConnectionImpl serverConnection, Pdu packet)  
	{
		balancerRunner.balancerContext.smppRequestsToServer.getAndIncrement();
		balancerRunner.balancerContext.smppRequestsProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToServer.addAndGet(packet.getCommandLength());
		
 		int serverIndex = i.getAndIncrement() % remoteServers.length;
		serverSessions.put(sessionId,serverConnection);
		SmppSessionConfiguration sessionConfig = serverConnection.getConfig();
		sessionConfig.setHost(remoteServers[serverIndex].getIP());
		sessionConfig.setPort(remoteServers[serverIndex].getPort());
		sessionConfig.setUseSsl(Boolean.parseBoolean(balancerRunner.balancerContext.properties.getProperty("isRemoteServerSsl")));
		if(sessionConfig.isUseSsl())
		{
			 SslConfiguration sslConfig = new SslConfiguration();
		     sslConfig.setTrustAll(true);
		     sslConfig.setValidateCerts(true);
		     sslConfig.setValidatePeerCerts(true);
		     sessionConfig.setSslConfiguration(sslConfig);
		}
		clientSessions.put(sessionId, new ClientConnectionImpl(sessionId, sessionConfig, this, monitorExecutor, balancerRunner , packet, serverIndex));
		handlerService.execute(new BinderRunnable(sessionId, packet, serverSessions, clientSessions, serverIndex, remoteServers));

	}

	@Override
	public void unbindRequested(Long sessionID, Pdu packet) 
	{
		balancerRunner.balancerContext.smppRequestsToServer.getAndIncrement();
		balancerRunner.balancerContext.smppRequestsProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToServer.addAndGet(packet.getCommandLength());
		
		clientSessions.get(sessionID).sendUnbindRequest(packet);
	}

	@Override
	public void bindSuccesfull(long sessionID, Pdu packet) 
	{
		balancerRunner.balancerContext.smppResponsesProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToClient.addAndGet(packet.getCommandLength());
		
		serverSessions.get(sessionID).sendBindResponse(packet);
	}
	
	@Override
	public void unbindSuccesfull(long sessionID, Pdu packet) 
	{
		balancerRunner.balancerContext.smppResponsesProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToClient.addAndGet(packet.getCommandLength());
		
		serverSessions.get(sessionID).sendUnbindResponse(packet);
		clientSessions.remove(sessionID);
		serverSessions.remove(sessionID);
	}
	
	@Override
	public void bindFailed(long sessionID, Pdu packet) 
	{
		balancerRunner.balancerContext.smppResponsesProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToClient.addAndGet(packet.getCommandLength());
		
		serverSessions.get(sessionID).sendBindResponse(packet);
		clientSessions.remove(sessionID);
		serverSessions.remove(sessionID);
	}
	
	@Override
	public void smppEntityRequested(Long sessionID, Pdu packet) 
	{
		balancerRunner.balancerContext.smppRequestsToServer.getAndIncrement();
		balancerRunner.balancerContext.smppRequestsProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToServer.addAndGet(packet.getCommandLength());
		
		clientSessions.get(sessionID).sendSmppRequest(packet);
	}

	@Override
	public void smppEntityResponse(Long sessionID, Pdu packet) 
	{
		balancerRunner.balancerContext.smppResponsesProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToClient.addAndGet(packet.getCommandLength());
		
		serverSessions.get(sessionID).sendResponse(packet);
	}

	@Override
	public void smppEntityRequestFromServer(Long sessionId, Pdu packet) 
	{
		balancerRunner.balancerContext.smppRequestsToClient.getAndIncrement();
		balancerRunner.balancerContext.smppRequestsProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToClient.addAndGet(packet.getCommandLength());
		
		serverSessions.get(sessionId).sendRequest(packet);
	}
	
	@Override
	public void smppEntityResponseFromClient(Long sessionId, Pdu packet) 
	{
		balancerRunner.balancerContext.smppResponsesProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToServer.addAndGet(packet.getCommandLength());
		
		clientSessions.get(sessionId).sendSmppResponse(packet);
	}
	
	@Override
	public void connectionLost(Long sessionId, Pdu packet, int serverIndex) 
	{
		serverSessions.get(sessionId).reconnectState(true);
		monitorExecutor.schedule(new BinderRunnable(sessionId, packet, serverSessions, clientSessions, serverIndex, remoteServers), reconnectPeriod, TimeUnit.MILLISECONDS);
	}
	
	@Override
	public void reconnectSuccesful(Long sessionId) 
	{
		serverSessions.get(sessionId).reconnectState(false);
	}
	
	@Override
	public void checkConnection(Long sessionId) 
	{
		serverSessions.get(sessionId).generateEnquireLink();
		clientSessions.get(sessionId).generateEnquireLink();	
	}

	@Override
	public void enquireLinkReceivedFromServer(Long sessionId) 
	{
		serverSessions.get(sessionId).serverSideOk();		
	}

	@Override
	public void closeConnection(Long sessionId) 
	{
		clientSessions.get(sessionId).closeChannel();
		clientSessions.remove(sessionId);
		serverSessions.remove(sessionId);
	}

	@Override
	public void unbindRequestedFromServer(Long sessionId, Pdu packet) 
	{
		balancerRunner.balancerContext.smppRequestsToClient.getAndIncrement();
		balancerRunner.balancerContext.smppRequestsProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToClient.addAndGet(packet.getCommandLength());
		
		serverSessions.get(sessionId).sendUnbindRequest(packet);
	}
	
	@Override
	public void unbindSuccesfullFromServer(Long sessionId, Pdu packet)
	{
		balancerRunner.balancerContext.smppResponsesProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToServer.addAndGet(packet.getCommandLength());
		
		if(clientSessions.get(sessionId)!=null)
		{
			clientSessions.get(sessionId).sendUnbindResponse(packet);
			clientSessions.remove(sessionId);
		}
		serverSessions.remove(sessionId);		
	}
}