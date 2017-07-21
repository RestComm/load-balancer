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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.sip.ListeningPoint;
import javax.sip.SipProvider;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.message.MessageFactory;

import org.jboss.netty.handler.codec.http.HttpMethod;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.heartbeat.api.Node;

import com.cloudhopper.smpp.SmppConstants;

public class BalancerContext {
	
	/* We will store here all nodes we ever saw because we will need the addresses to determine the
	 *  direction of requests where a dead node is involved. Otherwise if a node has died its address
	 *  will be recognized as client address which is wrong. This only affects config with single port SIP LB.
	 *  If internalPort is set there is no problem because we use other means to determine the direction.
	 */
	
	public HashSet<Node> allNodesEver = new HashSet<Node>();
	public CopyOnWriteArrayList<Node> aliveNodes = null;
	public ConcurrentHashMap<String, Node> jvmRouteToSipNode;
	public ConcurrentHashMap<String, KeySip> regexMap;
	public String algorithmClassName;
	public String smppToNodeAlgorithmClassName;
	public String smppToProviderAlgorithmClassName;
	public String nodeCommunicationProtocolClassName;
	
	public boolean securityRequired;
	public String login;
	public String password;
	
	public AtomicInteger numberHttpRequest = new AtomicInteger(0);
	
	public boolean terminateTLSTraffic;
	public boolean isSendTrying;
	public boolean isSend5xxResponse;
	public String isSend5xxResponseReasonHeader;
	public int isSend5xxResponseSatusCode;
	public boolean isUseWithNexmo;
	public String responsesReasonNodeRemoval;
	public List<Integer> responsesStatusCodeNodeRemoval;
	public int maxNumberResponsesWithError;
	public long maxErrorTime;
	public String matchingHostnameForRoute;
	public boolean isFilterSubdomain;
	public String internalTransport;
	public int shutdownTimeout;
	public Integer maxRequestNumberWithoutResponse;
	public Long maxResponseTime;
	public ArrayList <RoutingRule> routingRulesIpv4;
	public ArrayList <RoutingRule> routingRulesIpv6;
	
	public Object parameters;
	
	SIPBalancerForwarder forwarder;

	public SipProvider externalSipProvider;
	public SipProvider externalIpv6SipProvider;
	
	public SipProvider internalSipProvider;
	public SipProvider internalIpv6SipProvider;

	public String host;
	public String ipv6Host;
	public String externalHost;
	public String externalIpv6Host;
	public String internalHost;
	public String internalIpv6Host;
	
	public InetAddress ipv6HostAddress;
	public InetAddress externalIpv6HostAddress;
	public InetAddress internalIpv6HostAddress;
	
	public int [] externalPorts;
	public int [] externalIpv6Ports;
	public int [] externalIpLoadBalancerPorts;
	public int [] externalIpv6LoadBalancerPorts;
	
	public int [] internalPorts;
	public int [] internalIpv6Ports;
	public int [] internalIpLoadBalancerPorts;
	public int [] internalIpv6LoadBalancerPorts;

	
	public String externalViaHost;
	public String externalIpv6ViaHost;
	public String internalViaHost;
	public String internalIpv6ViaHost;
	
	public int [] externalViaPorts;
	public int [] externalIpv6ViaPorts;
	
	public int [] internalViaPorts;
	public int [] internalIpv6ViaPorts;
	

	
	public ArrayList <String> internalIpLoadBalancerAddresses;
	public ArrayList <String> internalIpv6LoadBalancerAddresses;

	public ArrayList <String> externalIpLoadBalancerAddresses;
	public ArrayList <String> externalIpv6LoadBalancerAddresses;
	
	public ArrayList <InetAddress> internalIpv6LoadBalancerAddressHosts;
	public ArrayList <InetAddress> externalIpv6LoadBalancerAddressHosts;
	
	public String sipHeaderAffinityKey;
	public Pattern sipHeaderAffinityKeyExclusionPattern;
	public String sipHeaderAffinityFallbackKey;
	
	public String publicIP;
	public String publicIPv6;
	
	public InetAddress publicIPv6Host;
	
	public ArrayList<String> blockedList;
	
	public AddressFactory addressFactory;
	public HeaderFactory headerFactory;
	public MessageFactory messageFactory;

	public SipStackImpl sipStack;	

	public LoadBalancerConfiguration lbConfig;
	
