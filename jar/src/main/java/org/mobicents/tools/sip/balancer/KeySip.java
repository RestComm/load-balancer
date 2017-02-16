package org.mobicents.tools.sip.balancer;

import java.io.Serializable;
import java.util.ArrayList;

import org.mobicents.tools.heartbeat.impl.Node;

public class KeySip {
	
	private String ip;
	private ArrayList <Integer> ports = new ArrayList<Integer>();
	private String [] transports = {"udp","tcp","tls","ws","wss"};
	
	public KeySip (Node node)
	{
		this.ip = node.getIp();
		for(String transport:transports)
		{
			Serializable currentPort = node.getProperties().get(transport + "Port");
			if(currentPort!=null)
				if(currentPort instanceof String)
					ports.add(Integer.parseInt((String) currentPort));
				else
					ports.add((Integer)currentPort);
		}
	}
	public KeySip (String ip, Integer port)
	{
		this.ip = ip;
		this.ports.add(port);
	}
	
	public String getIp() 
	{
		return ip;
	}
	public ArrayList<Integer> getPorts() {
		return ports;
	}

	@Override
    public int hashCode()
    { 
		return ip.hashCode();
    }
	
	@Override
	public boolean equals(Object obj) 
	{
		if (obj instanceof KeySip) 
		{
			if (ip.equals(((KeySip) obj).getIp())) 
			{
				if(((KeySip) obj).getPorts().equals(ports))
					return true;
				for (Integer port : ports) 
				{
					if (((KeySip) obj).getPorts().get(0).equals(port))
						return true;
				}
			} 
			else 
			{
				return false;
			}
			return false;
		} 
		else 
		{
			return false;
		}
		
	}
	
	public String toString() 
	{
		return "SIP key: " + ip + " : " + ports;
	}
}
