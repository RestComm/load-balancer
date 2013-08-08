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

import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPResponse;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

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
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;

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
	
	public static final String ROUTE_PARAM_NODE_VERSION = "version";
	
	public static final int UDP = 0;
	public static final int TCP = 1;
	BalancerRunner balancerRunner;

	protected static final HashSet<String> dialogCreationMethods=new HashSet<String>(2);
    
    static{
    	dialogCreationMethods.add(Request.INVITE);
    	dialogCreationMethods.add(Request.SUBSCRIBE);
    }      

	public NodeRegister register;
    
    protected String[] extraServerAddresses;
    protected int[] extraServerPorts;
	
	public SIPBalancerForwarder(Properties properties, BalancerRunner balancerRunner, NodeRegister register) throws IllegalStateException{
		super();
		this.balancerRunner = balancerRunner;
		this.balancerRunner.balancerContext.forwarder = this;
		this.balancerRunner.balancerContext.properties = properties;
		this.register = register;		
	}

	public void start() {

		SipFactory sipFactory = null;
		balancerRunner.balancerContext.sipStack = null;

		balancerRunner.balancerContext.host = balancerRunner.balancerContext.properties.getProperty("host");   
		balancerRunner.balancerContext.internalHost = balancerRunner.balancerContext.properties.getProperty("internalHost",balancerRunner.balancerContext.host); 
		balancerRunner.balancerContext.externalHost = balancerRunner.balancerContext.properties.getProperty("externalHost",balancerRunner.balancerContext.host); 
		balancerRunner.balancerContext.externalPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalPort"));
		if(balancerRunner.balancerContext.properties.getProperty("internalPort") != null) {
			balancerRunner.balancerContext.internalPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalPort"));
		}
		balancerRunner.balancerContext.externalIpLoadBalancerAddress = balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerAddress");
		balancerRunner.balancerContext.internalIpLoadBalancerAddress = balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerAddress");
		
		if(balancerRunner.balancerContext.properties.getProperty("externalLoadBalancerPort") != null) {
			balancerRunner.balancerContext.externalLoadBalancerPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalLoadBalancerPort"));
		}
		if(balancerRunner.balancerContext.properties.getProperty("internalLoadBalancerPort") != null) {
			balancerRunner.balancerContext.internalLoadBalancerPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalLoadBalancerPort"));
		}
		
		// We ended up with two duplicate set of properties for interna and external IP LB ports, just keep then for back-compatibility
		if(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerPort") != null) {
			balancerRunner.balancerContext.externalLoadBalancerPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerPort"));
		}
		if(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerPort") != null) {
			balancerRunner.balancerContext.internalLoadBalancerPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerPort"));
		}
		
		if(balancerRunner.balancerContext.isTwoEntrypoints()) {
			if(balancerRunner.balancerContext.externalLoadBalancerPort > 0) {
				if(balancerRunner.balancerContext.internalLoadBalancerPort <=0) {
					throw new RuntimeException("External IP load balancer specified, but not internal load balancer");
				}
			}
		}
		if(balancerRunner.balancerContext.externalIpLoadBalancerAddress != null) {
			if(balancerRunner.balancerContext.externalLoadBalancerPort<=0) {
				throw new RuntimeException("External load balancer address specified, but not externalLoadBalancerPort");
			}
		}
		
		if(balancerRunner.balancerContext.internalIpLoadBalancerAddress != null) {
			if(balancerRunner.balancerContext.internalLoadBalancerPort<=0) {
				throw new RuntimeException("Internal load balancer address specified, but not internalLoadBalancerPort");
			}
		}
		
		String extraServerNodesString = balancerRunner.balancerContext.properties.getProperty("extraServerNodes");
		String performanceTestingMode = balancerRunner.balancerContext.properties.getProperty("performanceTestingMode");
		if(extraServerNodesString != null) {
			ArrayList<SIPNode> extraServerNodes = new ArrayList<SIPNode>();
			extraServerAddresses = extraServerNodesString.split(",");
			extraServerPorts = new int[extraServerAddresses.length];
			for(int q=0; q<extraServerAddresses.length; q++) {
				int indexOfPort = extraServerAddresses[q].indexOf(':');
				if(indexOfPort > 0) {
					extraServerPorts[q] = Integer.parseInt(extraServerAddresses[q].substring(indexOfPort + 1, extraServerAddresses[q].length()));
					extraServerAddresses[q] = extraServerAddresses[q].substring(0, indexOfPort);
				} else {
					extraServerPorts[q] = 5060;
				}
				ExtraServerNode extraServerNode = new ExtraServerNode("ExtraServerNode"+q+"-"+extraServerAddresses[q]+":"+extraServerPorts[q], extraServerAddresses[q]);
				HashMap<String, Serializable> properties = new HashMap<String, Serializable>();
				properties.put("udpPort", extraServerPorts[q]);
				properties.put("tcpPort", extraServerPorts[q]);
				properties.put("version","0");
				extraServerNode.setProperties(properties);
				extraServerNodes.add(extraServerNode);
				logger.info("Extra Server: " + extraServerAddresses[q] + ":" + extraServerPorts[q]);
			}
			if(performanceTestingMode.equalsIgnoreCase("true")){
				register.handlePingInRegister(extraServerNodes);
				logger.info("Extra Servers registered as active nodes!");
			}
		}
		
        try {
            // Create SipStack object
        	sipFactory = SipFactory.getInstance();
	        sipFactory.setPathName("gov.nist");
	        balancerRunner.balancerContext.properties.setProperty("gov.nist.javax.sip.SIP_MESSAGE_VALVE", SIPBalancerValveProcessor.class.getName());
	        if(balancerRunner.balancerContext.properties.getProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE") == null) {
	        	balancerRunner.balancerContext.properties.setProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", "100");
	        }
		    
	        balancerRunner.balancerContext.sipStack = sipFactory.createSipStack(balancerRunner.balancerContext.properties);
           
        } catch (PeerUnavailableException pue) {
            // could not find
            // gov.nist.jain.protocol.ip.sip.SipStackImpl
            // in the classpath
            throw new IllegalStateException("Cant create stack due to["+pue.getMessage()+"]", pue);
        }

        try {
        	balancerRunner.balancerContext.headerFactory = sipFactory.createHeaderFactory();
        	boolean usePrettyEncoding = Boolean.valueOf(balancerRunner.balancerContext.properties.getProperty("usePrettyEncoding", "false"));
        	if(usePrettyEncoding) {
				((HeaderFactoryImpl)balancerRunner.balancerContext.headerFactory).setPrettyEncoding(true);
			}
        	balancerRunner.balancerContext.addressFactory = sipFactory.createAddressFactory();
        	balancerRunner.balancerContext.messageFactory = sipFactory.createMessageFactory();

            ListeningPoint externalLp = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.externalHost, balancerRunner.balancerContext.externalPort, "udp");
            ListeningPoint externalLpTcp = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.externalHost, balancerRunner.balancerContext.externalPort, "tcp");
            
            balancerRunner.balancerContext.externalSipProvider = balancerRunner.balancerContext.sipStack.createSipProvider(externalLp);
            balancerRunner.balancerContext.externalSipProvider.addListeningPoint(externalLpTcp);
            balancerRunner.balancerContext.externalSipProvider.addSipListener(this);
            
            
            ListeningPoint internalLp = null;
            if(balancerRunner.balancerContext.isTwoEntrypoints()) {
            	internalLp = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.internalHost, balancerRunner.balancerContext.internalPort, "udp");
            	ListeningPoint internalLpTcp = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.internalHost, balancerRunner.balancerContext.internalPort, "tcp");
                balancerRunner.balancerContext.internalSipProvider = balancerRunner.balancerContext.sipStack.createSipProvider(internalLp);
                balancerRunner.balancerContext.internalSipProvider.addListeningPoint(internalLpTcp);
                balancerRunner.balancerContext.internalSipProvider.addSipListener(this);
            }


            //Creating the Record Route headers on startup since they can't be changed at runtime and this will avoid the overhead of creating them
            //for each request
            
    		//We need to use double record (better option than record route rewriting) routing otherwise it is impossible :
    		//a) to forward BYE from the callee side to the caller
    		//b) to support different transports		
            {
            	SipURI externalLocalUri = balancerRunner.balancerContext.addressFactory
            	.createSipURI(null, externalLp.getIPAddress());
            	externalLocalUri.setPort(externalLp.getPort());
            	externalLocalUri.setTransportParam("udp");
            	//See RFC 3261 19.1.1 for lr parameter
            	externalLocalUri.setLrParam();
            	Address externalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(externalLocalUri);
            	externalLocalAddress.setURI(externalLocalUri);

            	if(logger.isDebugEnabled()) {
            		logger.debug("adding Record Router Header :"+externalLocalAddress);
            	}                    
            	balancerRunner.balancerContext.externalRecordRouteHeader[UDP] = balancerRunner.balancerContext.headerFactory
            	.createRecordRouteHeader(externalLocalAddress);    
            }
            {
            	SipURI externalLocalUri = balancerRunner.balancerContext.addressFactory
            	.createSipURI(null, externalLp.getIPAddress());
            	externalLocalUri.setPort(externalLp.getPort());
            	externalLocalUri.setTransportParam("tcp");
            	//See RFC 3261 19.1.1 for lr parameter
            	externalLocalUri.setLrParam();
            	Address externalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(externalLocalUri);
            	externalLocalAddress.setURI(externalLocalUri);

            	if(logger.isDebugEnabled()) {
            		logger.debug("adding Record Router Header :"+externalLocalAddress);
            	}                    
            	balancerRunner.balancerContext.externalRecordRouteHeader[TCP] = balancerRunner.balancerContext.headerFactory
            	.createRecordRouteHeader(externalLocalAddress);    
            }

            if(balancerRunner.balancerContext.isTwoEntrypoints()) {
            	{
            		SipURI internalLocalUri = balancerRunner.balancerContext.addressFactory
            		.createSipURI(null, internalLp.getIPAddress());
            		internalLocalUri.setPort(internalLp.getPort());
            		internalLocalUri.setTransportParam("udp");
            		//See RFC 3261 19.1.1 for lr parameter
            		internalLocalUri.setLrParam();
            		Address internalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(internalLocalUri);
            		internalLocalAddress.setURI(internalLocalUri);
            		if(logger.isDebugEnabled()) {
            			logger.debug("adding Record Router Header :"+internalLocalAddress);
            		}                    
            		balancerRunner.balancerContext.internalRecordRouteHeader[UDP] = balancerRunner.balancerContext.headerFactory
            		.createRecordRouteHeader(internalLocalAddress);  
            	}
            	{
            		SipURI internalLocalUri = balancerRunner.balancerContext.addressFactory
            		.createSipURI(null, internalLp.getIPAddress());
            		internalLocalUri.setPort(internalLp.getPort());
            		internalLocalUri.setTransportParam("tcp");
            		//See RFC 3261 19.1.1 for lr parameter
            		internalLocalUri.setLrParam();
            		Address internalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(internalLocalUri);
            		internalLocalAddress.setURI(internalLocalUri);
            		if(logger.isDebugEnabled()) {
            			logger.debug("adding Record Router Header :"+internalLocalAddress);
            		}                    
            		balancerRunner.balancerContext.internalRecordRouteHeader[TCP] = balancerRunner.balancerContext.headerFactory
            		.createRecordRouteHeader(internalLocalAddress);  
            	}
    		}

    		if(balancerRunner.balancerContext.externalIpLoadBalancerAddress != null) {
    			//UDP RR
    			{
    				SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
    				.createSipURI(null, balancerRunner.balancerContext.externalIpLoadBalancerAddress);
    				ipLbSipUri.setPort(balancerRunner.balancerContext.externalLoadBalancerPort);
    				ipLbSipUri.setTransportParam("udp");
    				ipLbSipUri.setLrParam();
    				Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
    				ipLbAdress.setURI(ipLbSipUri);
    				balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[UDP] = balancerRunner.balancerContext.headerFactory
    				.createRecordRouteHeader(ipLbAdress);
    			}
    			//TCP RR
    			{
    				SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
    				.createSipURI(null, balancerRunner.balancerContext.externalIpLoadBalancerAddress);
    				ipLbSipUri.setPort(balancerRunner.balancerContext.externalLoadBalancerPort);
    				ipLbSipUri.setTransportParam("tcp");
    				ipLbSipUri.setLrParam();
    				Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
    				ipLbAdress.setURI(ipLbSipUri);
    				balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[TCP] = balancerRunner.balancerContext.headerFactory
    				.createRecordRouteHeader(ipLbAdress);
    			}
    		}
    		
    		if(balancerRunner.balancerContext.internalIpLoadBalancerAddress != null) {
    			{
    				SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
    				.createSipURI(null, balancerRunner.balancerContext.internalIpLoadBalancerAddress);
    				ipLbSipUri.setPort(balancerRunner.balancerContext.internalLoadBalancerPort);
    				ipLbSipUri.setTransportParam("udp");
    				ipLbSipUri.setLrParam();
    				Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
    				ipLbAdress.setURI(ipLbSipUri);
    				balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[UDP] = balancerRunner.balancerContext.headerFactory
    				.createRecordRouteHeader(ipLbAdress);
    			}
    			{
    				SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
    				.createSipURI(null, balancerRunner.balancerContext.internalIpLoadBalancerAddress);
    				ipLbSipUri.setPort(balancerRunner.balancerContext.internalLoadBalancerPort);
    				ipLbSipUri.setTransportParam("tcp");
    				ipLbSipUri.setLrParam();
    				Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
    				ipLbAdress.setURI(ipLbSipUri);
    				balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[TCP] = balancerRunner.balancerContext.headerFactory
    				.createRecordRouteHeader(ipLbAdress);
    			}
    		}
    		balancerRunner.balancerContext.activeExternalHeader[UDP] = balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[UDP] != null ?
    				balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[UDP] : balancerRunner.balancerContext.externalRecordRouteHeader[UDP];
    		balancerRunner.balancerContext.activeInternalHeader[UDP] = balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[UDP] != null ?
    				balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[UDP] : balancerRunner.balancerContext.internalRecordRouteHeader[UDP];
    		
    		balancerRunner.balancerContext.activeExternalHeader[TCP] = balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[TCP] != null ?
    				balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[TCP] : balancerRunner.balancerContext.externalRecordRouteHeader[TCP];
    		balancerRunner.balancerContext.activeInternalHeader[TCP] = balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[TCP] != null ?
    				balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[TCP] : balancerRunner.balancerContext.internalRecordRouteHeader[TCP];
    		
    		balancerRunner.balancerContext.useIpLoadBalancerAddressInViaHeaders = Boolean.valueOf(
    				balancerRunner.balancerContext.properties.getProperty("useIpLoadBalancerAddressInViaHeaders", "false"));
    		if(balancerRunner.balancerContext.useIpLoadBalancerAddressInViaHeaders) {
    					balancerRunner.balancerContext.externalViaHost = balancerRunner.balancerContext.externalIpLoadBalancerAddress;
    					balancerRunner.balancerContext.internalViaHost = balancerRunner.balancerContext.internalIpLoadBalancerAddress;
    					balancerRunner.balancerContext.externalViaPort = balancerRunner.balancerContext.externalLoadBalancerPort;
    					balancerRunner.balancerContext.internalViaPort = balancerRunner.balancerContext.internalLoadBalancerPort;
    		} else {
    					balancerRunner.balancerContext.externalViaHost = balancerRunner.balancerContext.externalHost;
    					balancerRunner.balancerContext.internalViaHost = balancerRunner.balancerContext.internalHost;
    					balancerRunner.balancerContext.externalViaPort = balancerRunner.balancerContext.externalPort;
    					balancerRunner.balancerContext.internalViaPort = balancerRunner.balancerContext.internalPort;
    		}
    		balancerRunner.balancerContext.sipStack.start();
    		SipStackImpl stackImpl = (SipStackImpl) balancerRunner.balancerContext.sipStack;
    		SIPBalancerValveProcessor valve = (SIPBalancerValveProcessor) stackImpl.sipMessageValve;
    		valve.balancerRunner = balancerRunner;
        } catch (Exception ex) {
        	throw new IllegalStateException("Can't create sip objects and lps due to["+ex.getMessage()+"]", ex);
        }
        if(logger.isInfoEnabled()) {
        	logger.info("Sip Balancer started on external address " + 
        			balancerRunner.balancerContext.externalHost + ", external port : " + 
        			balancerRunner.balancerContext.externalPort + ", internalPort : " + 
        			balancerRunner.balancerContext.internalPort);
        }              
	}
	
	public void stop() {
		if(balancerRunner.balancerContext.sipStack == null) return;// already stopped
		Iterator<SipProvider> sipProviderIterator = balancerRunner.balancerContext.sipStack.getSipProviders();
		try{
			while (sipProviderIterator.hasNext()) {
				SipProvider sipProvider = sipProviderIterator.next();
				ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
				for (ListeningPoint listeningPoint : listeningPoints) {
					if(logger.isInfoEnabled()) {
						logger.info("Removing the following Listening Point " + listeningPoint);
					}
					try {
						sipProvider.removeListeningPoint(listeningPoint);
						balancerRunner.balancerContext.sipStack.deleteListeningPoint(listeningPoint);
					} catch (Exception e) {
						logger.error("Cant remove the listening points or sip providers", e);
					}
				}
				if(logger.isInfoEnabled()) {
					logger.info("Removing the sip provider");
				}
				sipProvider.removeSipListener(this);
				balancerRunner.balancerContext.sipStack.deleteSipProvider(sipProvider);	
				sipProviderIterator = balancerRunner.balancerContext.sipStack.getSipProviders();
			}
			balancerRunner.balancerContext.sipStack.stop();
			balancerRunner.balancerContext.sipStack = null;
			System.gc();
			if(logger.isInfoEnabled()) {
				logger.info("Sip forwarder SIP stack stopped");
			}
		} catch (Exception e) {
			throw new IllegalStateException("Cant remove the listening points or sip providers", e);
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
		
		if((requestMethod.equals(Request.OPTIONS) ||
				requestMethod.equals(Request.INFO)) &&
				request.getHeader("Mobicents-Heartbeat") != null &&
				sipProvider == balancerRunner.balancerContext.internalSipProvider) {
			byte[] bytes = (byte[]) request.getContent();
			Properties prop = new Properties();
			try {
				prop.load(new ByteArrayInputStream(bytes, 0, bytes.length));
				SIPNode node = new SIPNode(prop.getProperty("hostname"), prop.getProperty("ip"));
				for(String id : prop.stringPropertyNames()) {
					node.getProperties().put(id, prop.getProperty(id));
				}
				ArrayList<SIPNode> list = new ArrayList<SIPNode>();
				list.add(node);
				this.register.handlePingInRegister(list);
				Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.OK, request);			
                sipProvider.sendResponse(response);	
				return;
			} catch (Exception e) {
				logger.error("Failure parsing heartbeat properties from this request " + request, e);
			}
		}
		try {	
			updateStats(request);
			forwardRequest(sipProvider,request);          						
		} catch (Throwable throwable) {
            logger.error("Unexpected exception while forwarding the request " + request, throwable);
            if(!Request.ACK.equalsIgnoreCase(requestMethod)) {
	            try {
	            	Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, request);			
	                sipProvider.sendResponse(response);	
	            } catch (Exception e) {
	            	logger.error("Unexpected exception while trying to send the error response for this " + request, e);
				}
            }
        }
	}

	private void updateStats(Message message) {
		if(balancerRunner.balancerContext.gatherStatistics) {
			if(message instanceof Request) {
				balancerRunner.balancerContext.requestsProcessed.incrementAndGet();
				final String method = ((Request) message).getMethod();
				final AtomicLong requestsProcessed = balancerRunner.balancerContext.requestsProcessedByMethod.get(method);
				if(requestsProcessed == null) {
					balancerRunner.balancerContext.requestsProcessedByMethod.put(method, new AtomicLong(0));
				} else {
					requestsProcessed.incrementAndGet();
				}
			} else {
				balancerRunner.balancerContext.responsesProcessed.incrementAndGet();
				final int statusCode = ((Response)message).getStatusCode();				
				int statusCodeDiv = statusCode / 100;
				switch (statusCodeDiv) {
					case 1:
						balancerRunner.balancerContext.responsesProcessedByStatusCode.get("1XX").incrementAndGet();
						break;
					case 2:
						balancerRunner.balancerContext.responsesProcessedByStatusCode.get("2XX").incrementAndGet();
						break;
					case 3:
						balancerRunner.balancerContext.responsesProcessedByStatusCode.get("3XX").incrementAndGet();
						break;
					case 4:
						balancerRunner.balancerContext.responsesProcessedByStatusCode.get("4XX").incrementAndGet();
						break;
					case 5:
						balancerRunner.balancerContext.responsesProcessedByStatusCode.get("5XX").incrementAndGet();
						break;
					case 6:
						balancerRunner.balancerContext.responsesProcessedByStatusCode.get("6XX").incrementAndGet();
						break;
					case 7:
						balancerRunner.balancerContext.responsesProcessedByStatusCode.get("7XX").incrementAndGet();
						break;
					case 8:
						balancerRunner.balancerContext.responsesProcessedByStatusCode.get("8XX").incrementAndGet();
						break;
					case 9:
						balancerRunner.balancerContext.responsesProcessedByStatusCode.get("9XX").incrementAndGet();
						break;
				}		
			}		
		}
	}
	
	private SIPNode getAliveNode(String host, int port, String otherTransport, InvocationContext ctx) {
		return getNodeFromCollection(host, port, otherTransport, ctx.nodes);
	}
	
	private SIPNode getAliveNodeAnyVersion(String host, int port, String otherTransport) {
		return getNodeFromCollection(host, port, otherTransport, balancerRunner.balancerContext.aliveNodes);
	}
	
	private SIPNode getNodeFromCollection(String host, int port, String otherTransport, Collection<SIPNode> ctx) {
		otherTransport = otherTransport.toLowerCase();
		for(SIPNode node : ctx) {
			if(host.equals(node.getHostName()) || host.equals(node.getIp())) {
				if((Integer)node.getProperties().get(otherTransport + "Port") == port) {
					return node;
				}
			}
		}
		return null;
	}
	
	private SIPNode getNodeDeadOrAlive(String host, int port, String otherTransport) {
		return getNodeFromCollection(host, port, otherTransport, balancerRunner.balancerContext.allNodesEver);
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
		if(getAliveNodeAnyVersion(host, port, transport) != null) {
			return true;
		}
		return false;
	}
	
	private SIPNode getTransactionSourceNode(Response response) {
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
		SIPNode node = getNodeDeadOrAlive(host, port, transport);
		if(node != null) {
			return node;
		}
		return null;
	}
	
	private SIPNode getSenderNode(Response response) {
		SIPResponse resp = (SIPResponse) response;
		String host = resp.getRemoteAddress().getHostAddress();
		String transport = "udp";
		int port = resp.getRemotePort();
		if(extraServerAddresses != null) {
			for(int q=0; q<extraServerAddresses.length; q++) {
				if(extraServerAddresses[q].equals(host) && extraServerPorts[q] == port) {
					return ExtraServerNode.extraServerNode;
				}
			}
		}
		SIPNode node = getNodeDeadOrAlive(host, port, transport);
		if(node != null) {
			return node;
		}
		return null;
	}
	
	public SipURI getLoopbackUri(Request request) {
		SipURI uri = null;
		
		RouteHeader route = (RouteHeader) request.getHeader(RouteHeader.NAME);
		if(route != null) {
			if(route.getAddress().getURI().isSipURI()) {
				uri = (SipURI) route.getAddress().getURI();
			}
		} else {
			if(request.getRequestURI().isSipURI()) {
				uri = (SipURI) request.getRequestURI();
			}
		}
		if(uri != null) {
			if( (uri.getHost().equals(balancerRunner.balancerContext.externalHost) && 
					uri.getPort() == balancerRunner.balancerContext.externalPort)
					
			|| (uri.getHost().equals(balancerRunner.balancerContext.internalHost) && 
					uri.getPort() == balancerRunner.balancerContext.internalPort)) {
				return uri;
			}
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
		if(logger.isDebugEnabled()) {
			logger.debug("got request:\n"+request);
		}
		
		boolean isRequestFromServer = false;
		if(!balancerRunner.balancerContext.isTwoEntrypoints()) {
			isRequestFromServer = isViaHeaderFromServer(request);
		} else {
			isRequestFromServer = sipProvider.equals(balancerRunner.balancerContext.internalSipProvider);
		}
		
		final boolean isCancel = Request.CANCEL.equals(request.getMethod());
		
		if(!isCancel) {
			decreaseMaxForwardsHeader(sipProvider, request);
		}
		
		RouteHeaderHints hints = removeRouteHeadersMeantForLB(request);
		
		String version = hints.version;
		
		if(version == null) {
			version = register.getLatestVersion();
			hints.version = version;
		}
		
		InvocationContext ctx = balancerRunner.getInvocationContext(version);
		
		final String callID = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
		
		String transport = ((ViaHeader)request.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
		
		if(hints.serverAssignedNode !=null) {
			String callId = ((SIPHeader) request.getHeader("Call-ID")).getValue();
			ctx.balancerAlgorithm.assignToNode(callId, hints.serverAssignedNode);
			if(logger.isDebugEnabled()) {
	    		logger.debug("Following node information has been found in one of the route Headers " + hints.serverAssignedNode);
			}

			SipURI uri = getLoopbackUri(request);
			if(uri != null) {
				uri.setHost(hints.serverAssignedNode.getIp());
				uri.setPort((Integer) hints.serverAssignedNode.getProperties().get(transport + "Port"));
			}
		}
		
		SIPNode nextNode = null;
		
		if(isRequestFromServer) {
			ctx.balancerAlgorithm.processInternalRequest(request);
			nextNode = hints.serverAssignedNode;
		} else {
			
			// Request is NOT from app server, first check if we have hints in Route headers
			SIPNode assignedNode = hints.serverAssignedNode;
			
			// If there are no hints see if there is route header pointing existing node
			if(assignedNode == null) {
				RouteHeader nextNodeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
				if(nextNodeHeader != null) {
					URI uri = nextNodeHeader.getAddress().getURI();
					if(uri instanceof SipURI) {
						SipURI sipUri = (SipURI) uri;
						assignedNode = getAliveNode(sipUri.getHost(), sipUri.getPort(), transport, ctx);
						if(logger.isDebugEnabled()) {
							logger.debug("Found SIP URI " + uri + " |Next node is " + assignedNode);
						}
					}
				}
			}
			SipURI assignedUri = null;
			//boolean nextNodeInRequestUri = false;
			SipURI originalRouteHeaderUri = null;
			if(assignedNode == null) {
				if(hints.subsequentRequest) {
					RouteHeader header = (RouteHeader) request.getHeader(RouteHeader.NAME);
					if(header != null) {
						assignedUri = (SipURI) header.getAddress().getURI();
						originalRouteHeaderUri = (SipURI) assignedUri.clone();
						request.removeFirst(RouteHeader.NAME);
					} else {
						if(request.getRequestURI() instanceof SipURI) {
							SipURI sipUri =(SipURI) request.getRequestURI();
							//nextNodeInRequestUri = true;
							assignedNode = getAliveNode(sipUri.getHost(), sipUri.getPort(), transport, ctx);
						}
					}
					if(logger.isDebugEnabled()) {
			    		logger.debug("Subsequent request -> Found Route Header " + header + " |Next node is " + assignedNode);
			    	}
				} else if(request.getRequestURI() instanceof SipURI) {
					SipURI sipUri =(SipURI) request.getRequestURI();
					//nextNodeInRequestUri = true;
					assignedNode = getAliveNode(sipUri.getHost(), sipUri.getPort(), transport, ctx);
					if(logger.isDebugEnabled()) {
			    		logger.debug("NOT Subsequent request -> using sipUri " + sipUri + " |Next node is " + assignedNode);
			    	}
				}
			}
			
			if(assignedNode == null) {
				if(logger.isDebugEnabled()) {
		    		logger.debug("assignedNode is null");
		    	}
				nextNode = ctx.balancerAlgorithm.processExternalRequest(request);
				if(nextNode instanceof NullServerNode) {
					if(logger.isDebugEnabled()) {
						logger.debug("Algorithm returned a NullServerNode. We will not attempt to forward this request " + request);
					}
				}
				if(nextNode != null) {
					if(logger.isDebugEnabled()) {
						String nodesString = "";
						Object[] nodes = ctx.nodes.toArray();
						for(Object n : nodes) {
							nodesString +=n + " , ";
						}
			    		logger.debug("Next node is not null. Assigned uri is " + assignedUri + "Available nodes: " + nodesString);
			    	}
					//Adding Route Header pointing to the node the sip balancer wants to forward to
					SipURI routeSipUri;
					try {

						if(assignedUri == null) { // If a next node is NOT already assigned in the dialog from previous requests
							routeSipUri = balancerRunner.balancerContext.addressFactory
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


						final RouteHeader route = balancerRunner.balancerContext.headerFactory.createRouteHeader(
								balancerRunner.balancerContext.addressFactory.createAddress(routeSipUri));
						request.addFirst(route);

						// If the request is meant for the AS it must recognize itself in the ruri, so update it too
						// For http://code.google.com/p/mobicents/issues/detail?id=2132
						if(originalRouteHeaderUri != null && request.getRequestURI().isSipURI()) {
							SipURI uri = (SipURI) request.getRequestURI();
							// we will just compare by hostport id
							String rurihostid = uri.getHost() + uri.getPort();
							String originalhostid = originalRouteHeaderUri.getHost() + originalRouteHeaderUri.getPort();
							if(rurihostid.equals(originalhostid)) {
								uri.setPort(routeSipUri.getPort());
								uri.setHost(routeSipUri.getHost());
							}
						}

					} catch (Exception e) {
						throw new RuntimeException("Error adding route header", e);
					}
				}
			} else {
				nextNode = ctx.balancerAlgorithm.processAssignedExternalRequest(request, assignedNode);
			}
			if(nextNode == null) {
				if(logger.isDebugEnabled()) {
					logger.debug("No nodes available");
				}
				if(!Request.ACK.equalsIgnoreCase(request.getMethod())) {
		            try {
		            	Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, request);			
		                response.setReasonPhrase("No nodes available");
		            	sipProvider.sendResponse(response);	
		            } catch (Exception e) {
		            	logger.error("Unexpected exception while trying to send the error response for this " + request, e);
					}
	            }
				return;
			} else {

			}
		}
		
		hints.serverAssignedNode = nextNode;
		if(!hints.subsequentRequest && dialogCreationMethods.contains(request.getMethod())) {
			addLBRecordRoute(sipProvider, request, hints, version);
		}
		
		// Stateless proxies must not use internal state or ransom values when creating branch because they
		// must repeat exactly the same branches for retransmissions
		final ViaHeader via = (ViaHeader) request.getHeader(ViaHeader.NAME);
		String newBranch = via.getBranch() + callID.substring(0, Math.min(callID.length(), 5));
		// Add the via header to the top of the header list.
		ViaHeader viaHeaderExternal = null;
		ViaHeader viaHeaderInternal = null;
		
		viaHeaderExternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
				balancerRunner.balancerContext.externalViaHost, balancerRunner.balancerContext.externalViaPort, transport, newBranch+ "_" + version);
		
		if(balancerRunner.balancerContext.isTwoEntrypoints()) {
			viaHeaderInternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
					balancerRunner.balancerContext.internalViaHost, balancerRunner.balancerContext.internalViaPort, transport, newBranch + "zsd" + "_" + version);
		}
		if(logger.isDebugEnabled()) {
			logger.debug("ViaHeaders will be added " + viaHeaderExternal + " and " + viaHeaderInternal);
    		logger.debug("Sending the request:\n" + request + "\n on the other side");
    	}
		if(getLoopbackUri(request) != null) {
			logger.warn("Drop. Cannot forward to loopback the following request: " + request);
			return;
		}
		if(!isRequestFromServer && balancerRunner.balancerContext.isTwoEntrypoints()) {
			request.addHeader(viaHeaderExternal); 
			if(viaHeaderInternal != null) request.addHeader(viaHeaderInternal); 
			balancerRunner.balancerContext.internalSipProvider.sendRequest(request);
		} else {
			// Check if the next hop is actually the load balancer again
			if(viaHeaderInternal != null) request.addHeader(viaHeaderInternal); 
			request.addHeader(viaHeaderExternal); 
			balancerRunner.balancerContext.externalSipProvider.sendRequest(request);
		}
	}
	
	private RecordRouteHeader stampRecordRoute(RecordRouteHeader rrh, RouteHeaderHints hints, String transport) {
		SipURI uri = (SipURI) rrh.getAddress().getURI();
		try {

			uri.setParameter(ROUTE_PARAM_NODE_HOST, hints.serverAssignedNode.getIp());
			uri.setParameter(ROUTE_PARAM_NODE_PORT, hints.serverAssignedNode.getProperties().get(transport.toLowerCase()+"Port").toString());
			uri.setParameter(ROUTE_PARAM_NODE_VERSION, hints.version);
		} catch (ParseException e) {
			logger.warn("Problem adding rrh" ,e);
		}
		return rrh;
	}
	
	private void addTwoRecordRoutes(Request request, RecordRouteHeader first, RecordRouteHeader second, RouteHeaderHints hints, String transport) {
		if(logger.isDebugEnabled()) {
			logger.debug("adding Record Router Header :" + first);
		}
		
		request.addHeader(stampRecordRoute((RecordRouteHeader) first.clone(), hints, transport));

		if(logger.isDebugEnabled()) {
			logger.debug("adding Record Router Header :" + second);
		}
		request.addHeader(stampRecordRoute((RecordRouteHeader) second.clone(), hints, transport));
	}

	/**
	 * @param sipProvider
	 * @param request
	 * @param hints 
	 * @throws ParseException
	 */
	private void addLBRecordRoute(SipProvider sipProvider, Request request, RouteHeaderHints hints, String version)
	throws ParseException {				
		if(logger.isDebugEnabled()) {
			logger.debug("adding Record Router Header :" + balancerRunner.balancerContext.activeExternalHeader);
		}
		String transport = ((ViaHeader)request.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
		int transportIndex = transport.equalsIgnoreCase("udp")?0:1;
		if(balancerRunner.balancerContext.isTwoEntrypoints()) { // comes from client
			if(sipProvider.equals(balancerRunner.balancerContext.externalSipProvider)) {
				addTwoRecordRoutes(request, 
						balancerRunner.balancerContext.activeExternalHeader[transportIndex],
						balancerRunner.balancerContext.activeInternalHeader[transportIndex],
						hints, transport);
			
			} else { // comes from app server
				addTwoRecordRoutes(request,
						balancerRunner.balancerContext.activeInternalHeader[transportIndex],
						balancerRunner.balancerContext.activeExternalHeader[transportIndex],
						hints, transport);
			}	
		} else {
			RecordRouteHeader recordRouteHeader = balancerRunner.balancerContext.activeExternalHeader[transportIndex];
			if(hints.serverAssignedNode != null) {
				recordRouteHeader = (RecordRouteHeader) recordRouteHeader.clone();
				SipURI sipuri = (SipURI) recordRouteHeader.getAddress().getURI();
				
				sipuri.setParameter(ROUTE_PARAM_NODE_HOST, hints.serverAssignedNode.getIp());
				sipuri.setParameter(ROUTE_PARAM_NODE_PORT, hints.serverAssignedNode.getProperties().get(transport.toLowerCase()+"Port").toString());
				sipuri.setParameter(ROUTE_PARAM_NODE_VERSION, version);
			}
			request.addHeader(recordRouteHeader);
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
		String hostVersion = routeSipUri.getParameter(ROUTE_PARAM_NODE_VERSION);
		if(hostNode != null && hostPort != null) {
			int port = Integer.parseInt(hostPort);
			String transport = routeSipUri.getTransportParam();
			if(transport == null) transport = "udp";
			node = register.getNode(hostNode, port, transport, hostVersion);
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
		if(logger.isDebugEnabled()) {
    		logger.debug("Checking if there is any route headers meant for the LB to remove...");
    	}
		SIPNode node = null;
		String callVersion = null;
		int numberOfRemovedRouteHeaders = 0;
		
		//Removing first routeHeader if it is for the sip balancer
		RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
		if(routeHeader != null) {
		    SipURI routeUri = (SipURI)routeHeader.getAddress().getURI();
		    callVersion = routeUri.getParameter(ROUTE_PARAM_NODE_VERSION);
		    
		    //FIXME check against a list of host we may have too
		    if(!isRouteHeaderExternal(routeUri.getHost(), routeUri.getPort())) {
		    	if(logger.isDebugEnabled()) {
		    		logger.debug("this route header is for the LB removing it " + routeUri);
		    	}
		    	
		    	numberOfRemovedRouteHeaders ++;
		    	
		    	request.removeFirst(RouteHeader.NAME);
		    	routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
		    	//since we used double record routing we may have 2 routes corresponding to us here
		        // for ACK and BYE from caller for example
		        node = checkRouteHeaderForSipNode(routeUri);
		        
		        if(routeHeader != null) {
		         	
		            routeUri = (SipURI)routeHeader.getAddress().getURI();
		            //FIXME check against a list of host we may have too
		            if(!isRouteHeaderExternal(routeUri.getHost(), routeUri.getPort())) {
		            	if(logger.isDebugEnabled()) {
		            		logger.debug("this route header is for the LB removing it " + routeUri);
		            	}
		            	
		            	numberOfRemovedRouteHeaders ++;
		            	
		            	request.removeFirst(RouteHeader.NAME);
		            	if(node == null) {
		            		node = checkRouteHeaderForSipNode(routeUri);
		            	}
		            	
		            	// SIPP sometimes appends more headers and lets remove them here. There is no legitimate reason
		            	// more than two SIP LB headers to be place next to each-other, so this cleanup is SAFE!
		            	boolean moreHeaders = true;
		            	while(moreHeaders) {
		            		RouteHeader extraHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
		            		if(extraHeader != null) {
		            			SipURI u = (SipURI)extraHeader.getAddress().getURI();
		            			if(!isRouteHeaderExternal(u.getHost(), u.getPort())) {
		            				numberOfRemovedRouteHeaders ++;
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
		
		if(node == null) {
			if(request.getRequestURI().isSipURI()) {
				node = checkRouteHeaderForSipNode((SipURI) request.getRequestURI());
			}
		}
		
		//logger.info(request.ge + " has this hint " + node);
		ToHeader to = (ToHeader)(request.getHeader(ToHeader.NAME));
		
		/*
		 * We determine if this is subsequent based on To tag instead of checking route header metadata.
		 */
		boolean subsequent = to.getTag() != null;
		
		if(logger.isDebugEnabled()) {
    		logger.debug("Number of removed Route headers is " + numberOfRemovedRouteHeaders);
		}
		if(numberOfRemovedRouteHeaders != 2 && subsequent) {
			logger.warn("A subsequent request should have two Route headers. Number of removed Route headers is " + numberOfRemovedRouteHeaders
					+ ". This indicates a client is removing important headers.");
		}

		return new RouteHeaderHints(node, subsequent, callVersion);
	}
	
	/**
	 * Check if the sip uri is meant for the LB same host and same port
	 * @param sipUri sip Uri to check 
	 * @return
	 */
	private boolean isRouteHeaderExternal(String host, int port) {
		 //FIXME check against a list of host we may have too and add transport
		if((host.equalsIgnoreCase(balancerRunner.balancerContext.externalHost) || host.equalsIgnoreCase(balancerRunner.balancerContext.internalHost))
				&& (port == balancerRunner.balancerContext.externalPort || port == balancerRunner.balancerContext.internalPort)) {
			return false;
		}
		if((host.equalsIgnoreCase(balancerRunner.balancerContext.externalIpLoadBalancerAddress) && port == balancerRunner.balancerContext.externalLoadBalancerPort)) {
			return false;
		}
		if((host.equalsIgnoreCase(balancerRunner.balancerContext.internalIpLoadBalancerAddress) && port == balancerRunner.balancerContext.internalLoadBalancerPort)) {
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
		if(logger.isDebugEnabled()) {
        	logger.debug("Decreasing  the Max Forward Header ");
        }
		MaxForwardsHeader maxForwardsHeader = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);
		if (maxForwardsHeader == null) {
			maxForwardsHeader = balancerRunner.balancerContext.headerFactory.createMaxForwardsHeader(70);
			request.addHeader(maxForwardsHeader);
		} else {
			if(maxForwardsHeader.getMaxForwards() - 1 > 0) {
				maxForwardsHeader.setMaxForwards(maxForwardsHeader.getMaxForwards() - 1);
			} else {
				//Max forward header equals to 0, thus sending too many hops response
				Response response = balancerRunner.balancerContext.messageFactory.createResponse
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
		if(logger.isDebugEnabled()) {
			logger.debug("got response :\n" + originalResponse);
		}

		updateStats(originalResponse);

		final Response response = (Response) originalResponse; 
		
		SIPNode senderNode = getSenderNode(response);
		if(senderNode != null) {
			senderNode.updateTimerStamp();
		}
		
		// Topmost via headers is me. As it is response to external request
		ViaHeader viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
		
		String branch = viaHeader.getBranch();
		int versionDelimiter = branch.lastIndexOf('_');
		String version = branch.substring(versionDelimiter + 1);
		
		InvocationContext ctx = balancerRunner.getInvocationContext(version);
		
		if(viaHeader!=null && !isRouteHeaderExternal(viaHeader.getHost(), viaHeader.getPort())) {
			response.removeFirst(ViaHeader.NAME);
		}
		
		viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
		
		if(viaHeader!=null && !isRouteHeaderExternal(viaHeader.getHost(), viaHeader.getPort())) {
			response.removeFirst(ViaHeader.NAME);
		}
		
		boolean fromServer = false;
		if(balancerRunner.balancerContext.isTwoEntrypoints()) {
			fromServer = sipProvider.equals(balancerRunner.balancerContext.internalSipProvider);
		} else {
			fromServer = senderNode == null;
		}
		
		if(fromServer) {
			/*
			if("true".equals(balancerRunner.balancerContext.properties.getProperty("removeNodesOn500Response")) && response.getStatusCode() == 500) {
				// If the server is broken remove it from the list and try another one with the next retransmission
				if(!(sourceNode instanceof ExtraServerNode)) {
					if(balancerRunner.balancerContext.nodes.size()>1) {
						balancerRunner.balancerContext.nodes.remove(sourceNode);
						balancerRunner.balancerContext.balancerAlgorithm.nodeRemoved(sourceNode);
					}
				}
			}*/
			ctx.balancerAlgorithm.processInternalResponse(response);
			try {	
				if(logger.isDebugEnabled()) {
					logger.debug("from server sending response externally " + response);
				}
				balancerRunner.balancerContext.externalSipProvider.sendResponse(response);
			} catch (Exception ex) {
				logger.error("Unexpected exception while forwarding the response \n" + response, ex);
			}
		} else {
			try {	
				ctx.balancerAlgorithm.processExternalResponse(response);
				if(balancerRunner.balancerContext.isTwoEntrypoints()) {
					if(logger.isDebugEnabled()) {
						logger.debug("two entry points: from external sending response " + response);
					}
					balancerRunner.balancerContext.internalSipProvider.sendResponse(response);
				} else {
					if(logger.isDebugEnabled()) {
						logger.debug("one entry point: from external sending response " + response);
					}
					balancerRunner.balancerContext.externalSipProvider.sendResponse(response);
				}
			} catch (Exception ex) {
				logger.error("Unexpected exception while forwarding the response \n" + response, ex);
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
			if(logger.isDebugEnabled()) {
				logger.debug("timeout => " + transaction.getRequest().toString());
			}
		} else {
			transaction = timeoutEvent.getClientTransaction();
			if(logger.isDebugEnabled()) {
				logger.debug("timeout => " + transaction.getRequest().toString());
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
			if(logger.isDebugEnabled()) {
				logger.debug("timeout => " + transaction.getRequest().toString());
			}
		} else {
			transaction = transactionTerminatedEvent.getClientTransaction();
			if(logger.isDebugEnabled()) {
				logger.debug("timeout => " + transaction.getRequest().toString());
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
		return balancerRunner.balancerContext.requestsProcessed.get();
	}
	
	/**
	 * @return the requestsProcessed
	 */
	public long getNumberOfResponsesProcessed() {
		return balancerRunner.balancerContext.responsesProcessed.get();
	}

	/**
	 * @return the requestsProcessed
	 */
	public long getRequestsProcessedByMethod(String method) {
		AtomicLong requestsProcessed = balancerRunner.balancerContext.requestsProcessedByMethod.get(method);
		if(requestsProcessed != null) {
			return requestsProcessed.get();
		}
		return 0;
	}
	
	public long getResponsesProcessedByStatusCode(String statusCode) {
		AtomicLong responsesProcessed = balancerRunner.balancerContext.responsesProcessedByStatusCode.get(statusCode);
		if(responsesProcessed != null) {
			return responsesProcessed.get();
		}
		return 0;
	}
	
	public Map<String, AtomicLong> getNumberOfRequestsProcessedByMethod() {
		return balancerRunner.balancerContext.requestsProcessedByMethod;
	}
	
	public Map<String, AtomicLong> getNumberOfResponsesProcessedByStatusCode() {
		return balancerRunner.balancerContext.responsesProcessedByStatusCode;
	}
	
	public BalancerContext getBalancerAlgorithmContext() {
		return balancerRunner.balancerContext;
	}

	public void setBalancerAlgorithmContext(
			BalancerContext balancerAlgorithmContext) {
		balancerRunner.balancerContext = balancerAlgorithmContext;
	}
	
	/**
	 * @param skipStatistics the skipStatistics to set
	 */
	public void setGatherStatistics(boolean skipStatistics) {
		balancerRunner.balancerContext.gatherStatistics = skipStatistics;
	}

	/**
	 * @return the skipStatistics
	 */
	public boolean isGatherStatistics() {
		return balancerRunner.balancerContext.gatherStatistics;
	}
}