	public RecordRouteHeader[] externalRecordRouteHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] externalIpv6RecordRouteHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] externalIpBalancerRecordRouteHeader = new RecordRouteHeader[5]; 
	public RecordRouteHeader[] externalIpv6BalancerRecordRouteHeader = new RecordRouteHeader[5]; 
	public RecordRouteHeader[] internalRecordRouteHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] internalIpv6RecordRouteHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] internalIpBalancerRecordRouteHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] internalIpv6BalancerRecordRouteHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] activeExternalHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] activeInternalHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] activeExternalIpv6Header = new RecordRouteHeader[5];
	public RecordRouteHeader[] activeInternalIpv6Header = new RecordRouteHeader[5];
	public RecordRouteHeader[] activePrivateExternalHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] activePrivateInternalHeader = new RecordRouteHeader[5];
	public RecordRouteHeader[] activePrivateExternalIpv6Header = new RecordRouteHeader[5];
	public RecordRouteHeader[] activePrivateInternalIpv6Header = new RecordRouteHeader[5];
    
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
    	return (internalPorts[0]>0 
    			|| internalPorts[1]>0
    			|| internalPorts[2]>0
    			|| internalPorts[3]>0
    			|| internalPorts[4]>0)  && internalHost != null;
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
	
	public int getExternalPortByTransport(String transport, boolean isIpv6)
	{
		if(!isIpv6)
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return externalPorts[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return externalPorts[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return externalPorts[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return externalPorts[1];
		
			return externalPorts[0];
		}
		else
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return externalIpv6Ports[4];
			
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return externalIpv6Ports[3];
			
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return externalIpv6Ports[2];
			
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return externalIpv6Ports[1];
			
			return externalIpv6Ports[0];
		}
		
	}
	
	public int getExternalViaPortByTransport(String transport, boolean isIpv6)
	{
		if(!isIpv6)
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return externalViaPorts[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return externalViaPorts[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return externalViaPorts[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return externalViaPorts[1];
		
			return externalViaPorts[0];
		}
		else
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return externalIpv6ViaPorts[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return externalIpv6ViaPorts[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return externalIpv6ViaPorts[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return externalIpv6ViaPorts[1];
		
			return externalIpv6ViaPorts[0];
		}
	}
	
	public int getExternalLoadBalancerPortByTransport(String transport, boolean isIpv6)
	{
		if(!isIpv6)
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return externalIpLoadBalancerPorts[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return externalIpLoadBalancerPorts[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return externalIpLoadBalancerPorts[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return externalIpLoadBalancerPorts[1];
		
			return externalIpLoadBalancerPorts[0];
		}
		else
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return externalIpv6LoadBalancerPorts[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return externalIpv6LoadBalancerPorts[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return externalIpv6LoadBalancerPorts[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return externalIpv6LoadBalancerPorts[1];
		
			return externalIpv6LoadBalancerPorts[0];
		}
	}
	
	public int getInternalPortByTransport(String transport, boolean isIpv6)
	{
		if(!isIpv6)
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return internalPorts[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return internalPorts[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return internalPorts[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return internalPorts[1];
		
			return internalPorts[0];
		}
		else
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return internalIpv6Ports[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return internalIpv6Ports[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return internalIpv6Ports[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return internalIpv6Ports[1];
		
			return internalIpv6Ports[0];
		}
	}
	
	public int getInternalViaPortByTransport(String transport, boolean isIpv6)
	{
		if(!isIpv6)
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return internalViaPorts[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return internalViaPorts[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return internalViaPorts[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return internalViaPorts[1];
		
			return internalViaPorts[0];
		}
		else
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return internalIpv6ViaPorts[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return internalIpv6ViaPorts[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return internalIpv6ViaPorts[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return internalIpv6ViaPorts[1];
		
			return internalIpv6ViaPorts[0];
		}
	}
	
	public int getInternalLoadBalancerPortByTransport(String transport, boolean isIpv6)
	{
		if(!isIpv6)
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return internalIpLoadBalancerPorts[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return internalIpLoadBalancerPorts[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return internalIpLoadBalancerPorts[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return internalIpLoadBalancerPorts[1];
		
			return internalIpLoadBalancerPorts[0];
		}
		else
		{
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				return internalIpv6LoadBalancerPorts[4];
		
			if(transport.equalsIgnoreCase(ListeningPointExt.WS))
				return internalIpv6LoadBalancerPorts[3];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				return internalIpv6LoadBalancerPorts[2];
		
			if(transport.equalsIgnoreCase(ListeningPoint.TCP))
				return internalIpv6LoadBalancerPorts[1];
		
			return internalIpv6LoadBalancerPorts[0];
		}
	}
}