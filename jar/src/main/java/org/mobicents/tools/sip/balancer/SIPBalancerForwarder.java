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

import gov.nist.javax.sip.header.SIPHeader;

import java.text.ParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.Transaction;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * A transaction stateful UDP Forwarder that listens at a port and forwards to multiple
 * outbound addresses. It keeps a timer thread around that pings the list of
 * proxy servers and sends to the first proxy server.
 * 
 * It uses double record routing to be able to listen on one transport and sends on another transport
 * or allows support for multihoming.
 * 
 * @author M. Ranganathan
 * @author baranowb 
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A>
 */
public class SIPBalancerForwarder implements SipListener {
	private static final Logger logger = Logger.getLogger(SIPBalancerForwarder.class
			.getCanonicalName());

	/*
	 * Those parameters is to indicate to the SIP Load Balancer, from which node comes from the request
	 * so that it can stick the Call Id to this node and correctly route the subsequent requests. 
	 */
	public static final String ROUTE_PARAM_NODE_HOST = "node_host";

	public static final String ROUTE_PARAM_NODE_PORT = "node_port";
	
	public static final int UDP = 0;
	public static final int TCP = 1;

	protected static final HashSet<String> dialogCreationMethods=new HashSet<String>(2);
    
    static{
    	dialogCreationMethods.add(Request.INVITE);
    	dialogCreationMethods.add(Request.SUBSCRIBE);
    }      

	public NodeRegister register;
    
    protected String[] extraServerAddresses;
    protected int[] extraServerPorts;
	
	public SIPBalancerForwarder(Properties properties, NodeRegister register) throws IllegalStateException{
		super();
		BalancerContext.balancerContext.properties = properties;
		this.register = register;		
	}

