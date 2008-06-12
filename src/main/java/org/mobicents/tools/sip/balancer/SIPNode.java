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
