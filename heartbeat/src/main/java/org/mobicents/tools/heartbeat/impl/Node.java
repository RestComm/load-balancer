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
package org.mobicents.tools.heartbeat.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.mobicents.tools.heartbeat.interfaces.Protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class Node implements Comparable<Node> {

	private String hostName = null;
	private String ip = null;
	private long timeStamp = System.currentTimeMillis();
	private HashMap<String, String> properties = new HashMap<String, String>();
	private boolean gracefulShutdown;
	private boolean bad;
	private int failCounter = 0;
	private Gson gson = new Gson();
	
	public Node(){}
	public Node(String hostName, String ip) {
		super();
		this.hostName = hostName;
		this.ip = ip;
	}
	
	@SuppressWarnings("unchecked")
	public Node(JsonObject json) {
		properties = gson.fromJson(json,properties.getClass());
		this.hostName = properties.remove(Protocol.HOST_NAME);
		this.ip = properties.remove(Protocol.IP);
		//rename restcomm instance id key
		String restcommInstanceId = properties.remove("restcommInstanceId");
		if(restcommInstanceId!=null)
			properties.put(Protocol.RESTCOMM_INSTANCE_ID, restcommInstanceId);
	}
	public String getHostName() {
		return hostName;
	}

	public String getIp() {
		return ip;
	}

	public Map<String, String> getProperties() {
		return properties;
	}
	
	public long getTimeStamp() {
		return this.timeStamp;
	}

	public void updateTimerStamp() {
		this.timeStamp = System.currentTimeMillis();
	}

	public int getAndIncrementFailCounter() {
		return ++failCounter;
	}
	public void setFailCounter(int failCounter) {
		this.failCounter = failCounter;
	}

	public boolean isGracefulShutdown() {
		return gracefulShutdown;
	}
	public void setGracefulShutdown(boolean gracefulShutdown) {
		this.gracefulShutdown = gracefulShutdown;
	}
	
	public boolean isBad() {
		return bad;
	}
	public void setBad(boolean bad) {
		this.bad = bad;
	}
	
	public String getPorts()
	{
		return properties.get(Protocol.TCP_PORT) +
				properties.get(Protocol.UDP_PORT) +
				properties.get(Protocol.TLS_PORT) +
				properties.get(Protocol.WS_PORT) +
				properties.get(Protocol.WSS_PORT) +
				properties.get(Protocol.HTTP_PORT) + 
				properties.get(Protocol.SSL_PORT) + 
				properties.get(Protocol.SMPP_PORT);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((hostName == null) ? 0 : hostName.hashCode());
		result = prime * result + ((ip == null) ? 0 : ip.hashCode());
		Iterator<String> keyIterator = properties.keySet().iterator();
		while(keyIterator.hasNext()) {
			String key = keyIterator.next();
			result = prime * result + properties.get(key).hashCode();
		}
		return result;
	}

	@Override
	public boolean equals(Object obj) 
	{
		if(obj!=null && obj instanceof Node)
		{
			if(this==obj)
				return true;
			else if(ip!=null&&ip.equals(((Node)obj).getIp())&&getPorts().equals(((Node)obj).getPorts()))
				return true;
			else if(properties.get(Protocol.SESSION_ID)!=null&&properties.get(Protocol.SESSION_ID).equals(((Node)obj).getProperties().get(Protocol.SESSION_ID)))
				return true;
			else
				return false;
		}
		else return false;
	}

	public String toString() {

		String result = "Node hostname[" + this.hostName + "] ip[" + this.ip
				+ "] ";
		Iterator<String> keyIterator = properties.keySet().iterator();
		while(keyIterator.hasNext()) {
			String key = keyIterator.next();
			result += key + "[" + properties.get(key) + "] ";
		}
		return result;
	}
	
	public String toStringWithoutJvmroute() {

		String result = "Node hostname[" + this.hostName + "] ip[" + this.ip
		+ "] ";
		Iterator<String> keyIterator = properties.keySet().iterator();
		while(keyIterator.hasNext()) {
			String key = keyIterator.next();
			if(!key.equals("jvmRoute")) {
				result += key + "[" + properties.get(key) + "] ";
			}
		}
		return result;
	}

	public int compareTo(Node node) {
		return this.toStringWithoutJvmroute().compareTo(node.toStringWithoutJvmroute());
	}
	
}
