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

import java.util.Iterator;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class StartRequestPacket implements Packet{
	
	private String hostName;
	private String ip;
	private String httpPort;
	private String sslPort;
	private String udpPort;
	private String tcpPort;
	private String tlsPort;
	private String wsPort;
	private String wssPort;
	private String version;
	private String sessionId;
	private String restcommInstanceId;
	private String heartbeatPort;
	
	public StartRequestPacket(Node node)
	{
		this.hostName = node.getHostName();
		this.ip = node.getIp();
		Iterator<String> keyIterator = node.getProperties().keySet().iterator();
		while(keyIterator.hasNext()) 
		{
			String key = keyIterator.next();
			switch (key) 
			{
            	case Protocol.HTTP_PORT: httpPort = node.getProperties().get(key);
                     			 break;
            	case Protocol.SSL_PORT: sslPort = node.getProperties().get(key);
                				break;
            	case Protocol.UDP_PORT: udpPort = node.getProperties().get(key);
                				break;
            	case Protocol.TCP_PORT: tcpPort = node.getProperties().get(key);
                				break;
            	case Protocol.TLS_PORT: tlsPort = node.getProperties().get(key);
                				break;
            	case Protocol.WS_PORT: wsPort = node.getProperties().get(key);
                			   break;
            	case Protocol.WSS_PORT: wssPort = node.getProperties().get(key);
                				break;
            	case Protocol.VERSION: version = node.getProperties().get(key);
                				break;
            	case Protocol.SESSION_ID: sessionId = node.getProperties().get(key);
								break;
            	case Protocol.RESTCOMM_INSTANCE_ID: restcommInstanceId = node.getProperties().get(key);
				                break;
            	case Protocol.HEARTBEAT_PORT: heartbeatPort = node.getProperties().get(key);
								break;
			}
		}
	}
	public StartRequestPacket(SIPNode sipNode)
	{
		this.hostName = sipNode.getHostName();
		this.ip = sipNode.getIp();
		Iterator<String> keyIterator = sipNode.getProperties().keySet().iterator();
		while(keyIterator.hasNext()) 
		{
			String key = keyIterator.next();
			switch (key) 
			{
            	case Protocol.HTTP_PORT: httpPort = ((Integer)sipNode.getProperties().get(key)).toString();
                     			 break;
            	case Protocol.SSL_PORT: sslPort = ((Integer)sipNode.getProperties().get(key)).toString();
                				break;
            	case Protocol.UDP_PORT: udpPort = ((Integer)sipNode.getProperties().get(key)).toString();
                				break;
            	case Protocol.TCP_PORT: tcpPort = ((Integer)sipNode.getProperties().get(key)).toString();
                				break;
            	case Protocol.TLS_PORT: tlsPort = ((Integer)sipNode.getProperties().get(key)).toString();
                				break;
            	case Protocol.WS_PORT: wsPort = ((Integer)sipNode.getProperties().get(key)).toString();
                			   break;
            	case Protocol.WSS_PORT: wssPort = ((Integer)sipNode.getProperties().get(key)).toString();
                				break;
            	case Protocol.VERSION: version = (String) sipNode.getProperties().get(key);
                				break;
            	case Protocol.SESSION_ID: sessionId = (String)sipNode.getProperties().get(key);
								break;
            	case Protocol.RESTCOMM_INSTANCE_ID: restcommInstanceId = (String) sipNode.getProperties().get(key);
				                break;
            	case Protocol.HEARTBEAT_PORT: heartbeatPort = ((Integer)sipNode.getProperties().get(key)).toString();
								break;
			}
		}
	}

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getHttpPort() {
		return httpPort;
	}

	public void setHttpPort(String httpPort) {
		this.httpPort = httpPort;
	}

	public String getSslPort() {
		return sslPort;
	}

	public void setSslPort(String sslPort) {
		this.sslPort = sslPort;
	}

	public String getUdpPort() {
		return udpPort;
	}

	public void setUdpPort(String udpPort) {
		this.udpPort = udpPort;
	}

	public String getTcpPort() {
		return tcpPort;
	}

	public void setTcpPort(String tcpPort) {
		this.tcpPort = tcpPort;
	}

	public String getTlsPort() {
		return tlsPort;
	}

	public void setTlsPort(String tlsPort) {
		this.tlsPort = tlsPort;
	}

	public String getWsPort() {
		return wsPort;
	}

	public void setWsPort(String wsPort) {
		this.wsPort = wsPort;
	}

	public String getWssPort() {
		return wssPort;
	}

	public void setWssPort(String wssPort) {
		this.wssPort = wssPort;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getRestcommInstanceId() {
		return restcommInstanceId;
	}

	public void setRestcommInstanceId(String restcommInstanceId) {
		this.restcommInstanceId = restcommInstanceId;
	}

	public String getHeartbeatPort() {
		return heartbeatPort;
	}

	public void setHeartbeatPort(String heartbeatPort) {
		this.heartbeatPort = heartbeatPort;
	}



}
