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
import org.mobicents.tools.sip.balancer.InvocationContext;
import org.mobicents.tools.sip.balancer.SIPNode;
import org.mobicents.tools.smpp.balancer.api.ClientConnection;
import org.mobicents.tools.smpp.balancer.api.LbClientListener;
import org.mobicents.tools.smpp.balancer.api.LbServerListener;
import org.mobicents.tools.smpp.balancer.api.ServerConnection;
import org.mobicents.tools.smpp.balancer.impl.BinderRunnable;
import org.mobicents.tools.smpp.balancer.impl.ClientConnectionImpl;
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
	private ScheduledExecutorService monitorExecutor; 
	private ExecutorService handlerService = Executors.newCachedThreadPool();
	private long reconnectPeriod;
	private BalancerRunner balancerRunner;
	private SIPNode node = new SIPNode();
	private AtomicInteger counterConnections = new AtomicInteger(0);

	public BalancerDispatcher(BalancerRunner balancerRunner, ScheduledExecutorService monitorExecutor)
	{
		this.balancerRunner = balancerRunner;
		this.reconnectPeriod = balancerRunner.balancerContext.lbConfig.getSmppConfiguration().getReconnectPeriod();
		this.monitorExecutor = monitorExecutor;
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
	public AtomicInteger getCounterConnections() 
	{
		return counterConnections;
	}

	@Override
	public void bindRequested(Long sessionId, ServerConnectionImpl serverConnection, Pdu packet)  
	{
		InvocationContext invocationContext = balancerRunner.getLatestInvocationContext();
		
		balancerRunner.balancerContext.smppRequestsToServer.getAndIncrement();
		balancerRunner.incMessages();
		balancerRunner.balancerContext.smppRequestsProcessedById.get(packet.getCommandId()).incrementAndGet();
		balancerRunner.balancerContext.smppBytesToServer.addAndGet(packet.getCommandLength());
		serverSessions.put(sessionId,serverConnection);
		
		SmppSessionConfiguration sessionConfig = serverConnection.getConfig();
		if(!serverConnection.getConfig().isUseSsl())
			sessionConfig.setUseSsl(false);
		else
			sessionConfig.setUseSsl(!balancerRunner.balancerContext.terminateTLSTraffic);
		
		counterConnections.compareAndSet(Integer.MAX_VALUE, 0);
		synchronized (node) 
		{
			//node = invocationContext.nodes.get(counterConnections.getAndIncrement() % invocationContext.nodes.size());
			node = invocationContext.sipNodeMap(false).elements().nextElement();//(counterConnections.getAndIncrement() % invocationContext.sipNodeMap.size());
			sessionConfig.setHost(node.getIp());
			if(!sessionConfig.isUseSsl())
				sessionConfig.setPort((Integer) node.getProperties().get("smppPort"));
			else
				sessionConfig.setPort((Integer) node.getProperties().get("smppSslPort"));
		}
		clientSessions.put(sessionId, new ClientConnectionImpl(sessionId, sessionConfig, this, monitorExecutor, balancerRunner , packet, node));
		handlerService.execute(new BinderRunnable(sessionId, packet, serverSessions, clientSessions, node, balancerRunner));

	}

	@Override
	public void unbindRequested(Long sessionID, Pdu packet) 
	{
		balancerRunner.balancerContext.smppRequestsToServer.getAndIncrement();
		balancerRunner.incMessages();
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
		balancerRunner.incMessages();
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
		balancerRunner.incMessages();
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
	public void connectionLost(Long sessionId, Pdu packet, SIPNode node) 
	{
		serverSessions.get(sessionId).reconnectState(true);
		monitorExecutor.schedule(new BinderRunnable(sessionId, packet, serverSessions, clientSessions, node, balancerRunner), reconnectPeriod, TimeUnit.MILLISECONDS);
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
		balancerRunner.incMessages();
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