	public void start() {

		SipFactory sipFactory = null;
		BalancerContext.balancerContext.sipStack = null;

		BalancerContext.balancerContext.host = BalancerContext.balancerContext.properties.getProperty("host");   
		BalancerContext.balancerContext.internalHost = BalancerContext.balancerContext.properties.getProperty("internalHost",BalancerContext.balancerContext.host); 
		BalancerContext.balancerContext.externalHost = BalancerContext.balancerContext.properties.getProperty("externalHost",BalancerContext.balancerContext.host); 
		BalancerContext.balancerContext.externalPort = Integer.parseInt(BalancerContext.balancerContext.properties.getProperty("externalPort"));
		if(BalancerContext.balancerContext.properties.getProperty("internalPort") != null) {
			BalancerContext.balancerContext.internalPort = Integer.parseInt(BalancerContext.balancerContext.properties.getProperty("internalPort"));
		}
		BalancerContext.balancerContext.externalIpLoadBalancerAddress = BalancerContext.balancerContext.properties.getProperty("externalIpLoadBalancerAddress");
		BalancerContext.balancerContext.internalIpLoadBalancerAddress = BalancerContext.balancerContext.properties.getProperty("internalIpLoadBalancerAddress");
		
		if(BalancerContext.balancerContext.properties.getProperty("externalLoadBalancerPort") != null) {
			BalancerContext.balancerContext.externalLoadBalancerPort = Integer.parseInt(BalancerContext.balancerContext.properties.getProperty("externalLoadBalancerPort"));
		}
		if(BalancerContext.balancerContext.properties.getProperty("internalLoadBalancerPort") != null) {
			BalancerContext.balancerContext.internalLoadBalancerPort = Integer.parseInt(BalancerContext.balancerContext.properties.getProperty("internalLoadBalancerPort"));
		}
		
		// We ended up with two duplicate set of properties for interna and external IP LB ports, just keep then for back-compatibility
		if(BalancerContext.balancerContext.properties.getProperty("externalIpLoadBalancerPort") != null) {
			BalancerContext.balancerContext.externalLoadBalancerPort = Integer.parseInt(BalancerContext.balancerContext.properties.getProperty("externalIpLoadBalancerPort"));
		}
		if(BalancerContext.balancerContext.properties.getProperty("internalIpLoadBalancerPort") != null) {
			BalancerContext.balancerContext.internalLoadBalancerPort = Integer.parseInt(BalancerContext.balancerContext.properties.getProperty("internalIpLoadBalancerPort"));
		}
		
		String extraServerNodesString = BalancerContext.balancerContext.properties.getProperty("extraServerNodes");
		if(extraServerNodesString != null) {
			extraServerAddresses = extraServerNodesString.split(",");
			extraServerPorts = new int[extraServerAddresses.length];
			for(int q=0; q<extraServerAddresses.length; q++) {
				int indexOfPort = extraServerAddresses[q].indexOf(':');
				if(indexOfPort > 0) {
					extraServerPorts[q] = Integer.parseInt(extraServerAddresses[q].substring(indexOfPort + 1, extraServerAddresses[q].length()));
					extraServerAddresses[q] = extraServerAddresses[q].substring(0, indexOfPort);
					logger.info("Extra Server: " + extraServerAddresses[q] + ":" + extraServerPorts[q]);
				} else {
					extraServerPorts[q] = 5060;
				}
			}
		}
		
        try {
            // Create SipStack object
        	sipFactory = SipFactory.getInstance();
	        sipFactory.setPathName("gov.nist");
	        BalancerContext.balancerContext.sipStack = sipFactory.createSipStack(BalancerContext.balancerContext.properties);
           
        } catch (PeerUnavailableException pue) {
            // could not find
            // gov.nist.jain.protocol.ip.sip.SipStackImpl
            // in the classpath
            throw new IllegalStateException("Cant create stack due to["+pue.getMessage()+"]", pue);
        }

        try {
        	BalancerContext.balancerContext.headerFactory = sipFactory.createHeaderFactory();
        	BalancerContext.balancerContext.addressFactory = sipFactory.createAddressFactory();
        	BalancerContext.balancerContext.messageFactory = sipFactory.createMessageFactory();

            ListeningPoint externalLp = BalancerContext.balancerContext.sipStack.createListeningPoint(BalancerContext.balancerContext.externalHost, BalancerContext.balancerContext.externalPort, "udp");
            ListeningPoint externalLpTcp = BalancerContext.balancerContext.sipStack.createListeningPoint(BalancerContext.balancerContext.externalHost, BalancerContext.balancerContext.externalPort, "tcp");
            
            BalancerContext.balancerContext.externalSipProvider = BalancerContext.balancerContext.sipStack.createSipProvider(externalLp);
            BalancerContext.balancerContext.externalSipProvider.addListeningPoint(externalLpTcp);
            BalancerContext.balancerContext.externalSipProvider.addSipListener(this);
            
            
            ListeningPoint internalLp = null;
            if(BalancerContext.balancerContext.isTwoEntrypoints()) {
            	internalLp = BalancerContext.balancerContext.sipStack.createListeningPoint(BalancerContext.balancerContext.internalHost, BalancerContext.balancerContext.internalPort, "udp");
            	ListeningPoint internalLpTcp = BalancerContext.balancerContext.sipStack.createListeningPoint(BalancerContext.balancerContext.internalHost, BalancerContext.balancerContext.internalPort, "tcp");
                BalancerContext.balancerContext.internalSipProvider = BalancerContext.balancerContext.sipStack.createSipProvider(internalLp);
                BalancerContext.balancerContext.internalSipProvider.addListeningPoint(internalLpTcp);
                BalancerContext.balancerContext.internalSipProvider.addSipListener(this);
            }


            //Creating the Record Route headers on startup since they can't be changed at runtime and this will avoid the overhead of creating them
            //for each request
            
    		//We need to use double record (better option than record route rewriting) routing otherwise it is impossible :
    		//a) to forward BYE from the callee side to the caller
    		//b) to support different transports		
            {
            	SipURI externalLocalUri = BalancerContext.balancerContext.addressFactory
            	.createSipURI(null, externalLp.getIPAddress());
            	externalLocalUri.setPort(externalLp.getPort());
            	externalLocalUri.setTransportParam("udp");
            	//See RFC 3261 19.1.1 for lr parameter
            	externalLocalUri.setLrParam();
            	Address externalLocalAddress = BalancerContext.balancerContext.addressFactory.createAddress(externalLocalUri);
            	externalLocalAddress.setURI(externalLocalUri);

            	if(logger.isLoggable(Level.FINEST)) {
            		logger.finest("adding Record Router Header :"+externalLocalAddress);
            	}                    
            	BalancerContext.balancerContext.externalRecordRouteHeader[UDP] = BalancerContext.balancerContext.headerFactory
            	.createRecordRouteHeader(externalLocalAddress);    
            }
            {
            	SipURI externalLocalUri = BalancerContext.balancerContext.addressFactory
            	.createSipURI(null, externalLp.getIPAddress());
            	externalLocalUri.setPort(externalLp.getPort());
            	externalLocalUri.setTransportParam("tcp");
            	//See RFC 3261 19.1.1 for lr parameter
            	externalLocalUri.setLrParam();
            	Address externalLocalAddress = BalancerContext.balancerContext.addressFactory.createAddress(externalLocalUri);
            	externalLocalAddress.setURI(externalLocalUri);

            	if(logger.isLoggable(Level.FINEST)) {
            		logger.finest("adding Record Router Header :"+externalLocalAddress);
            	}                    
            	BalancerContext.balancerContext.externalRecordRouteHeader[TCP] = BalancerContext.balancerContext.headerFactory
            	.createRecordRouteHeader(externalLocalAddress);    
            }

            if(BalancerContext.balancerContext.isTwoEntrypoints()) {
            	{
            		SipURI internalLocalUri = BalancerContext.balancerContext.addressFactory
            		.createSipURI(null, internalLp.getIPAddress());
            		internalLocalUri.setPort(internalLp.getPort());
            		internalLocalUri.setTransportParam("udp");
            		//See RFC 3261 19.1.1 for lr parameter
            		internalLocalUri.setLrParam();
            		Address internalLocalAddress = BalancerContext.balancerContext.addressFactory.createAddress(internalLocalUri);
            		internalLocalAddress.setURI(internalLocalUri);
            		if(logger.isLoggable(Level.FINEST)) {
            			logger.finest("adding Record Router Header :"+internalLocalAddress);
            		}                    
            		BalancerContext.balancerContext.internalRecordRouteHeader[UDP] = BalancerContext.balancerContext.headerFactory
            		.createRecordRouteHeader(internalLocalAddress);  
            	}
            	{
            		SipURI internalLocalUri = BalancerContext.balancerContext.addressFactory
            		.createSipURI(null, internalLp.getIPAddress());
            		internalLocalUri.setPort(internalLp.getPort());
            		internalLocalUri.setTransportParam("tcp");
            		//See RFC 3261 19.1.1 for lr parameter
            		internalLocalUri.setLrParam();
            		Address internalLocalAddress = BalancerContext.balancerContext.addressFactory.createAddress(internalLocalUri);
            		internalLocalAddress.setURI(internalLocalUri);
            		if(logger.isLoggable(Level.FINEST)) {
            			logger.finest("adding Record Router Header :"+internalLocalAddress);
            		}                    
            		BalancerContext.balancerContext.internalRecordRouteHeader[TCP] = BalancerContext.balancerContext.headerFactory
            		.createRecordRouteHeader(internalLocalAddress);  
            	}
    		}

    		if(BalancerContext.balancerContext.externalIpLoadBalancerAddress != null) {
    			//UDP RR
    			{
    				SipURI ipLbSipUri = BalancerContext.balancerContext.addressFactory
    				.createSipURI(null, BalancerContext.balancerContext.externalIpLoadBalancerAddress);
    				ipLbSipUri.setPort(BalancerContext.balancerContext.externalLoadBalancerPort);
    				ipLbSipUri.setTransportParam("udp");
    				ipLbSipUri.setLrParam();
    				Address ipLbAdress = BalancerContext.balancerContext.addressFactory.createAddress(ipLbSipUri);
    				ipLbAdress.setURI(ipLbSipUri);
    				BalancerContext.balancerContext.externalIpBalancerRecordRouteHeader[UDP] = BalancerContext.balancerContext.headerFactory
    				.createRecordRouteHeader(ipLbAdress);
    			}
    			//TCP RR
    			{
    				SipURI ipLbSipUri = BalancerContext.balancerContext.addressFactory
    				.createSipURI(null, BalancerContext.balancerContext.externalIpLoadBalancerAddress);
    				ipLbSipUri.setPort(BalancerContext.balancerContext.externalLoadBalancerPort);
    				ipLbSipUri.setTransportParam("tcp");
    				ipLbSipUri.setLrParam();
    				Address ipLbAdress = BalancerContext.balancerContext.addressFactory.createAddress(ipLbSipUri);
    				ipLbAdress.setURI(ipLbSipUri);
    				BalancerContext.balancerContext.externalIpBalancerRecordRouteHeader[TCP] = BalancerContext.balancerContext.headerFactory
    				.createRecordRouteHeader(ipLbAdress);
    			}
    		}
    		
    		if(BalancerContext.balancerContext.internalIpLoadBalancerAddress != null) {
    			{
    				SipURI ipLbSipUri = BalancerContext.balancerContext.addressFactory
    				.createSipURI(null, BalancerContext.balancerContext.internalIpLoadBalancerAddress);
    				ipLbSipUri.setPort(BalancerContext.balancerContext.internalLoadBalancerPort);
    				ipLbSipUri.setTransportParam("udp");
    				ipLbSipUri.setLrParam();
    				Address ipLbAdress = BalancerContext.balancerContext.addressFactory.createAddress(ipLbSipUri);
    				ipLbAdress.setURI(ipLbSipUri);
    				BalancerContext.balancerContext.internalIpBalancerRecordRouteHeader[UDP] = BalancerContext.balancerContext.headerFactory
    				.createRecordRouteHeader(ipLbAdress);
    			}
    			{
    				SipURI ipLbSipUri = BalancerContext.balancerContext.addressFactory
    				.createSipURI(null, BalancerContext.balancerContext.internalIpLoadBalancerAddress);
    				ipLbSipUri.setPort(BalancerContext.balancerContext.internalLoadBalancerPort);
    				ipLbSipUri.setTransportParam("tcp");
    				ipLbSipUri.setLrParam();
    				Address ipLbAdress = BalancerContext.balancerContext.addressFactory.createAddress(ipLbSipUri);
    				ipLbAdress.setURI(ipLbSipUri);
    				BalancerContext.balancerContext.internalIpBalancerRecordRouteHeader[TCP] = BalancerContext.balancerContext.headerFactory
    				.createRecordRouteHeader(ipLbAdress);
    			}
    		}
    		BalancerContext.balancerContext.activeExternalHeader[UDP] = BalancerContext.balancerContext.externalIpBalancerRecordRouteHeader[UDP] != null ?
    				BalancerContext.balancerContext.externalIpBalancerRecordRouteHeader[UDP] : BalancerContext.balancerContext.externalRecordRouteHeader[UDP];
    		BalancerContext.balancerContext.activeInternalHeader[UDP] = BalancerContext.balancerContext.internalIpBalancerRecordRouteHeader[UDP] != null ?
    				BalancerContext.balancerContext.internalIpBalancerRecordRouteHeader[UDP] : BalancerContext.balancerContext.internalRecordRouteHeader[UDP];
    		
    		BalancerContext.balancerContext.activeExternalHeader[TCP] = BalancerContext.balancerContext.externalIpBalancerRecordRouteHeader[TCP] != null ?
    				BalancerContext.balancerContext.externalIpBalancerRecordRouteHeader[TCP] : BalancerContext.balancerContext.externalRecordRouteHeader[TCP];
    		BalancerContext.balancerContext.activeInternalHeader[TCP] = BalancerContext.balancerContext.internalIpBalancerRecordRouteHeader[TCP] != null ?
    				BalancerContext.balancerContext.internalIpBalancerRecordRouteHeader[TCP] : BalancerContext.balancerContext.internalRecordRouteHeader[TCP];
    		
    		BalancerContext.balancerContext.sipStack.start();
        } catch (Exception ex) {
        	throw new IllegalStateException("Can't create sip objects and lps due to["+ex.getMessage()+"]", ex);
        }
        if(logger.isLoggable(Level.INFO)) {
        	logger.info("Sip Balancer started on external address " + BalancerContext.balancerContext.externalHost + ", external port : " + BalancerContext.balancerContext.externalPort + "");
        }              
	}
	
