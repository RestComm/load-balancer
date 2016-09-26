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

import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.SipStackImpl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.sip.ListeningPoint;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.message.MessageFactory;

import org.jboss.netty.handler.codec.http.HttpMethod;

import com.cloudhopper.smpp.SmppConstants;

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
	
	public AtomicInteger numberHttpRequest = new AtomicInteger(0);
	
	public boolean terminateTLSTraffic;
	public boolean isSendTrying;
	public boolean isSend5xxResponse;
	public String isSend5xxResponseReasonHeader;
	public int isSend5xxResponseSatusCode;
	public boolean isUseWithNexmo;
	public String responseReasonNodeRemoval;
	public int responseStatusCodeNodeRemoval;
	
	public Object parameters;
	
	SIPBalancerForwarder forwarder;

	public SipProvider externalSipProvider;
	public SipProvider internalSipProvider;

	public String host;
	public String externalHost;
	public String internalHost;
	
	public int externalUdpPort;
	public int externalTcpPort;
	public int externalTlsPort;
	public int externalWsPort;
	public int externalWssPort;
	
	public int internalUdpPort;
	public int internalTcpPort;
	public int internalTlsPort;
	public int internalWsPort;
	public int internalWssPort;

	
	public String externalViaHost;
	public String internalViaHost;
	
	public int externalViaUdpPort;
	public int externalViaTcpPort;
	public int externalViaTlsPort;
	public int externalViaWsPort;
	public int externalViaWssPort;
	
	public int internalViaUdpPort;
	public int internalViaTcpPort;
	public int internalViaTlsPort;
	public int internalViaWsPort;
	public int internalViaWssPort;

	
	public String internalIpLoadBalancerAddress;
	public int internalLoadBalancerUdpPort;
	public int internalLoadBalancerTcpPort;
	public int internalLoadBalancerTlsPort;
	public int internalLoadBalancerWsPort;
	public int internalLoadBalancerWssPort;
	
	
	public String externalIpLoadBalancerAddress;
	public int externalLoadBalancerUdpPort;
	public int externalLoadBalancerTcpPort;
	public int externalLoadBalancerTlsPort;
	public int externalLoadBalancerWsPort;
	public int externalLoadBalancerWssPort;
	
	public boolean useIpLoadBalancerAddressInViaHeaders;
	public String sipHeaderAffinityKey;
	
	public String publicIP;
	
	public ArrayList<String> blockedList;
	
	public AddressFactory addressFactory;
	public HeaderFactory headerFactory;
	public MessageFactory messageFactory;

	public SipStackImpl sipStack;	

	public Properties properties;  
	
	public RecordRouteHeader[] externalRecordRouteHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] externalIpBalancerRecordRouteHeader = new RecordRouteHeader[5]; 
	public RecordRouteHeader[] internalRecordRouteHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] internalIpBalancerRecordRouteHeader = new RecordRouteHeader[5]; 
	public RecordRouteHeader[] activeExternalHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] activeInternalHeader = new RecordRouteHeader[5];
    
	//SIP balancer variables for monitoring
	public boolean gatherStatistics = true;
	public AtomicLong requestsProcessed = new AtomicLong(0);
    public AtomicLong responsesProcessed = new AtomicLong(0);
    public AtomicLong bytesTransferred = new AtomicLong(0);
    private static final String[] METHODS_SUPPORTED = 
		{"REGISTER", "INVITE", "ACK", "BYE", "CANCEL", "MESSAGE", "INFO", "SUBSCRIBE", "NOTIFY", "UPDATE", "PUBLISH", "REFER", "PRACK", "OPTIONS"};
	private static final String[] RESPONSES_PER_CLASS_OF_SC = 
		{"1XX", "2XX", "3XX", "4XX", "5XX", "6XX", "7XX", "8XX", "9XX"};
    
	final Map<String, AtomicLong> requestsProcessedByMethod = new ConcurrentHashMap<String, AtomicLong>();
	final Map<String, AtomicLong> responsesProcessedByStatusCode = new ConcurrentHashMap<String, AtomicLong>();
    
    public boolean isTwoEntrypoints() {
    	return (internalUdpPort>0 
    			|| internalTcpPort>0
    			|| internalTlsPort>0
    			|| internalWsPort>0
    			|| internalWssPort>0)  && internalHost != null;
    }

    public BalancerContext() {
    	for (String method : METHODS_SUPPORTED)
			requestsProcessedByMethod.put(method, new AtomicLong(0));

    	for (String classOfSc : RESPONSES_PER_CLASS_OF_SC)
			responsesProcessedByStatusCode.put(classOfSc, new AtomicLong(0));
    	
    	for(String method : HTTP_METHODS)
    		httpRequestsProcessedByMethod.put(method, new AtomicLong(0));
    	
    	for(String statusCode: HTTP_CODE_RESPONSE)
    		httpResponseProcessedByCode.put(statusCode, new AtomicLong(0));

    	for(Integer id : SMPP_REQUEST_IDS)
    		smppRequestsProcessedById.put(id, new AtomicLong(0));
    	
    	for(Integer id : SMPP_RESPONSE_IDS)
    		smppResponsesProcessedById.put(id, new AtomicLong(0));
	}
	//HTTP balancer variables for monitoring
    public AtomicLong httpRequests = new AtomicLong(0);
    public AtomicLong httpBytesToServer = new AtomicLong(0);
    public AtomicLong httpBytesToClient = new AtomicLong(0);
    
    public Map<String, AtomicLong> httpRequestsProcessedByMethod = new ConcurrentHashMap<String, AtomicLong>();
    public Map<String, AtomicLong> httpResponseProcessedByCode = new ConcurrentHashMap<String, AtomicLong>();
    private static final String[] HTTP_METHODS  = {
    	HttpMethod.CONNECT.getName(),
    	HttpMethod.DELETE.getName(),
    	HttpMethod.GET.getName(),
    	HttpMethod.HEAD.getName(),
    	HttpMethod.OPTIONS.getName(),
    	HttpMethod.PATCH.getName(),
    	HttpMethod.POST.getName(),
    	HttpMethod.PUT.getName(),
    	HttpMethod.TRACE.getName()
    };
    private static final String [] HTTP_CODE_RESPONSE  = 
    	{"1XX", "2XX", "3XX", "4XX", "5XX"};
    
    //SMPP balancer variables for monitoring
    public AtomicLong smppRequestsToServer = new AtomicLong(0);
    public AtomicLong smppRequestsToClient = new AtomicLong(0);
    public AtomicLong smppBytesToServer = new AtomicLong(0);
    public AtomicLong smppBytesToClient = new AtomicLong(0);
    public Map<Integer, AtomicLong> smppRequestsProcessedById = new ConcurrentHashMap<Integer, AtomicLong>();
    public Map<Integer, AtomicLong> smppResponsesProcessedById = new ConcurrentHashMap<Integer, AtomicLong>();
	private static final Integer[] SMPP_REQUEST_IDS  = 
		{
		SmppConstants.CMD_ID_BIND_RECEIVER,
		SmppConstants.CMD_ID_BIND_TRANSMITTER,
		SmppConstants.CMD_ID_QUERY_SM,
		SmppConstants.CMD_ID_SUBMIT_SM,
		SmppConstants.CMD_ID_DELIVER_SM,
		SmppConstants.CMD_ID_UNBIND,
		SmppConstants.CMD_ID_REPLACE_SM,
		SmppConstants.CMD_ID_CANCEL_SM,
		SmppConstants.CMD_ID_BIND_TRANSCEIVER,
		SmppConstants.CMD_ID_OUTBIND,
		SmppConstants.CMD_ID_ENQUIRE_LINK,
		SmppConstants.CMD_ID_SUBMIT_MULTI,
		SmppConstants.CMD_ID_DATA_SM
		};
	private static final Integer[] SMPP_RESPONSE_IDS  = 
		{
		SmppConstants.CMD_ID_GENERIC_NACK,
		SmppConstants.CMD_ID_BIND_RECEIVER_RESP,
		SmppConstants.CMD_ID_BIND_TRANSMITTER_RESP,
		SmppConstants.CMD_ID_QUERY_SM_RESP,
		SmppConstants.CMD_ID_SUBMIT_SM_RESP,
		SmppConstants.CMD_ID_DELIVER_SM_RESP,
		SmppConstants.CMD_ID_UNBIND_RESP,
		SmppConstants.CMD_ID_REPLACE_SM_RESP,
		SmppConstants.CMD_ID_CANCEL_SM_RESP,
		SmppConstants.CMD_ID_BIND_TRANSCEIVER_RESP,
		SmppConstants.CMD_ID_ENQUIRE_LINK_RESP,
		SmppConstants.CMD_ID_SUBMIT_MULTI_RESP,
		SmppConstants.CMD_ID_DATA_SM_RESP
		};
	
	public int getExternalPortByTransport(String transport)
	{
		if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
			return externalWssPort;
		
		if(transport.equalsIgnoreCase(ListeningPointExt.WS))
			return externalWsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TLS))
			return externalTlsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TCP))
			return externalTcpPort;
		
		return externalUdpPort;
	}
	
	public int getExternalViaPortByTransport(String transport)
	{
		if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
			return externalViaWssPort;
		
		if(transport.equalsIgnoreCase(ListeningPointExt.WS))
			return externalViaWsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TLS))
			return externalViaTlsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TCP))
			return externalViaTcpPort;
		
		return externalViaUdpPort;
	}
	
	public int getExternalLoadBalancerPortByTransport(String transport)
	{
		if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
			return externalLoadBalancerWssPort;
		
		if(transport.equalsIgnoreCase(ListeningPointExt.WS))
			return externalLoadBalancerWsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TLS))
			return externalLoadBalancerTlsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TCP))
			return externalLoadBalancerTcpPort;
		
		return externalLoadBalancerUdpPort;
	}
	
	public int getInternalPortByTransport(String transport)
	{
		if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
			return internalWssPort;
		
		if(transport.equalsIgnoreCase(ListeningPointExt.WS))
			return internalWsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TLS))
			return internalTlsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TCP))
			return internalTcpPort;
		
		return internalUdpPort;
	}
	
	public int getInternalViaPortByTransport(String transport)
	{
		if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
			return internalViaWssPort;
		
		if(transport.equalsIgnoreCase(ListeningPointExt.WS))
			return internalViaWsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TLS))
			return internalViaTlsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TCP))
			return internalViaTcpPort;
		
		return internalViaUdpPort;
	}
	
	public int getInternalLoadBalancerPortByTransport(String transport)
	{
		if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
			return internalLoadBalancerWssPort;
		
		if(transport.equalsIgnoreCase(ListeningPointExt.WS))
			return internalLoadBalancerWsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TLS))
			return internalLoadBalancerTlsPort;
		
		if(transport.equalsIgnoreCase(ListeningPoint.TCP))
			return internalLoadBalancerTcpPort;
		
		return internalLoadBalancerUdpPort;
	}
}