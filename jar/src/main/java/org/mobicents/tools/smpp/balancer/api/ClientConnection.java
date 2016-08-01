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

public interface ClientConnection {
	/**
	*Try to connect to the server
	*@return was the connection successful
	*/
	public Boolean connect();
	/**
	*Send bind request to server
	*/
	public void bind();
	/**
	*Try to reconnect to the server if connection lost
	*/
	public void rebind();
	/**
	*Analysis of the received packet from server
	*(bind_resp, submit_sm_resp, data_sm etc.)
	*@param packet PDU packet received from server
	*/
	public void packetReceived(Pdu packet);
	/**
	*Send unbind request to server
	*@param packet PDU packet
	*/
	public void sendUnbindRequest(Pdu packet);
	/**
	*Send SMPP request to server
	*@param packet PDU packet
	*/
	public void sendSmppRequest(Pdu packet);
	/**
	*Create a response with error to the server if he did not get it in time
	*@param packet PDU packet
	*/
	public void requestTimeout(Pdu packet);
	/**
	*Send SMPP response to server
	*@param packet PDU packet
	*/
	public void sendSmppResponse(Pdu packet);
	/**
	*Send unbind response to server
	*@param packet PDU packet
	*/
	public void sendUnbindResponse(Pdu packet);
	/**
	*Try to rebind if did not get enquire link response in time
	*/
	public void connectionCheckServerSide();
	/**
	*Send enquire_link to client for checking connection 
	*/
	public void generateEnquireLink();
	/**
	*Close connection to server
	*/
	public void closeChannel();
	
	void sendSmppRequest(Long sessionId, Pdu packet);
	void enquireTimeout();	

}
