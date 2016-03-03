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

public interface LbClientListener {
	/**
	*Send bind response to client
	*@param sessionId session (client) id
	*@param packet PDU bind response received from server
	*/
	void bindSuccesfull(long sessionID, Pdu packet);
	/**
	*Send unbind response with error code to client 
	*and remove client and server implementation from map
	*if server discard bind request because of error
	*@param sessionId session (client) id
	*@param packet PDU packet received from server
	*/
	void bindFailed(long sessionID, Pdu packet);
	/**
	*Send unbind response to client
	*@param sessionId session (client) id
	*@param packet PDU unbind response received from server
	*/
	void unbindSuccesfull(long sessionID, Pdu packet);
	/**
	*Send PDU response packet to client
	*@param sessionId session (client) id
	*@param packet PDU packet received from server
	*/
	void smppEntityResponse(Long sessionId, Pdu packet);
	/**
	*Send PDU request packet to client
	*@param sessionId session (client) id
	*@param packet PDU packet received from server
	*/
	void smppEntityRequestFromServer(Long sessionId, Pdu packet);
	/**
	 *Change state of server implementation(REBINDING) and try to reconnect to another server
	 *when connection lost to server
	 *@param sessionId session (client) id
	 *@param packet bind packet received from client
	 *@param serverIndex index of server to which client was connected 
	 */
	void connectionLost(Long sessionId, Pdu packet, int serverIndex);
	/**
	 *Change state of server implementation(BOUND)
	 *@param sessionId session (client) id
	 */
	void reconnectSuccesful(Long sessionId);
	/**
	 *Signals that server connection is ok
	 *@param sessionId session (client) id
	 */
	void enquireLinkReceivedFromServer(Long sessionId);
	/**
	*Send unbind request from server to client
	*@param sessionId session (client) id
	*@param packet PDU unbind request received from server
	*/
	void unbindRequestedFromServer(Long sessionId, Pdu packet);

}
