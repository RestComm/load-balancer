package org.mobicents.tools.heartbeat.rmi;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.mobicents.tools.heartbeat.api.HeartbeatConfig;
@XmlRootElement(name="heartbeatConfig")
public class HeartbeatConfigRmi implements HeartbeatConfig{
	
	private int rmiRegistryPort = 2000;
	private int rmiRemoteObjectPort = 2001;
	private String protocolClassName = "org.mobicents.tools.heartbeat.rmi.ServerControllerRmi";
	
	public int getRmiRegistryPort() 
	{
		return rmiRegistryPort;
	}
	@XmlElement(name="rmiRegistryPort")
	public void setRmiRegistryPort(int rmiRegistryPort) 
	{
		this.rmiRegistryPort = rmiRegistryPort;
	}
	public int getRmiRemoteObjectPort() 
	{
		return rmiRemoteObjectPort;
	}
	@XmlElement(name="rmiRemoteObjectPort")
	public void setRmiRemoteObjectPort(int rmiRemoteObjectPort) 
	{
		this.rmiRemoteObjectPort = rmiRemoteObjectPort;
	}

	public String getProtocolClassName() {
		return protocolClassName;
	}

	@XmlElement(name="protocolClassName")
	public void setProtocolClassName(String protocolClassName) {
		this.protocolClassName = protocolClassName;
	}
}
