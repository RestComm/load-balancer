/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
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
package org.mobicents.tools.heartbeat.api;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class Protocol 
{

	public final static String START = "start";
	public final static String HEARTBEAT = "heartbeat";
	public final static String SHUTDOWN = "shutdown";
	public static final String STOP = "stop";
	public static final String SWITCHOVER = "switchover";
	public final static String OK = "OK";
	public final static String NOK = "NOK";
	
	public static final String HOST_NAME = "hostName";
	public static final String IP = "ip";
	public static final String HTTP_PORT = "httpPort";
	public static final String SSL_PORT = "sslPort";
	public static final String UDP_PORT = "udpPort";
	public static final String TCP_PORT = "tcpPort";
	public static final String TLS_PORT = "tlsPort";
	public static final String WS_PORT = "wsPort";
	public static final String WSS_PORT = "wssPort";
	public static final String SCTP_PORT = "sctpPort";
	public static final String SMPP_PORT = "smppPort";
	public static final String SMPP_SSL_PORT = "smppSslPort";
	public static final String MGCP_PORT = "mgcpPort";
	public static final String VERSION = "version";
	public static final String SESSION_ID = "sessionId";
	public static final String RESTCOMM_INSTANCE_ID = "Restcomm-Instance-Id";
	public static final String HEARTBEAT_PORT = "heartbeatPort";
	public static final String GRACEFUL_SHUTDOWN ="graceful-shutdown";
	public static final String LB_LABEL ="lbs";

}
