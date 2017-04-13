package org.mobicents.tools.heartbeat.impl;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.mobicents.tools.heartbeat.api.HeartbeatConfig;
@XmlRootElement(name="heartbeatConfig")
public class HeartbeatConfigHttp implements HeartbeatConfig{

	private int heartbeatPort = 2610;
	private String protocolClassName = "org.mobicents.tools.heartbeat.impl.ServerController";

	public int getHeartbeatPort() {
		return heartbeatPort;
	}

	@XmlElement(name="heartbeatPort")
	public void setHeartbeatPort(int heartbeatPort) {
		this.heartbeatPort = heartbeatPort;
	}

	public String getProtocolClassName() {
		return protocolClassName;
	}

	@XmlElement(name="protocolClassName")
	public void setProtocolClassName(String protocolClassName) {
		this.protocolClassName = protocolClassName;
	}

	
	
}
