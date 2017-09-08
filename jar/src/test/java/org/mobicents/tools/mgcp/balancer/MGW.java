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

import jain.protocol.ip.mgcp.JainMgcpCommandEvent;
import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.JainMgcpProvider;
import jain.protocol.ip.mgcp.JainMgcpResponseEvent;
import jain.protocol.ip.mgcp.message.Constants;
import jain.protocol.ip.mgcp.message.CreateConnection;
import jain.protocol.ip.mgcp.message.CreateConnectionResponse;
import jain.protocol.ip.mgcp.message.DeleteConnection;
import jain.protocol.ip.mgcp.message.DeleteConnectionResponse;
import jain.protocol.ip.mgcp.message.ModifyConnectionResponse;
import jain.protocol.ip.mgcp.message.NotificationRequest;
import jain.protocol.ip.mgcp.message.NotificationRequestResponse;
import jain.protocol.ip.mgcp.message.Notify;
import jain.protocol.ip.mgcp.message.RestartInProgress;
import jain.protocol.ip.mgcp.message.parms.CallIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.EventName;
import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;
import jain.protocol.ip.mgcp.message.parms.RequestIdentifier;
import jain.protocol.ip.mgcp.message.parms.RestartMethod;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;
import jain.protocol.ip.mgcp.pkg.MgcpEvent;
import jain.protocol.ip.mgcp.pkg.PackageName;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.TooManyListenersException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.jboss.netty.channel.MessageEvent;
import org.restcomm.media.client.mgcp.stack.JainMgcpExtendedListener;
import org.restcomm.media.client.mgcp.stack.JainMgcpStackProviderImpl;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.mobicents.tools.heartbeat.impl.ClientController;
import org.mobicents.tools.heartbeat.interfaces.IClientListener;

