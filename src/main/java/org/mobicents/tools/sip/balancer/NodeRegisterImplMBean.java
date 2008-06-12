package org.mobicents.tools.sip.balancer;

import java.net.InetAddress;
import java.util.List;

public interface NodeRegisterImplMBean {
	
	/**
	 * Sets interval between runs of task that removes nodes that expired.
	 * @param value
	 */
	public void setNodeExpirationTaskInterval(long value);
	public long getNodeExpirationTaskInterval();
	
	/**
	 * Sets value which indicates when node has expired. if node.timeStamp+nodeExpiration<System.currentTimeMilis than node has expired and on next
	 * run of nodeExpirationTask will be removed.
	 * @param value
	 */
	public void setNodeExpiration(long value);
	public long getNodeExpiration();
	
	public InetAddress getAddress();
	
	public boolean startServer();
	public boolean stopServer();
	
	public List<SIPNode> getGatheredInfo();
	
}
