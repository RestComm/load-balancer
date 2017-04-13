package org.mobicents.tools.heartbeat.kube;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.mobicents.tools.heartbeat.api.HeartbeatConfig;
@XmlRootElement(name="heartbeatConfig")
public class HeartbeatConfigKube implements HeartbeatConfig{
	
	private int pullPeriod = 5000;
	private String nodeName = "defaultNode";
	private String protocolClassName = "org.mobicents.tools.heartbeat.kube.ServerControllerKube";

	public int getPullPeriod() 
	{
		return pullPeriod;
	}
	@XmlElement(name="pullPeriod")
	public void setPullPeriod(int pullPeriod) 
	{
		this.pullPeriod = pullPeriod;
	}

	public String getNodeName() 
	{
		return nodeName;
	}
	@XmlElement(name="nodeName")
	public void setNodeName(String nodeName) 
	{
		this.nodeName = nodeName;
	}
	
	public String getProtocolClassName() {
		return protocolClassName;
	}

	@XmlElement(name="protocolClassName")
	public void setProtocolClassName(String protocolClassName) {
		this.protocolClassName = protocolClassName;
	}
}
