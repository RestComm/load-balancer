/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.mobicents.tools.sip.balancer;

import java.io.Serializable;
import java.util.Arrays;

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
public class SIPNode implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4959114432342926569L;
	private String hostName = null;
	private String ip = null;
	private int port = -1;
	private String[] transports = null;
	private long timeStamp = System.currentTimeMillis();

	public SIPNode(String hostName, String ip, int port, String[] transports) {
		super();
		this.hostName = hostName;
		this.ip = ip;
		this.port = port;
		this.transports = transports;		
	}

	public String getHostName() {
		return hostName;
	}

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}

	public String[] getTransports() {
		return transports;
	}
	
	public String getTransportsAsString() {
		return Arrays.toString(this.transports);
	}
	
	public long getTimeStamp() {
		return this.timeStamp;
	}

	public void updateTimerStamp() {
		this.timeStamp = System.currentTimeMillis();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((hostName == null) ? 0 : hostName.hashCode());
		result = prime * result + ((ip == null) ? 0 : ip.hashCode());
		result = prime * result + port;
		result = prime * result + Arrays.hashCode(transports);
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
		if (port != other.port)
			return false;
		if (!Arrays.equals(transports, other.transports))
			return false;
		return true;
	}

	public String toString() {

		return "SIPNode hostname[" + this.hostName + "] ip[" + this.ip
				+ "] port[" + this.port + "] transport["
				+ Arrays.toString(this.transports) + "]";
	}

}
