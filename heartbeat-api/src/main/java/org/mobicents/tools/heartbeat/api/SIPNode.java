/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.tools.heartbeat.api;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * Class holding information about a node such as hostname, ip address, port and
 * transports supported.<br/>
 * 
 * This might contain health status information about the node later on. <br/>
 * 
 * The node is responsible for sending this information to the sip load
 * balancer.
 * </p>
 *
 * @author M. Ranganathan
 * @author baranowb 
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A>
 * 
 */
public class SIPNode implements Serializable, Comparable<SIPNode> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4959114432342926569L;
	private String hostName = null;
	private String ip = null;
	private long timeStamp = System.currentTimeMillis();
	private HashMap<String, Serializable> properties = new HashMap<String, Serializable>();
	private int failCounter = 0;


	public SIPNode(){}
	public SIPNode(String hostName, String ip) {
		super();
		this.hostName = hostName;
		this.ip = ip;
	}
	
	public String getHostName() {
		return hostName;
	}

	public String getIp() {
		return ip;
	}

	public Map<String, Serializable> getProperties() {
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
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final SIPNode other = (SIPNode) obj;
		if (hostName == null) {
			if (other.hostName != null)
				return false;
		} else if (!hostName.equals(other.hostName))
			return false;
		if (ip == null) {
			if (other.ip != null)
				return false;
		} else if (!ip.equals(other.ip))
			return false;
		// Issue 1805 : Fixed the equals method
		// contribution by Yukinobu Imai
		Set<Map.Entry<String, Serializable>> set1 = properties.entrySet();
		Set<Map.Entry<String, Serializable>> set2 = other.getProperties().entrySet();
		if (!set1.containsAll(set2))
		 return false;
		if (!set2.containsAll(set1))
		 return false;
		return true;
	}

	public String toString() {

		String result = "SIPNode hostname[" + this.hostName + "] ip[" + this.ip
				+ "] ";
		Iterator<String> keyIterator = properties.keySet().iterator();
		while(keyIterator.hasNext()) {
			String key = keyIterator.next();
			result += key + "[" + properties.get(key) + "] ";
		}
		return result;
	}
	
	public String toStringWithoutJvmroute() {

		String result = "SIPNode hostname[" + this.hostName + "] ip[" + this.ip
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

	public int compareTo(SIPNode sipNode) {
		return this.toStringWithoutJvmroute().compareTo(sipNode.toStringWithoutJvmroute());
	}

}