	public void stop() {
		Iterator<SipProvider> sipProviderIterator = BalancerContext.balancerContext.sipStack.getSipProviders();
		try{
			while (sipProviderIterator.hasNext()) {
				SipProvider sipProvider = sipProviderIterator.next();
				ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
				for (ListeningPoint listeningPoint : listeningPoints) {
					if(logger.isLoggable(Level.INFO)) {
						logger.info("Removing the following Listening Point " + listeningPoint);
					}
					sipProvider.removeListeningPoint(listeningPoint);
					BalancerContext.balancerContext.sipStack.deleteListeningPoint(listeningPoint);
				}
				if(logger.isLoggable(Level.INFO)) {
					logger.info("Removing the sip provider");
				}
				sipProvider.removeSipListener(this);	
				BalancerContext.balancerContext.sipStack.deleteSipProvider(sipProvider);	
				sipProviderIterator = BalancerContext.balancerContext.sipStack.getSipProviders();
			}
		} catch (Exception e) {
			throw new IllegalStateException("Cant remove the listening points or sip providers", e);
		}
		
		BalancerContext.balancerContext.sipStack.stop();
		BalancerContext.balancerContext.sipStack = null;
		if(logger.isLoggable(Level.INFO)) {
			logger.info("Sip Balancer stopped");
		}
	}
	
