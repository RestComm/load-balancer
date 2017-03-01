package org.mobicents.tools.sip.balancer;

import org.mobicents.tools.heartbeat.api.Node;

public class KeySmpp {
	
	private Integer smppPort;
	private String smppIp;
	
	public KeySmpp(String smppIP, Integer smppPort)
	{
		this.smppIp = smppIP;
		this.smppPort = smppPort;
	}
	public KeySmpp(Node node)
	{
		this.smppIp = node.getIp();
		this.smppPort = Integer.parseInt(node.getProperties().get("smppPort"));
	}

	public Integer getSmppPort() {
		return smppPort;
	}

	public String getSmppIp() {
		return smppIp;
	}

	@Override
    public int hashCode()
    { 
		return smppIp.hashCode()+smppPort.hashCode();
    }
	
	@Override
	public boolean equals(Object obj) 
	{
		if ((obj instanceof KeySmpp) 
				&& (this.smppIp.equals(((KeySmpp) obj).getSmppIp()))
				&&(this.smppPort.equals(((KeySmpp) obj).getSmppPort()))) 
			return true;
		else 
			return false;
	}
	
	@Override
	public String toString() 
	{
		return "SMPP key: " + smppIp + " : " + smppPort;
	}

}