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

import java.util.ArrayList;

import jain.protocol.ip.mgcp.JainMgcpCommandEvent;
import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.JainMgcpProvider;
import jain.protocol.ip.mgcp.JainMgcpResponseEvent;
import jain.protocol.ip.mgcp.message.Constants;
import jain.protocol.ip.mgcp.message.CreateConnection;
import jain.protocol.ip.mgcp.message.CreateConnectionResponse;
import jain.protocol.ip.mgcp.message.DeleteConnection;
import jain.protocol.ip.mgcp.message.DeleteConnectionResponse;
import jain.protocol.ip.mgcp.message.ModifyConnection;
import jain.protocol.ip.mgcp.message.NotificationRequest;
import jain.protocol.ip.mgcp.message.NotifyResponse;
import jain.protocol.ip.mgcp.message.RestartInProgressResponse;
import jain.protocol.ip.mgcp.message.parms.CallIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConnectionDescriptor;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConnectionMode;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;

import org.apache.log4j.Logger;
import org.restcomm.media.client.mgcp.stack.JainMgcpExtendedListener;
import org.restcomm.media.client.mgcp.stack.JainMgcpStackProviderImpl;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class CA implements JainMgcpExtendedListener {

	private static Logger logger = Logger.getLogger(CA.class);

	private JainMgcpStackProviderImpl caProvider;
	private int portLb = 2527;
	private EndpointIdentifier endpointId1 = null;
	private EndpointIdentifier endpointId2 = null;
	private CallIdentifier callId = null;
	private ConnectionIdentifier connectionId1 = null;
	private ConnectionIdentifier connectionId2 = null;
	private ConnectionIdentifier connectionId3 = null;
	private String domainName = "127.0.0.1:" + portLb;
	private NotifiedEntity notifiedEntity;
	private int responsesCounter = 0;
	private ArrayList <JainMgcpCommandEvent> receivedCommands = new ArrayList<>();
	private ArrayList <JainMgcpResponseEvent> receivedResponses = new ArrayList<>();
	private int commandsCount = 0;

	public CA(JainMgcpProvider caProvider, int portLb) {
		this.caProvider = (JainMgcpStackProviderImpl)caProvider;
		this.portLb = portLb;
		this.notifiedEntity = new NotifiedEntity("restcomm","127.0.0.1", caProvider.getJainMgcpStack().getPort());
	}

	public void sendCreateConnectionWildcard() {
		commandsCount++;
		try {
			caProvider.addJainMgcpListener(this);
			callId = caProvider.getUniqueCallIdentifier();
			EndpointIdentifier endpointID1 = new EndpointIdentifier("mobicents/brige/$", domainName);
			EndpointIdentifier endpointID2 = new EndpointIdentifier("mobicents/ivr/$", domainName);
			CreateConnection createConnection = new CreateConnection(this, callId, endpointID1, ConnectionMode.SendRecv);
			createConnection.setNotifiedEntity(notifiedEntity);
			createConnection.setSecondEndpointIdentifier(endpointID2);
			createConnection.setTransactionHandle(caProvider.getUniqueTransactionHandler());
			caProvider.sendMgcpEvents(new JainMgcpEvent[] { createConnection });
			logger.debug(" CreateConnection command sent for TxId " + createConnection.getTransactionHandle() + " and CallId " + callId);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void sendModifyConnection() {
		commandsCount++;
		try {
			ModifyConnection modifyConnection = new ModifyConnection(this, callId, endpointId2, connectionId2);
			modifyConnection.setTransactionHandle(caProvider.getUniqueTransactionHandler());
			caProvider.sendMgcpEvents(new JainMgcpEvent[] { modifyConnection });
			logger.debug(" ModifyConnection command sent for TxId " + modifyConnection.getTransactionHandle() + " and CallId " + callId);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void sendCreateConnection() {
		commandsCount++;
		try {
			CreateConnection createConnection = new CreateConnection(this, callId, endpointId1, ConnectionMode.SendRecv);
			String sdpData = "v=0\r\n" + "o=4855 13760799956958020 13760799956958020" + " IN IP4  127.0.0.1\r\n"
					+ "s=mysession session\r\n" + "p=+46 8 52018010\r\n" + "c=IN IP4  127.0.0.1\r\n" + "t=0 0\r\n"
					+ "m=audio 6022 RTP/AVP 0 4 18\r\n" + "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:4 G723/8000\r\n"
					+ "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";
			createConnection.setRemoteConnectionDescriptor(new ConnectionDescriptor(sdpData));
			createConnection.setNotifiedEntity(notifiedEntity);
			createConnection.setTransactionHandle(caProvider.getUniqueTransactionHandler());
			caProvider.sendMgcpEvents(new JainMgcpEvent[] { createConnection });
			logger.debug(" CreateConnection command sent for TxId " + createConnection.getTransactionHandle() + " and CallId " + callId);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void sendNotificationRequest() {
		commandsCount++;

		try {
			NotificationRequest notificationRequest = new NotificationRequest(this, endpointId2, caProvider.getUniqueRequestIdentifier());
			notificationRequest.setNotifiedEntity(notifiedEntity);
			notificationRequest.setTransactionHandle(caProvider.getUniqueTransactionHandler());
			caProvider.sendMgcpEvents(new JainMgcpEvent[] { notificationRequest });
			logger.debug(" NotificationRequest command sent for TxId " + notificationRequest.getTransactionHandle());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void sendDeleteConnectionWithConnection() {
		commandsCount++;

		try {
			DeleteConnection deleteConnection = null;
			if(connectionId1 != null)
			{
				deleteConnection = new DeleteConnection(this, callId, endpointId1, connectionId1);
				connectionId1 = null;
			}
			else if (connectionId2 != null)
			{
				deleteConnection = new DeleteConnection(this, callId, endpointId2, connectionId2);
				connectionId2 = null;
			}
			else
			{
				deleteConnection = new DeleteConnection(this, callId, endpointId1, connectionId3);
			}
			deleteConnection.setTransactionHandle(caProvider.getUniqueTransactionHandler());
			caProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void sendDeleteConnectionWithCall() {
		commandsCount++;
		try {
			DeleteConnection deleteConnection = null;
			if(endpointId1 != null)
			{
				deleteConnection = new DeleteConnection(this, callId, endpointId1);
				endpointId1 = null;
			}
			else
			{
				deleteConnection = new DeleteConnection(this, callId, endpointId2);
			}
			deleteConnection.setTransactionHandle(caProvider.getUniqueTransactionHandler());
			caProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendDeleteConnectionWithEndpoint(){
		commandsCount++;
		try {
			DeleteConnection deleteConnection = null;
			if(endpointId1 != null)
			{
				deleteConnection = new DeleteConnection(this, endpointId1);
				endpointId1 = null;
			}
			else
			{
				deleteConnection = new DeleteConnection(this, endpointId2);
			}
			deleteConnection.setTransactionHandle(caProvider.getUniqueTransactionHandler());
			caProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });
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

	public void processMgcpCommandEvent(JainMgcpCommandEvent jainmgcpcommandevent) {
		logger.info("processMgcpCommandEvent " + jainmgcpcommandevent);
		switch (jainmgcpcommandevent.getObjectIdentifier()) 
		{
		case Constants.CMD_NOTIFY:		
			receivedCommands.add(jainmgcpcommandevent);
			NotifyResponse response = new NotifyResponse(jainmgcpcommandevent.getSource(),	ReturnCode.Transaction_Executed_Normally);
			response.setTransactionHandle(jainmgcpcommandevent.getTransactionHandle());
			caProvider.sendMgcpEvents(new JainMgcpEvent[] { response });
			break;
		case Constants.CMD_DELETE_CONNECTION:			
			receivedCommands.add(jainmgcpcommandevent);
			DeleteConnectionResponse dlcxResponse = new DeleteConnectionResponse(jainmgcpcommandevent.getSource(),	ReturnCode.Transaction_Executed_Normally);
			dlcxResponse.setTransactionHandle(jainmgcpcommandevent.getTransactionHandle());
			caProvider.sendMgcpEvents(new JainMgcpEvent[] { dlcxResponse });
			break;
		case Constants.CMD_RESTART_IN_PROGRESS:			
			receivedCommands.add(jainmgcpcommandevent);
			RestartInProgressResponse rsipResponse = new RestartInProgressResponse(jainmgcpcommandevent.getSource(), ReturnCode.Transaction_Executed_Normally);
			rsipResponse.setTransactionHandle(jainmgcpcommandevent.getTransactionHandle());
			caProvider.sendMgcpEvents(new JainMgcpEvent[] { rsipResponse });
			break;
		default:
			logger.warn("This REQUEST is unexpected " + jainmgcpcommandevent);
			break;
		}
	}

	public void processMgcpResponseEvent(JainMgcpResponseEvent jainmgcpresponseevent) {
		logger.debug("processMgcpResponseEvent = " + jainmgcpresponseevent);
		switch (jainmgcpresponseevent.getObjectIdentifier()) 
		{
		case Constants.RESP_CREATE_CONNECTION:
			receivedResponses.add(jainmgcpresponseevent);
			responsesCounter++;
			CreateConnectionResponse crcxResponse = (CreateConnectionResponse) jainmgcpresponseevent;
			if (crcxResponse.getReturnCode().getValue() == ReturnCode.TRANSACTION_EXECUTED_NORMALLY) 
			{
				if(crcxResponse.getSpecificEndpointIdentifier()!=null)
					endpointId1 = crcxResponse.getSpecificEndpointIdentifier();
				if(crcxResponse.getSecondEndpointIdentifier()!=null)
					endpointId2 = crcxResponse.getSecondEndpointIdentifier();
				
				if(crcxResponse.getConnectionIdentifier()!=null&&responsesCounter==1)
					connectionId1 = crcxResponse.getConnectionIdentifier();
				else
					connectionId3 = crcxResponse.getConnectionIdentifier();
				if(crcxResponse.getSecondConnectionIdentifier()!=null)
					connectionId2 = crcxResponse.getSecondConnectionIdentifier();
			}
			break;
		case Constants.RESP_MODIFY_CONNECTION:
			receivedResponses.add(jainmgcpresponseevent);
			break;
		case Constants.RESP_NOTIFICATION_REQUEST:
			receivedResponses.add(jainmgcpresponseevent);
			break;
		case Constants.RESP_DELETE_CONNECTION:
			receivedResponses.add(jainmgcpresponseevent);
			break;
		default:
			logger.warn("This RESPONSE is unexpected " + jainmgcpresponseevent);
			break;

		}

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