	public void processDialogTerminated(
			DialogTerminatedEvent dialogTerminatedEvent) {
		// We wont see those
	}

	public void processIOException(IOExceptionEvent exceptionEvent) {
		// Hopefully we wont see those either
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.SipListener#processRequest(javax.sip.RequestEvent)
	 */
	public void processRequest(RequestEvent requestEvent) {
		// This will be invoked only by external endpoint
		final SipProvider sipProvider = (SipProvider) requestEvent.getSource();
         
		final Request request = requestEvent.getRequest();
		final String requestMethod = request.getMethod();
		try {	
			updateStats(request);
            forwardRequest(sipProvider,request);          						
        } catch (Throwable throwable) {
            logger.log(Level.SEVERE, "Unexpected exception while forwarding the request " + request, throwable);
            if(!Request.ACK.equalsIgnoreCase(requestMethod)) {
	            try {
	            	Response response = BalancerContext.balancerContext.messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, request);			
	                sipProvider.sendResponse(response);	
	            } catch (Exception e) {
	            	logger.log(Level.SEVERE, "Unexpected exception while trying to send the error response for this " + request, e);
				}
            }
        }
	}

	private void updateStats(Message message) {
		if(BalancerContext.balancerContext.gatherStatistics) {
			if(message instanceof Request) {
				BalancerContext.balancerContext.requestsProcessed.incrementAndGet();
				final String method = ((Request) message).getMethod();
				final AtomicLong requestsProcessed = BalancerContext.balancerContext.requestsProcessedByMethod.get(method);
				if(requestsProcessed == null) {
					BalancerContext.balancerContext.requestsProcessedByMethod.put(method, new AtomicLong(0));
				} else {
					requestsProcessed.incrementAndGet();
				}
			} else {
				BalancerContext.balancerContext.responsesProcessed.incrementAndGet();
				final int statusCode = ((Response)message).getStatusCode();				
				int statusCodeDiv = statusCode / 100;
				switch (statusCodeDiv) {
					case 1:
						BalancerContext.balancerContext.responsesProcessedByStatusCode.get("1XX").incrementAndGet();
						break;
					case 2:
						BalancerContext.balancerContext.responsesProcessedByStatusCode.get("2XX").incrementAndGet();
						break;
					case 3:
						BalancerContext.balancerContext.responsesProcessedByStatusCode.get("3XX").incrementAndGet();
						break;
					case 4:
						BalancerContext.balancerContext.responsesProcessedByStatusCode.get("4XX").incrementAndGet();
						break;
					case 5:
						BalancerContext.balancerContext.responsesProcessedByStatusCode.get("5XX").incrementAndGet();
						break;
					case 6:
						BalancerContext.balancerContext.responsesProcessedByStatusCode.get("6XX").incrementAndGet();
						break;
					case 7:
						BalancerContext.balancerContext.responsesProcessedByStatusCode.get("7XX").incrementAndGet();
						break;
					case 8:
						BalancerContext.balancerContext.responsesProcessedByStatusCode.get("8XX").incrementAndGet();
						break;
					case 9:
						BalancerContext.balancerContext.responsesProcessedByStatusCode.get("9XX").incrementAndGet();
						break;
				}		
			}		
		}
	}
	
	private SIPNode getNode(String host, int port, String otherTransport) {
		otherTransport = otherTransport.toLowerCase();
		for(SIPNode node : BalancerContext.balancerContext.nodes) {
			if(node.getHostName().equals(host) || node.getIp().equals(host)) {
				if((Integer)node.getProperties().get(otherTransport + "Port") == port) {
					return node;
				}
			}
		}
		return null;
	}
	
	private boolean isViaHeaderFromServer(Request request) {
		ViaHeader viaHeader = ((ViaHeader)request.getHeader(ViaHeader.NAME));
		String host = viaHeader.getHost();
		String transport = viaHeader.getTransport();
		if(transport == null) transport = "udp";
		int port = viaHeader.getPort();
		if(extraServerAddresses != null) {
			for(int q=0; q<extraServerAddresses.length; q++) {
				if(extraServerAddresses[q].equals(host) && extraServerPorts[q] == port) {
					return true;
				}
			}
		}
		if(getNode(host, port, transport) != null) {
			return true;
		}
		return false;
	}
	
