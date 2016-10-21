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

package org.mobicents.tools.smpp.balancer.api;

import com.cloudhopper.smpp.pdu.Pdu;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public interface ServerConnection {
	/**
	*Analysis of the received packet from client
	*(bind, submit, unbind etc.)
	*@param packet PDU packet received from client
	*/
	public void packetReceived(Pdu packet);
	/**
	*Send bind response to client
	*@param packet PDU packet
	*/
	public void sendBindResponse(Pdu packet);
	/**
	*Send unbind response to client
	*@param packet PDU packet
	*/
	public void sendUnbindResponse(Pdu packet);
	/**
	*Send SMPP response to client
	*@param packet PDU packet
	*/
	public void sendResponse(Pdu packet);
	/**
	*Send SMPP request to client
	*@param packet PDU packet
	*/
	public void sendRequest(Pdu packet);
	/**
	*Send SMPP request to client
	*@param serverSessionID server ID andPDU packet
	*/
	public void sendRequest(Long serverSessionID,Pdu packet);
	/**
	*Create a response with error to the client if he did not get it in time
	*@param packet PDU packet
	*/
	public void requestTimeout(Pdu packet);
	/**
	*Disconnect the client if he did not send bind request after connection in time
	*@param sessionId session(client) id
	*/
	public void connectionTimeout(Long sessionId);
	/**
	*Send enquire_link to client and server for checking connection 
	*@param sessionId session(client) id
	*/
	public void enquireLinkTimerCheck();
	/**
	*Close connection if enquire response does not receive from client or server in time
	*@param sessionId session(client) id
	*/
	public void connectionCheck(Long sessionId);
	/**
	*Send unbind request to client
	*@param packet PDU packet
	*/
	public void sendUnbindRequest(Pdu packet);
	/**
	*Set state of server implementation(REBINDING or BOUND)
	*@param isReconnect choose state
	*/
	public void reconnectState(boolean b);
	/**
	*Send enquire_link to client for checking connection 
	*/
	public void generateEnquireLink();
	/**
	* Updates the time at which either a last enquire_link request was received or enquire_link response was received
	*/
	public void updateLastTimeSMPPLinkUpdated();

}
