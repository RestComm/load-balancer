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

import java.util.List;


/**
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public interface BalancerRunnerMBean {
	void start(String configurationFile);
	void stop();
	
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
	
	long getNumberOfRequestsProcessed();
	long getNumberOfResponsesProcessed();
	
	int getNumberOfGluedSessions();
	
	List<SIPNode> getNodes();
	String[] getNodeList();		
}
