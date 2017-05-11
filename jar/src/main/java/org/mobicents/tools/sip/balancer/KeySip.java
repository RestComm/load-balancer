package org.mobicents.tools.sip.balancer;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import org.mobicents.tools.heartbeat.api.Node;

public class KeySip {
	
	private String ip;
	private boolean isIpv6 = false;
	private ArrayList <Integer> ports = new ArrayList<Integer>();
	private String [] transports = {"udp","tcp","tls","ws","wss", "http", "ssl"};
	
	public KeySip (Node node,Boolean isIpv6)
	{
		ipToCommonForm(node.getIp());
		this.isIpv6 = isIpv6;
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
	public KeySip (String ip, Integer port,Boolean isIpv6)
	{
		ipToCommonForm(ip);
		this.isIpv6 = isIpv6;
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
		if(!isIpv6)
		{
			if(ip==null)
				return 0;
		
			return ip.hashCode();
    	}
		else
		{
			int hashCode = 0;
			try {
				hashCode = Inet6Address.getByName(ip).hashCode();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			return hashCode;
		}
    }
	
	@Override
	public boolean equals(Object obj) 
	{
		if (obj instanceof KeySip) 
		{
			if(!isIpv6)
			{
				if(ip==null)
					return false;
				
				if (ip.equals(((KeySip) obj).getIp()))
				{
					if(((KeySip) obj).getPorts().equals(ports))
						return true;
					for (Integer port : ports) 
					{
						if (((KeySip) obj).getPorts().get(0).equals(port))
						{
							return true;
						}
					}
					for (Integer objPort : ((KeySip) obj).getPorts()) 
					{
						if (ports.get(0).equals(objPort))
							return true;
					}
				} 
				else 
				{
					return false;
				}
			}
			else
			{
				try {
					InetAddress currAddress=Inet6Address.getByName(ip);
					InetAddress otherAddress=Inet6Address.getByName(((KeySip) obj).getIp());
					if(currAddress==null)
						return false;
					else
					{
						if (currAddress.equals(otherAddress))
						{
							if(((KeySip) obj).getPorts().equals(ports))
								return true;
							for (Integer port : ports) 
							{
								if (((KeySip) obj).getPorts().get(0).equals(port))
									return true;
							}
							for (Integer objPort : ((KeySip) obj).getPorts()) 
							{
								if (ports.get(0).equals(objPort))
									return true;
							}

						} 
						else 
						{
							return false;
						}
					}
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
			}
			return false;
		} 
		else 
		{
			return false;
		}
		
	}
	private void ipToCommonForm(String notCommonIp)
	{
		try 
		{
			InetAddress address=null;
			if(!isIpv6)
				address=Inet4Address.getByName(notCommonIp);
			else
				address=Inet6Address.getByName(notCommonIp);
			
			if(address==null)
				this.ip=null;
			else
			this.ip = address.getHostAddress();
		} catch (UnknownHostException e) {
			this.ip = null;
		}
	}
	
	public String toString()
	{
		return ip +":" + ports;
	}
}