	private SIPNode getSourceNode(Response response) {
		ViaHeader viaHeader = ((ViaHeader)response.getHeader(ViaHeader.NAME));
		String host = viaHeader.getHost();
		String transport = viaHeader.getTransport();
		if(transport == null) transport = "udp";
		transport = transport.toLowerCase();
		int port = viaHeader.getPort();
		if(extraServerAddresses != null) {
			for(int q=0; q<extraServerAddresses.length; q++) {
				if(extraServerAddresses[q].equals(host) && extraServerPorts[q] == port) {
					return ExtraServerNode.extraServerNode;
				}
			}
		}
		SIPNode node = getNode(host, port, transport);
		if(getNode(host, port, transport) != null) {
			return node;
		}
		return null;
	}

	/**
	 * @param requestEvent
	 * @param sipProvider
	 * @param originalRequest
	 * @param serverTransaction
	 * @param request
	 * @throws ParseException
	 * @throws InvalidArgumentException
	 * @throws SipException
	 * @throws TransactionUnavailableException
	 */
	private void forwardRequest(
			SipProvider sipProvider,
			Request request)
			throws ParseException, InvalidArgumentException, SipException,
			TransactionUnavailableException {
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("got request:\n"+request);
		}
		
		boolean isRequestFromServer = false;
		if(!BalancerContext.balancerContext.isTwoEntrypoints()) {
			isRequestFromServer = isViaHeaderFromServer(request);
		} else {
			isRequestFromServer = sipProvider.equals(BalancerContext.balancerContext.internalSipProvider);
		}
		
		final boolean isCancel = Request.CANCEL.equals(request.getMethod());
		
		if(!isCancel) {
			decreaseMaxForwardsHeader(sipProvider, request);
		}
		
		if(dialogCreationMethods.contains(request.getMethod())) {
			addLBRecordRoute(sipProvider, request);
		}
		
		final String callID = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
		
		RouteHeaderHints hints = removeRouteHeadersMeantForLB(request);
		
		if(hints.serverAssignedNode !=null) {
			String callId = ((SIPHeader) request.getHeader("Call-ID")).getValue();
			BalancerContext.balancerContext.balancerAlgorithm.assignToNode(callId, hints.serverAssignedNode);
			if(logger.isLoggable(Level.FINEST)) {
	    		logger.finest("Following node information has been found in one of the route Headers " + hints.serverAssignedNode);
	    	}
		}
		
