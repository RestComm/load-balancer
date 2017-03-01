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

package org.mobicents.tools.sip.balancer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.heartbeat.api.Node;


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
	void setNodeExpirationTaskInterval(long value);
	long getNodeExpirationTaskInterval();
	
	/**
	 * Sets value which indicates when node has expired. if node.timeStamp+nodeExpiration<System.currentTimeMilis than node has expired and on next
	 * run of nodeExpirationTask will be removed.
	 * @param value
	 */
	void setNodeExpiration(long value);
	long getNodeExpiration();
	
	long getNumberOfRequestsProcessed();
	long getNumberOfResponsesProcessed();
	long getNumberOfBytesTransferred();
	
	Map<String, AtomicLong> getNumberOfRequestsProcessedByMethod();
	Map<String, AtomicLong> getNumberOfResponsesProcessedByStatusCode();
	
	long getRequestsProcessedByMethod(String method);
	long getResponsesProcessedByStatusCode(String statusCode);
	
	int getNumberOfActiveSipConnections();
	
	List<Node> getNodes();
	String[] getNodeList();
	//TODO:
//	String getProperty(String key);
//	void setProperty(String key, String value);
	LoadBalancerConfiguration getConfiguration();

	//HTTP balancer
	long getNumberOfHttpRequests();
	long getNumberOfHttpBytesToServer();
	long getNumberOfHttpBytesToClient();
	long getHttpRequestsProcessedByMethod(String method);
	long getHttpResponseProcessedByCode(String code);
	
	int getNumberOfActiveHttpConnections();
		
	//SMPP balancer
	long getNumberOfSmppRequestsToServer();
	long getNumberOfSmppRequestsToClient();
	long getNumberOfSmppBytesToServer();
	long getNumberOfSmppBytesToClient();
	long getSmppRequestsProcessedById(Integer id);
	long getSmppResponsesProcessedById(Integer id);
	
	int getNumberOfActiveSmppConnections();
	
	//hw usage
	/**
	* @return Returns the "recent cpu usage" for the Java Virtual Machine process.
	*/
	double getJvmCpuUsage();
	/**
	* @return Returns the  amount of used memory of the heap in bytes.
	*/
	long getJvmHeapSize();
}