import com.google.gson.JsonObject;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class MGW implements JainMgcpExtendedListener, IClientListener {

	private static Logger logger = Logger.getLogger(MGW.class);
	private ExecutorService executor;

	private ClientController clientController;
	private Node node;

	private RequestIdentifier requestIdentifier;
	private JainMgcpStackProviderImpl mgwProvider;
	public static int counterEnpoint = 0;
	private NotifiedEntity notifiedEntity;
	private EndpointIdentifier specificEndpoint1 = null;
	private EndpointIdentifier specificEndpoint2 = null;
	private ConnectionIdentifier connectionIdentifier1 = null;
	private ConnectionIdentifier connectionIdentifier2 = null;
	private ConnectionIdentifier connectionIdentifier3 = null;
	private int commandsCount = 0;
	private CallIdentifier callIdentifier = null;

	private static int delta = 0;
	private int udpPort;
	private int mgcpPort;
	private int heartbeatPort;
	
	
	
	private String lbAddress = "127.0.0.1";
	private int lbHeartbeatPort = 2610;
	private int lbMgcpInternalPort = 2627;
	private int heartbeatPeriod = 1000;
	
	private ArrayList <JainMgcpCommandEvent> receivedCommands = new ArrayList<>();
	private ArrayList <JainMgcpResponseEvent> receivedResponses = new ArrayList<>();


	public MGW(JainMgcpProvider mgwProvider, int heartbeatPort)
	{
		this.mgwProvider = (JainMgcpStackProviderImpl)mgwProvider;
		try {
			this.mgwProvider.addJainMgcpListener(this);
		} catch (TooManyListenersException e) {
			e.printStackTrace();
		}
		this.mgcpPort = mgwProvider.getJainMgcpStack().getPort();
		this.udpPort = 4060 + delta++;
		this.heartbeatPort = heartbeatPort;
	}
	
	public void start() 
	{
		executor = Executors.newCachedThreadPool();
		//ping
		node = new Node("MGW", "127.0.0.1");		
		node.getProperties().put("mgcpPort",""+ mgcpPort);
		node.getProperties().put("udpPort",""+ udpPort);
		node.getProperties().put(Protocol.SESSION_ID, ""+System.currentTimeMillis());
		node.getProperties().put(Protocol.HEARTBEAT_PORT, ""+heartbeatPort);
		clientController = new ClientController(this, lbAddress, lbHeartbeatPort, node, 5000 , heartbeatPeriod, executor);
		clientController.startClient();
	}
	
	public void stop() {
		clientController.stopClient(false);
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if(executor == null) return; // already stopped
		
		executor.shutdownNow();
		executor = null;
		
		//cleaning everything
		logger.info("MGW stoped : " + node);
	}
	
	public void sendNotify() {
		commandsCount++;
		try {
			mgwProvider.addJainMgcpListener(this);
			Notify notify = new Notify(this, specificEndpoint2, mgwProvider.getUniqueRequestIdentifier(), 
					new EventName[] { new EventName(PackageName.Announcement, MgcpEvent.oc, new ConnectionIdentifier("1"))});
			notify.setTransactionHandle(mgwProvider.getUniqueTransactionHandler());
			notify.setNotifiedEntity(notifiedEntity);
			notify.setRequestIdentifier(requestIdentifier);
			mgwProvider.sendMgcpEvents(new JainMgcpEvent[] { notify });
			logger.debug(" Notify command sent for TxId " + notify.getTransactionHandle());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void sendRestartInProgress() {
		commandsCount++;
	try{
		RestartInProgress rsip = new RestartInProgress(this, specificEndpoint1, RestartMethod.Disconnected);
		rsip.setTransactionHandle(mgwProvider.getUniqueTransactionHandler());
		InetAddress lbInetAddress = InetAddress.getByName(lbAddress);
		mgwProvider.sendMgcpEvents(new JainMgcpEvent[] { rsip }, lbInetAddress, lbMgcpInternalPort);
	} catch (Exception e) {
		e.printStackTrace();
	}
	}
	
	public void sendDeleteConnectionWithEnpoint() {
		commandsCount++;
		try {
			DeleteConnection deleteConnection = null;
			if(specificEndpoint1!=null)
			{
				deleteConnection = new DeleteConnection(this, specificEndpoint1);
				specificEndpoint1=null;
			}
			else
			{
				deleteConnection = new DeleteConnection(this, specificEndpoint2);
			}
			deleteConnection.setTransactionHandle(mgwProvider.getUniqueTransactionHandler());
			InetAddress lbInetAddress = InetAddress.getByName(lbAddress);
			mgwProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection },lbInetAddress, lbMgcpInternalPort);
		} catch (Exception e) {
			e.printStackTrace();

		}
	}
	public void sendDeleteConnectionWithCall() {
		commandsCount++;
		try {
			DeleteConnection deleteConnection = null;
			if(specificEndpoint1!=null)
			{
				deleteConnection = new DeleteConnection(this, callIdentifier, specificEndpoint1);
				specificEndpoint1=null;
			}
			else
			{
				deleteConnection = new DeleteConnection(this, callIdentifier, specificEndpoint2);
			}
			deleteConnection.setTransactionHandle(mgwProvider.getUniqueTransactionHandler());
			InetAddress lbInetAddress = InetAddress.getByName(lbAddress);
			mgwProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection },lbInetAddress, lbMgcpInternalPort);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void sendDeleteConnectionWithConnection() {
		commandsCount++;
		try {
			DeleteConnection deleteConnection = null;
			if(connectionIdentifier1!=null)
			{
				deleteConnection = new DeleteConnection(this, callIdentifier, specificEndpoint1, connectionIdentifier1);
				connectionIdentifier1=null;
			}
			else if (connectionIdentifier2!=null)
			{
				deleteConnection = new DeleteConnection(this, callIdentifier, specificEndpoint2,connectionIdentifier2);
				connectionIdentifier2 = null;
			}
			else
			{
				deleteConnection = new DeleteConnection(this, callIdentifier, specificEndpoint1,connectionIdentifier3);
			}
			deleteConnection.setTransactionHandle(mgwProvider.getUniqueTransactionHandler());
			InetAddress lbInetAddress = InetAddress.getByName(lbAddress);
			mgwProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection },lbInetAddress, lbMgcpInternalPort);
		} catch (Exception e) {
			e.printStackTrace();
		}

		
	}

	public void checkState() {
	}

	public void transactionEnded(int handle) {
		logger.info("transactionEnded " + handle);

	}

	public void transactionRxTimedOut(JainMgcpCommandEvent command) {
		logger.info("transactionRxTimedOut " + command);

	}

	public void transactionTxTimedOut(JainMgcpCommandEvent command) {
		logger.info("transactionTxTimedOut " + command);

	}

	public void processMgcpCommandEvent(JainMgcpCommandEvent event) {
		logger.info("Server processing MGCP Command Event " + event);
		
		switch (event.getObjectIdentifier()) {
		case Constants.CMD_CREATE_CONNECTION:
			receivedCommands.add(event);
			CreateConnectionResponse crcxResponse = null;
			CreateConnection cc = (CreateConnection) event;
			String localEndpoint = cc.getEndpointIdentifier().getLocalEndpointName();
			if(localEndpoint.contains("$"))
			{
				callIdentifier = cc.getCallIdentifier();
				notifiedEntity = cc.getNotifiedEntity();
				logger.info("Processing CRCX with wildcard  " + localEndpoint);
				specificEndpoint1 = new EndpointIdentifier(localEndpoint.replace("$",""+counterEnpoint) , cc.getEndpointIdentifier().getDomainName());
				connectionIdentifier1 = new ConnectionIdentifier(((CallIdentifier) mgwProvider.getUniqueCallIdentifier()).toString());
				crcxResponse = new CreateConnectionResponse(cc.getSource(), ReturnCode.Transaction_Executed_Normally, connectionIdentifier1);
				specificEndpoint2 = new EndpointIdentifier(cc.getSecondEndpointIdentifier().getLocalEndpointName().replace("$",""+counterEnpoint), cc.getEndpointIdentifier().getDomainName());
				
				connectionIdentifier2 = new ConnectionIdentifier(((CallIdentifier) mgwProvider.getUniqueCallIdentifier()).toString());
				crcxResponse.setSecondEndpointIdentifier(specificEndpoint2);
				crcxResponse.setSecondConnectionIdentifier(connectionIdentifier2);
				crcxResponse.setSpecificEndpointIdentifier(specificEndpoint1);
				counterEnpoint++;
			}
			else
			{
				logger.info("Processing CRCX with specific endpointID" + localEndpoint);
				connectionIdentifier3 = new ConnectionIdentifier(((CallIdentifier) mgwProvider.getUniqueCallIdentifier()).toString());
				crcxResponse = new CreateConnectionResponse(cc.getSource(), ReturnCode.Transaction_Executed_Normally, connectionIdentifier3);
				crcxResponse.setSpecificEndpointIdentifier(cc.getEndpointIdentifier());
			}
			crcxResponse.setTransactionHandle(event.getTransactionHandle());
			logger.info("Server sending response : " + crcxResponse);
			mgwProvider.sendMgcpEvents(new JainMgcpEvent[] { crcxResponse });
			break;
		case Constants.CMD_MODIFY_CONNECTION:
			receivedCommands.add(event);
			ModifyConnectionResponse mdcxResponse = new ModifyConnectionResponse(event.getSource(), ReturnCode.Transaction_Executed_Normally);
			mdcxResponse.setTransactionHandle(event.getTransactionHandle());
			logger.info("Server sending response : " + mdcxResponse);
			mgwProvider.sendMgcpEvents(new JainMgcpEvent[] { mdcxResponse });
			break;
		case Constants.CMD_NOTIFICATION_REQUEST:
			receivedCommands.add(event);
			NotificationRequestResponse rqntResponse = new NotificationRequestResponse(event.getSource(), ReturnCode.Transaction_Executed_Normally);
			requestIdentifier = ((NotificationRequest)event).getRequestIdentifier();
			rqntResponse.setTransactionHandle(event.getTransactionHandle());
			logger.info("Server sending response : " + rqntResponse);
			mgwProvider.sendMgcpEvents(new JainMgcpEvent[] { rqntResponse });
			break;
		case Constants.CMD_DELETE_CONNECTION:
			receivedCommands.add(event);
			DeleteConnectionResponse response = new DeleteConnectionResponse(event.getSource(),	ReturnCode.Transaction_Executed_Normally);
			response.setTransactionHandle(event.getTransactionHandle());
			mgwProvider.sendMgcpEvents(new JainMgcpEvent[] { response });
			if(specificEndpoint1 != null)
				specificEndpoint1 = null;
			else
				specificEndpoint2 = null;
			break;
		default:
			logger.warn("This REQUEST is unexpected " + event);
			break;
		}
	}

	public void processMgcpResponseEvent(JainMgcpResponseEvent jainmgcpresponseevent) {
		logger.info("process Mgcp Response Event " + jainmgcpresponseevent);
		receivedResponses.add(jainmgcpresponseevent);

	}

	@Override
	public void responseReceived(JsonObject json) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stopRequestReceived(MessageEvent e, JsonObject json) {
		// TODO Auto-generated method stub
		
	}

	public ArrayList<JainMgcpCommandEvent> getReceivedCommands() {
		return receivedCommands;
	}

	public ArrayList<JainMgcpResponseEvent> getReceivedResponses() {
		return receivedResponses;
	}

	public int getCommandsCount() {
		return commandsCount;
	}
	
}
