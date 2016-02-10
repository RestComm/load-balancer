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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.message.MessageFactory;

public class BalancerContext {
	
	/* We will store here all nodes we ever saw because we will need the addresses to determine the
	 *  direction of requests where a dead node is involved. Otherwise if a node has died its address
	 *  will be recognized as client address which is wrong. This only affects config with single port SIP LB.
	 *  If internalPort is set there is no problem because we use other means to determine the direction.
	 */
	
	public HashSet<SIPNode> allNodesEver = new HashSet<SIPNode>();
	public CopyOnWriteArrayList<SIPNode> aliveNodes = null;
	public ConcurrentHashMap<String, SIPNode> jvmRouteToSipNode;
	public String algorithmClassName;
	
	public Object parameters;
	
	SIPBalancerForwarder forwarder;

	public SipProvider externalSipProvider;
	public SipProvider internalSipProvider;

	public String host;
	public String externalHost;
	public String internalHost;
	
	public int externalPort;
	public int internalPort;
	
	public int externalSecurePort;
	public int internalSecurePort;
	
	public String externalViaHost;
	public String internalViaHost;
	
	public int externalViaPort;
	public int externalSecureViaPort;
	
	public int internalViaPort;
	public int internalSecureViaPort;
	
	public String internalIpLoadBalancerAddress;
	public int internalLoadBalancerPort;
	public int internalSecureLoadBalancerPort;
	
	public String externalIpLoadBalancerAddress;
	public int externalLoadBalancerPort;
	public int externalSecureLoadBalancerPort;
	
	public boolean useIpLoadBalancerAddressInViaHeaders;
	
	public String publicIP;
	
	public ArrayList<String> blockedList;
	
	public AddressFactory addressFactory;
	public HeaderFactory headerFactory;
	public MessageFactory messageFactory;

	public SipStack sipStack;

	public Properties properties;  
	
	public RecordRouteHeader[] externalRecordRouteHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] externalIpBalancerRecordRouteHeader = new RecordRouteHeader[5]; 
	public RecordRouteHeader[] internalRecordRouteHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] internalIpBalancerRecordRouteHeader = new RecordRouteHeader[5]; 
	public RecordRouteHeader[] activeExternalHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] activeInternalHeader = new RecordRouteHeader[5];
    
	//stats
	public boolean gatherStatistics = true;
	public AtomicLong requestsProcessed = new AtomicLong(0);
    public AtomicLong responsesProcessed = new AtomicLong(0);
    private static final String[] METHODS_SUPPORTED = 
		{"REGISTER", "INVITE", "ACK", "BYE", "CANCEL", "MESSAGE", "INFO", "SUBSCRIBE", "NOTIFY", "UPDATE", "PUBLISH", "REFER", "PRACK", "OPTIONS"};
	private static final String[] RESPONSES_PER_CLASS_OF_SC = 
		{"1XX", "2XX", "3XX", "4XX", "5XX", "6XX", "7XX", "8XX", "9XX"};
    
	final Map<String, AtomicLong> requestsProcessedByMethod = new ConcurrentHashMap<String, AtomicLong>();
	final Map<String, AtomicLong> responsesProcessedByStatusCode = new ConcurrentHashMap<String, AtomicLong>();
    
    public boolean isTwoEntrypoints() {
    	return (internalPort>0 || internalSecurePort>0)  && internalHost != null;
    }

    public BalancerContext() {
    	for (String method : METHODS_SUPPORTED) {
			requestsProcessedByMethod.put(method, new AtomicLong(0));
		}
    	for (String classOfSc : RESPONSES_PER_CLASS_OF_SC) {
			responsesProcessedByStatusCode.put(classOfSc, new AtomicLong(0));
		}
	}
}