		SIPNode nextNode = null;
		String transport = ((ViaHeader)request.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
		if(isRequestFromServer) {
			BalancerContext.balancerContext.balancerAlgorithm.processInternalRequest(request);
		} else {
			SIPNode assignedNode = null;
			RouteHeader nextNodeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
			if(nextNodeHeader != null) {
				URI uri = nextNodeHeader.getAddress().getURI();
				if(uri instanceof SipURI) {
					SipURI sipUri = (SipURI) uri;
					assignedNode = getNode(sipUri.getHost(), sipUri.getPort(), transport);
					if(logger.isLoggable(Level.FINEST)) {
			    		logger.finest("Found SIP URI " + uri + " |Next node is " + assignedNode);
			    	}
				}
			}
			SipURI assignedUri = null;
			//boolean nextNodeInRequestUri = false;
			
			if(assignedNode == null) {
				if(hints.subsequentRequest) {
					RouteHeader header = (RouteHeader) request.getHeader(RouteHeader.NAME);
					if(header != null) {
						assignedUri = (SipURI) header.getAddress().getURI();
						request.removeFirst(RouteHeader.NAME);
					} else {
						SipURI sipUri =(SipURI) request.getRequestURI();
						//nextNodeInRequestUri = true;
						assignedNode = getNode(sipUri.getHost(), sipUri.getPort(), transport);
					}
					if(logger.isLoggable(Level.FINEST)) {
			    		logger.finest("Subsequent request -> Found Route Header " + header + " |Next node is " + assignedNode);
			    	}
				} else {
					SipURI sipUri =(SipURI) request.getRequestURI();
					//nextNodeInRequestUri = true;
					assignedNode = getNode(sipUri.getHost(), sipUri.getPort(), transport);
					if(logger.isLoggable(Level.FINEST)) {
			    		logger.finest("NOT Subsequent request -> using sipUri " + sipUri + " |Next node is " + assignedNode);
			    	}
				}
			}
			
			if(assignedNode == null) {
				if(logger.isLoggable(Level.FINEST)) {
		    		logger.finest("assignedNode is null");
		    	}
				nextNode = BalancerContext.balancerContext.balancerAlgorithm.processExternalRequest(request);
				if(nextNode != null) {
					if(logger.isLoggable(Level.FINEST)) {
						String nodesString = "";
						Object[] nodes = BalancerContext.balancerContext.nodes.toArray();
						for(Object n : nodes) {
							nodesString +=n + " , ";
						}
			    		logger.finest("Next node is not null. Assigned uri is " + assignedUri + "Available nodes: " + nodesString);
			    	}
					//Adding Route Header pointing to the node the sip balancer wants to forward to
					SipURI routeSipUri;
					try {

						if(assignedUri == null) { // If a next node is NOT already assigned in the dialog from previous requests
							routeSipUri = BalancerContext.balancerContext.addressFactory
							.createSipURI(null, nextNode.getIp());
						}
						else { // OTHERWISE, a node is already assigned and it's alive
							routeSipUri = assignedUri;
						}
						routeSipUri.setHost(nextNode.getIp());
						Integer port = (Integer)nextNode.getProperties().get(transport + "Port");
						if(port == null) {
							throw new RuntimeException("Port is null in the node properties for transport="
									+ transport);
						}
						routeSipUri.setPort(port);
						routeSipUri.setTransportParam(transport);
						routeSipUri.setLrParam();
						
						// Either we should put it in route header of request URI (based on what the incoming request looks like)
						//if( nextNodeInRequestUri) {
						//	request.setRequestURI(routeSipUri);
						//} else {
							final RouteHeader route = BalancerContext.balancerContext.headerFactory.createRouteHeader(
									BalancerContext.balancerContext.addressFactory.createAddress(routeSipUri));
							request.addFirst(route);
						//}
						
					} catch (Exception e) {
						throw new RuntimeException("Error adding route header", e);
					}
				}
			} else {
				nextNode = BalancerContext.balancerContext.balancerAlgorithm.processAssignedExternalRequest(request, assignedNode);
			}
			if(nextNode == null) {
				throw new RuntimeException("No nodes available");
			} else {

			}
		}
		
		// Stateless proxies must not use internal state or ransom values when creating branch because they
		// must repeat exactly the same branches for retransmissions
		final ViaHeader via = (ViaHeader) request.getHeader(ViaHeader.NAME);
		String newBranch = via.getBranch() + callID.substring(0, Math.min(callID.length(), 5));
		// Add the via header to the top of the header list.
		final ViaHeader viaHeaderExternal = BalancerContext.balancerContext.headerFactory.createViaHeader(
				BalancerContext.balancerContext.externalHost, BalancerContext.balancerContext.externalPort, transport, newBranch);
		
		ViaHeader viaHeaderInternal = null;
		if(BalancerContext.balancerContext.isTwoEntrypoints()) {
			viaHeaderInternal = BalancerContext.balancerContext.headerFactory.createViaHeader(
				BalancerContext.balancerContext.internalHost, BalancerContext.balancerContext.internalPort, transport, newBranch + "zsd");
		}

		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("ViaHeaders will be added " + viaHeaderExternal + " and " + viaHeaderInternal);
    		logger.finest("Sending the request:\n" + request + "\n on the other side");
    	}
		if(!isRequestFromServer && BalancerContext.balancerContext.isTwoEntrypoints()) {
			request.addHeader(viaHeaderExternal); 
			if(viaHeaderInternal != null) request.addHeader(viaHeaderInternal); 
			BalancerContext.balancerContext.internalSipProvider.sendRequest(request);
		} else {
			if(viaHeaderInternal != null) request.addHeader(viaHeaderInternal); 
			request.addHeader(viaHeaderExternal); 
			BalancerContext.balancerContext.externalSipProvider.sendRequest(request);
		}
	}

	/**
	 * @param sipProvider
	 * @param request
	 * @throws ParseException
	 */
	private void addLBRecordRoute(SipProvider sipProvider, Request request)
	throws ParseException {				
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("adding Record Router Header :" + BalancerContext.balancerContext.activeExternalHeader);
		}
		String transport = ((ViaHeader)request.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
		int transportIndex = transport.equalsIgnoreCase("udp")?0:1;
		
		if(BalancerContext.balancerContext.isTwoEntrypoints()) {
			if(sipProvider.equals(BalancerContext.balancerContext.externalSipProvider)) {
				if(logger.isLoggable(Level.FINEST)) {
					logger.finest("adding Record Router Header :" + BalancerContext.balancerContext.activeExternalHeader);
				}
				request.addHeader(BalancerContext.balancerContext.activeExternalHeader[transportIndex]);

				if(logger.isLoggable(Level.FINEST)) {
					logger.finest("adding Record Router Header :" + BalancerContext.balancerContext.activeInternalHeader);
				}
				request.addHeader(BalancerContext.balancerContext.activeInternalHeader[transportIndex]);
			} else {
				if(logger.isLoggable(Level.FINEST)) {
					logger.finest("adding Record Router Header :" + BalancerContext.balancerContext.activeInternalHeader);
				}
				request.addHeader(BalancerContext.balancerContext.activeInternalHeader[transportIndex]);

				if(logger.isLoggable(Level.FINEST)) {
					logger.finest("adding Record Router Header :" + BalancerContext.balancerContext.activeExternalHeader);
				}
				request.addHeader(BalancerContext.balancerContext.activeExternalHeader[transportIndex]);			
			}	
		} else {
			request.addHeader(BalancerContext.balancerContext.activeExternalHeader[transportIndex]);
		}
	}

	/**
	 * This will check if in the route header there is information on which node from the cluster send the request.
	 * If the request is not received from the cluster, this information will not be present. 
	 * @param routeHeader the route header to check
	 * @return the corresponding Sip Node
	 */
	private SIPNode checkRouteHeaderForSipNode(SipURI routeSipUri) {
		SIPNode node = null;
		String hostNode = routeSipUri.getParameter(ROUTE_PARAM_NODE_HOST);
		String hostPort = routeSipUri.getParameter(ROUTE_PARAM_NODE_PORT);
		if(hostNode != null && hostPort != null) {
			int port = Integer.parseInt(hostPort);
			node = register.getNode(hostNode, port, routeSipUri.getTransportParam());
		}
		return node;
	}

	/**
	 * Remove the different route headers that are meant for the Load balancer. 
	 * There is two cases here :
	 * <ul>
	 * <li>* Requests coming from external and going to the cluster : dialog creating requests can have route header so that they go through the LB and subsequent requests 
	 * will have route headers since the LB record routed</li>
	 * <li>* Requests coming from the cluster and going to external : dialog creating requests can have route header so that they go through the LB - those requests will define in the route header
	 * the originating node of the request so that that subsequent requests are routed to the originating node if still alive</li>
	 * </ul>
	 * 
	 * @param request
	 */
	private RouteHeaderHints removeRouteHeadersMeantForLB(Request request) {
		if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("Checking if there is any route headers meant for the LB to remove...");
    	}
		SIPNode node = null; 
		boolean subsequent = false;
		//Removing first routeHeader if it is for the sip balancer
		RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
		if(routeHeader != null) {
		    SipURI routeUri = (SipURI)routeHeader.getAddress().getURI();
		    
	        // We determine if request is subsequent if both internal and external LB RR headers are present. 
	        // If only one, this probably means that this dialog never passed through the SIP LB. SIPP however,
	        // removes the first Route header and we must check if the route header is the internal port, which
	        // the caller or the calle would know only if they have passed through the L before.
		    if(routeUri.getPort() == BalancerContext.balancerContext.internalPort && 
		    		routeUri.getHost().equals(BalancerContext.balancerContext.internalHost)) subsequent = true;
		    
		    //FIXME check against a list of host we may have too
		    if(!isRouteHeaderExternal(routeUri.getHost(), routeUri.getPort())) {
		    	if(logger.isLoggable(Level.FINEST)) {
		    		logger.finest("this route header is for the LB removing it " + routeUri);
		    	}
		    	request.removeFirst(RouteHeader.NAME);
		    	routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
		    	//since we used double record routing we may have 2 routes corresponding to us here
		        // for ACK and BYE from caller for example
		        node = checkRouteHeaderForSipNode(routeUri);
		        if(routeHeader != null) {
		            routeUri = (SipURI)routeHeader.getAddress().getURI();
		            //FIXME check against a list of host we may have too
		            if(!isRouteHeaderExternal(routeUri.getHost(), routeUri.getPort())) {
		            	if(logger.isLoggable(Level.FINEST)) {
		            		logger.finest("this route header is for the LB removing it " + routeUri);
		            	}
		            	request.removeFirst(RouteHeader.NAME);
		            	if(node == null) {
		            		node = checkRouteHeaderForSipNode(routeUri);
		            	}
		            	subsequent = true;
		            	
		            	// SIPP sometimes appends more headers and lets remove them here. There is no legitimate reason
		            	// more than two SIP LB headers to be place next to each-other, so this cleanup is SAFE!
		            	boolean moreHeaders = true;
		            	while(moreHeaders) {
		            		RouteHeader extraHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
		            		if(extraHeader != null) {
		            			SipURI u = (SipURI)extraHeader.getAddress().getURI();
		            			if(!isRouteHeaderExternal(u.getHost(), u.getPort())) {
		            				request.removeFirst(RouteHeader.NAME);
		            			} else {
		            				moreHeaders = false;
		            			}
		            		} else {
		            			moreHeaders = false;
		            		}
		            	}
		            }
		        }
		    }	                
		}

		return new RouteHeaderHints(node, subsequent);
	}
	
	/**
	 * Check if the sip uri is meant for the LB same host and same port
	 * @param sipUri sip Uri to check 
	 * @return
	 */
	private boolean isRouteHeaderExternal(String host, int port) {
		 //FIXME check against a list of host we may have too and add transport
		if((host.equalsIgnoreCase(BalancerContext.balancerContext.externalHost) || host.equalsIgnoreCase(BalancerContext.balancerContext.internalHost))
				&& (port == BalancerContext.balancerContext.externalPort || port == BalancerContext.balancerContext.internalPort)) {
			return false;
		}
		if((host.equalsIgnoreCase(BalancerContext.balancerContext.externalIpLoadBalancerAddress) && port == BalancerContext.balancerContext.externalLoadBalancerPort)) {
			return false;
		}
		if((host.equalsIgnoreCase(BalancerContext.balancerContext.internalIpLoadBalancerAddress) && port == BalancerContext.balancerContext.internalLoadBalancerPort)) {
			return false;
		}
		return true;
	}

	/**
	 * @param sipProvider
	 * @param request
	 * @throws InvalidArgumentException
	 * @throws ParseException
	 * @throws SipException
	 */
	private void decreaseMaxForwardsHeader(SipProvider sipProvider,
			Request request) throws InvalidArgumentException, ParseException,
			SipException {
		// Decreasing the Max Forward Header
		if(logger.isLoggable(Level.FINEST)) {
        	logger.finest("Decreasing  the Max Forward Header ");
        }
		MaxForwardsHeader maxForwardsHeader = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);
		if (maxForwardsHeader == null) {
			maxForwardsHeader = BalancerContext.balancerContext.headerFactory.createMaxForwardsHeader(70);
			request.addHeader(maxForwardsHeader);
		} else {
			if(maxForwardsHeader.getMaxForwards() - 1 > 0) {
				maxForwardsHeader.setMaxForwards(maxForwardsHeader.getMaxForwards() - 1);
			} else {
				//Max forward header equals to 0, thus sending too many hops response
				Response response = BalancerContext.balancerContext.messageFactory.createResponse
		        	(Response.TOO_MANY_HOPS,request);			
		        sipProvider.sendResponse(response);
			}
		}
	}

	/**
	 * @param originalRequest
	 * @param serverTransaction
	 * @throws ParseException
	 * @throws SipException
	 * @throws InvalidArgumentException
	 * @throws TransactionUnavailableException
	 */

	/*
	 * (non-Javadoc)
	 * @see javax.sip.SipListener#processResponse(javax.sip.ResponseEvent)
	 */
	public void processResponse(ResponseEvent responseEvent) {
		SipProvider sipProvider = (SipProvider) responseEvent.getSource();
		Response originalResponse = responseEvent.getResponse();
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("got response :\n" + originalResponse);
		}

		updateStats(originalResponse);

		final Response response = originalResponse;
		
		// Topmost via headers is me. As it is response to external request
		ViaHeader viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
		
		if(viaHeader!=null && !isRouteHeaderExternal(viaHeader.getHost(), viaHeader.getPort())) {
			response.removeFirst(ViaHeader.NAME);
		}
		
		viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
		
		if(viaHeader!=null && !isRouteHeaderExternal(viaHeader.getHost(), viaHeader.getPort())) {
			response.removeFirst(ViaHeader.NAME);
		}
		
		boolean fromServer = false;
		if(BalancerContext.balancerContext.isTwoEntrypoints()) {
			fromServer = sipProvider.equals(BalancerContext.balancerContext.internalSipProvider);
		} else {
			fromServer = getSourceNode(response) != null;
		}
		
		if(fromServer) {
			/*
			if("true".equals(BalancerContext.balancerContext.properties.getProperty("removeNodesOn500Response")) && response.getStatusCode() == 500) {
				// If the server is broken remove it from the list and try another one with the next retransmission
				if(!(sourceNode instanceof ExtraServerNode)) {
					if(BalancerContext.balancerContext.nodes.size()>1) {
						BalancerContext.balancerContext.nodes.remove(sourceNode);
						BalancerContext.balancerContext.balancerAlgorithm.nodeRemoved(sourceNode);
					}
				}
			}*/
			BalancerContext.balancerContext.balancerAlgorithm.processInternalResponse(response);
			try {	
				BalancerContext.balancerContext.externalSipProvider.sendResponse(response);
			} catch (Exception ex) {
				logger.log(Level.SEVERE, "Unexpected exception while forwarding the response \n" + response, ex);
			}
		} else {
			BalancerContext.balancerContext.balancerAlgorithm.processExternalResponse(response);
			try {	
				if(BalancerContext.balancerContext.isTwoEntrypoints()) {
					BalancerContext.balancerContext.internalSipProvider.sendResponse(response);
				} else {
					BalancerContext.balancerContext.externalSipProvider.sendResponse(response);
				}
			} catch (Exception ex) {
				logger.log(Level.SEVERE, "Unexpected exception while forwarding the response \n" + response, ex);
			}
		}



	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.SipListener#processTimeout(javax.sip.TimeoutEvent)
	 */
	public void processTimeout(TimeoutEvent timeoutEvent) {
		Transaction transaction = null;
		if(timeoutEvent.isServerTransaction()) {
			transaction = timeoutEvent.getServerTransaction();
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("timeout => " + transaction.getRequest().toString());
			}
		} else {
			transaction = timeoutEvent.getClientTransaction();
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("timeout => " + transaction.getRequest().toString());
			}
		}
		String callId = ((CallIdHeader)transaction.getRequest().getHeader(CallIdHeader.NAME)).getCallId();
		register.unStickSessionFromNode(callId);
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.SipListener#processTransactionTerminated(javax.sip.TransactionTerminatedEvent)
	 */
	public void processTransactionTerminated(
			TransactionTerminatedEvent transactionTerminatedEvent) {
		Transaction transaction = null;
		if(transactionTerminatedEvent.isServerTransaction()) {
			transaction = transactionTerminatedEvent.getServerTransaction();
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("timeout => " + transaction.getRequest().toString());
			}
		} else {
			transaction = transactionTerminatedEvent.getClientTransaction();
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("timeout => " + transaction.getRequest().toString());
			}
		}
		if(Request.BYE.equals(transaction.getRequest().getMethod())) {
			String callId = ((CallIdHeader)transaction.getRequest().getHeader(CallIdHeader.NAME)).getCallId();
			register.unStickSessionFromNode(callId);
		}
	}
	
	/**
	 * @return the requestsProcessed
	 */
	public long getNumberOfRequestsProcessed() {
		return BalancerContext.balancerContext.requestsProcessed.get();
	}
	
	/**
	 * @return the requestsProcessed
	 */
	public long getNumberOfResponsesProcessed() {
		return BalancerContext.balancerContext.responsesProcessed.get();
	}

	/**
	 * @return the requestsProcessed
	 */
	public long getRequestsProcessedByMethod(String method) {
		AtomicLong requestsProcessed = BalancerContext.balancerContext.requestsProcessedByMethod.get(method);
		if(requestsProcessed != null) {
			return requestsProcessed.get();
		}
		return 0;
	}
	
	public long getResponsesProcessedByStatusCode(String statusCode) {
		AtomicLong responsesProcessed = BalancerContext.balancerContext.responsesProcessedByStatusCode.get(statusCode);
		if(responsesProcessed != null) {
			return responsesProcessed.get();
		}
		return 0;
	}
	
	public Map<String, AtomicLong> getNumberOfRequestsProcessedByMethod() {
		return BalancerContext.balancerContext.requestsProcessedByMethod;
	}
	
	public Map<String, AtomicLong> getNumberOfResponsesProcessedByStatusCode() {
		return BalancerContext.balancerContext.responsesProcessedByStatusCode;
	}
	
	public BalancerContext getBalancerAlgorithmContext() {
		return BalancerContext.balancerContext;
	}

	public void setBalancerAlgorithmContext(
			BalancerContext balancerAlgorithmContext) {
		BalancerContext.balancerContext = balancerAlgorithmContext;
	}
	
	/**
	 * @param skipStatistics the skipStatistics to set
	 */
	public void setGatherStatistics(boolean skipStatistics) {
		BalancerContext.balancerContext.gatherStatistics = skipStatistics;
	}

	/**
	 * @return the skipStatistics
	 */
	public boolean isGatherStatistics() {
		return BalancerContext.balancerContext.gatherStatistics;
	}
}
