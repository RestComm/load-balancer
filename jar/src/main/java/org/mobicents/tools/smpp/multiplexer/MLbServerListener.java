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

import java.util.List;

import org.mobicents.tools.smpp.balancer.impl.ServerConnectionImpl;

import com.cloudhopper.smpp.pdu.Pdu;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public interface MLbServerListener {
	/**
	*Create new client after requested bind and choose server using round-robin algorithm
	*@param sessionId session (client) id
	*@param serverConnection reference to server implementation in loadbalancer
	*@param packet PDU bind request received from client
	*/
	UserSpace bindRequested(Long sessionId, MServerConnectionImpl customer, Pdu packet);
	/**
	*Send unbind request to server
	*@param sessionId session (client) id
	*@param packet PDU unbind request received from client
	*/
//	void unbindRequested(Long sessionId, Pdu packet);
//	/**
//	*Send PDU request packet to server
//	*@param sessionId session (client) id
//	*@param packet PDU packet received from client
//	*/
//	void smppEntityRequested(Long sessionId, Pdu packet);
//	/**
//	*Send PDU response packet to server
//	*@param sessionId session (client) id
//	*@param packet PDU packet received from client
//	*/
//	void smppEntityResponseFromClient(Long sessionId, Pdu packet);
//	/**
//	 *Send enquire_link to client and server for connection check
//	 *@param sessionId session (client) id
//	 */
//	void checkConnection(Long sessionId);
//	/**
//	 *Close connection to server if connection was lost from client side
//	 *or all servers was lost and remove client and server implementation from map
//	 *@param sessionId session (client) id
//	 */
//	void closeConnection(Long sessionId);
//	/**
//	*Send unbind response from client to server
//	*@param sessionId session (client) id
//	*@param packet PDU unbind response received from client
//	*/
//	void unbindSuccesfullFromServer(Long sessionId, Pdu packet);

}
