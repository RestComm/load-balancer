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
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.HeaderExt;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.message.ResponseExt;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.SIPMessageValve;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.TooManyListenersException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
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
import javax.sip.TransportAlreadySupportedException;
import javax.sip.TransportNotSupportedException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.TelURL;
import javax.sip.address.URI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderAddress;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ReasonHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.heartbeat.api.Node;

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
    public static final int TLS = 2;
    public static final int WS = 3;
    public static final int WSS = 4;

    BalancerRunner balancerRunner;

    protected static final HashSet<String> dialogCreationMethods=new HashSet<String>(2);

    static{
        dialogCreationMethods.add(Request.INVITE);
        dialogCreationMethods.add(Request.SUBSCRIBE);
    }      

    public NodeRegister register;

    protected String[] extraServerAddresses;
    protected int[] extraServerPorts;

    public SIPBalancerForwarder(LoadBalancerConfiguration lbConfig, BalancerRunner balancerRunner, NodeRegister register) throws IllegalStateException{
        super();
        this.balancerRunner = balancerRunner;
        this.balancerRunner.balancerContext.forwarder = this;
        this.balancerRunner.balancerContext.lbConfig = lbConfig;
        this.register = register;		
    }
 
    public void start() {
    	
		setSipLoadBalancerProperty();
        setLoadBalacerHostsPorts();

        String extraServerNodesString = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExtraServerNodes();
        if(extraServerNodesString != null&&!extraServerNodesString.equals(""))
        	setExtraServerNodes(extraServerNodesString);

        try {
        	createSipStack();
            ArrayList <ListeningPoint> listeningPoints = new ArrayList<ListeningPoint>();
            ArrayList <ListeningPoint> listeningPointsIpv6 = new ArrayList<ListeningPoint>();
            createListeningPoints(balancerRunner.balancerContext.externalPorts, balancerRunner.balancerContext.externalHost, listeningPoints);
            createListeningPoints(balancerRunner.balancerContext.externalIpv6Ports, balancerRunner.balancerContext.externalIpv6Host, listeningPointsIpv6);
            if(balancerRunner.balancerContext.externalIpLoadBalancerAddresses != null)
            {
            	createRecordRouteHeaders(balancerRunner.balancerContext.externalIpLoadBalancerPorts, balancerRunner.balancerContext.externalIpLoadBalancerAddresses.get(0), balancerRunner.balancerContext.activeExternalHeader);
            	createRecordRouteHeaders(balancerRunner.balancerContext.externalIpv6LoadBalancerPorts, balancerRunner.balancerContext.externalIpLoadBalancerAddresses.get(0), balancerRunner.balancerContext.activeExternalIpv6Header);
            }
            else
            {
            	createRecordRouteHeaders(listeningPoints, balancerRunner.balancerContext.activeExternalHeader);
            	createRecordRouteHeaders(listeningPointsIpv6, balancerRunner.balancerContext.activeExternalIpv6Header);
            }

            balancerRunner.balancerContext.externalSipProvider = createSipProviders(listeningPoints);
            if(listeningPointsIpv6.size()!=0)
            	balancerRunner.balancerContext.externalIpv6SipProvider = createSipProviders(listeningPointsIpv6);
            
            if(balancerRunner.balancerContext.isTwoEntrypoints()) {
            	listeningPoints = new ArrayList<ListeningPoint>();
            	listeningPointsIpv6 = new ArrayList<ListeningPoint>();
            	createListeningPoints(balancerRunner.balancerContext.internalPorts, balancerRunner.balancerContext.internalHost, listeningPoints);
                createListeningPoints(balancerRunner.balancerContext.internalIpv6Ports, balancerRunner.balancerContext.internalIpv6Host, listeningPointsIpv6);
            	
            	if(balancerRunner.balancerContext.internalIpLoadBalancerAddresses != null)
            	{
            		createRecordRouteHeaders(balancerRunner.balancerContext.internalIpLoadBalancerPorts, balancerRunner.balancerContext.internalIpLoadBalancerAddresses.get(0), balancerRunner.balancerContext.activeInternalHeader);
                	createRecordRouteHeaders(balancerRunner.balancerContext.internalIpv6LoadBalancerPorts, balancerRunner.balancerContext.internalIpLoadBalancerAddresses.get(0), balancerRunner.balancerContext.activeInternalIpv6Header);
            	}
                else
                 	if(balancerRunner.balancerContext.isTwoEntrypoints())
                 	{
                    	createRecordRouteHeaders(listeningPoints, balancerRunner.balancerContext.activeInternalHeader);
                    	createRecordRouteHeaders(listeningPointsIpv6, balancerRunner.balancerContext.activeInternalIpv6Header);
                 	}
            	balancerRunner.balancerContext.internalSipProvider = createSipProviders(listeningPoints);
                if(listeningPointsIpv6.size()!=0)
                	balancerRunner.balancerContext.internalIpv6SipProvider = createSipProviders(listeningPointsIpv6);
            }
            setViaHostsPorts();
			balancerRunner.balancerContext.sipStack.start();
			for(SIPMessageValve valve : balancerRunner.balancerContext.sipStack.sipMessageValves)
				if(valve instanceof SIPBalancerValveProcessor)
					((SIPBalancerValveProcessor)valve).balancerRunner = balancerRunner;
        } catch (Exception ex) {
            throw new IllegalStateException("Can't create sip objects and lps due to["+ex.getMessage()+"]", ex);
        }
        if(logger.isInfoEnabled())
        	printConfigInfo();
    }

	public void stop() {
        if(balancerRunner.balancerContext.sipStack == null) return;// already stopped
        @SuppressWarnings("rawtypes")
		Iterator sipProviderIterator = balancerRunner.balancerContext.sipStack.getSipProviders();
        try{
            while (sipProviderIterator.hasNext()) {
                SipProvider sipProvider = (SipProvider)sipProviderIterator.next();
                sipProvider.removeSipListener(this);
                ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
                balancerRunner.balancerContext.sipStack.deleteSipProvider(sipProvider);	
                
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
    	BalancerAppContent content=(BalancerAppContent)requestEvent.getSource();
    	boolean isIpv6 = content.isIpv6();
        final SipProvider sipProvider = content.getProvider();
        final Request request = requestEvent.getRequest();
        final String requestMethod = request.getMethod();

        if(logger.isDebugEnabled()) {
            logger.debug("got request:\n"+request);
        }
        
        if((requestMethod.equals(Request.OPTIONS) ||
                requestMethod.equals(Request.INFO)) &&
                request.getHeader("Mobicents-Heartbeat") != null &&
                sipProvider == balancerRunner.balancerContext.internalSipProvider) {
            byte[] bytes = (byte[]) request.getContent();
            Properties prop = new Properties();
            try {
                prop.load(new ByteArrayInputStream(bytes, 0, bytes.length));
                Node node = new Node(prop.getProperty("hostname"), prop.getProperty("ip"));
                for(String id : prop.stringPropertyNames()) {
                    node.getProperties().put(id, prop.getProperty(id));
                }
                ArrayList<Node> list = new ArrayList<Node>();
                list.add(node);
                this.register.handlePingInRegister(list);
                Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.OK, request);			
                sipProvider.sendResponse(response);	
                return;
            } catch (Exception e) {
                logger.error("Failure parsing heartbeat properties from this request " + request, e);
            }
        }
        //Issue 10: https://telestax.atlassian.net/browse/LB-10
        if (request.getContent() != null || (requestMethod.equals(Request.REGISTER) && sipProvider != balancerRunner.balancerContext.internalSipProvider)) {
            SIPMessage message = (SIPMessage)request;            

            String initialRemoteAddr = message.getPeerPacketSourceAddress().getHostAddress();
            String initialRemotePort = String.valueOf(message.getPeerPacketSourcePort());

            Header remoteAddrHeader = null;
            Header remotePortHeader = null;
            try {
                remoteAddrHeader = SipFactory.getInstance().createHeaderFactory().createHeader("X-Sip-Balancer-InitialRemoteAddr", initialRemoteAddr);
                remotePortHeader = SipFactory.getInstance().createHeaderFactory().createHeader("X-Sip-Balancer-InitialRemotePort", initialRemotePort);
            } catch (PeerUnavailableException e) {
                logger.error("Unexpected exception while creating custom headers for REGISTER message ", e);
            } catch (ParseException e) {
                logger.error("Unexpected exception while creating custom headers for REGISTER message ", e);
            }
            if (remoteAddrHeader != null)
                request.addHeader(remoteAddrHeader);
            if (remotePortHeader != null)
                request.addHeader(remotePortHeader);
        } 
        try {	
            updateStats(request);
            forwardRequest(sipProvider,request, isIpv6);          						
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
            	balancerRunner.balancerContext.bytesTransferred.addAndGet(((Request) message).getContentLength().getContentLength());
                balancerRunner.balancerContext.requestsProcessed.incrementAndGet();
                final String method = ((Request) message).getMethod();
                final AtomicLong requestsProcessed = balancerRunner.balancerContext.requestsProcessedByMethod.get(method);
                if(requestsProcessed == null) {
                    balancerRunner.balancerContext.requestsProcessedByMethod.put(method, new AtomicLong(0));
                } else {
                    requestsProcessed.incrementAndGet();
                }
                if(Request.INVITE.equalsIgnoreCase(method)) {
                	balancerRunner.incCalls();
                }
                if(Request.MESSAGE.equalsIgnoreCase(method)) {
                	balancerRunner.incMessages();
                }
            } else {
                balancerRunner.balancerContext.responsesProcessed.incrementAndGet();
                balancerRunner.balancerContext.bytesTransferred.addAndGet(((Response) message).getContentLength().getContentLength());
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

    private Node getAliveNode(String host, int port, String otherTransport, InvocationContext ctx,Boolean isIpV6) {
        //return getNodeFromCollection(host, port, otherTransport, ctx.nodes);
    	return ctx.sipNodeMap(isIpV6).get(new KeySip(host,port,isIpV6));
    }

    private Node getAliveNodeAnyVersion(String host, int port, String otherTransport) {
        return getNodeFromCollection(host, port, otherTransport, balancerRunner.balancerContext.aliveNodes);
    }

    private Node getNodeFromCollection(String host, int port, String otherTransport, Collection<Node> ctx) {
    	
        otherTransport = otherTransport.toLowerCase();
        for(Node node : ctx) {
            if(host.equals(node.getHostName()) || host.equals(node.getIp())) {
            	if(Integer.parseInt(node.getProperties().get(otherTransport + "Port")) == port) {
                    return node;
                }
            }
        }
        return null;
    }

    private Node getNodeDeadOrAlive(String host, int port, String otherTransport) {
        return getNodeFromCollection(host, port, otherTransport, balancerRunner.balancerContext.allNodesEver);
    }

    private boolean isViaHeaderFromServer(Request request) {
        ViaHeader viaHeader = ((ViaHeader)request.getHeader(ViaHeader.NAME));
        String host = viaHeader.getHost();
        String transport = viaHeader.getTransport();
        if(balancerRunner.balancerContext.internalTransport!=null)
        {
        	if(logger.isDebugEnabled()) {
				logger.debug("Set internal transport for Via checking (is from server): " +  balancerRunner.balancerContext.internalTransport);
			}
        	transport = balancerRunner.balancerContext.internalTransport;
        }else if(balancerRunner.balancerContext.terminateTLSTraffic)
		{
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				transport = ListeningPoint.TCP;
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				transport = ListeningPointExt.WS;
		}
        if(transport == null) transport = ListeningPoint.UDP;
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

//    private Node getTransactionSourceNode(Response response) {
//        ViaHeader viaHeader = ((ViaHeader)response.getHeader(ViaHeader.NAME));
//        String host = viaHeader.getHost();
//        String transport = viaHeader.getTransport();
//        if(transport == null) transport = ListeningPoint.UDP;
//        transport = transport.toLowerCase();
//        int port = viaHeader.getPort();
//        if(extraServerAddresses != null) {
//            for(int q=0; q<extraServerAddresses.length; q++) {
//                if(extraServerAddresses[q].equals(host) && extraServerPorts[q] == port) {
//                    return ExtraServerNode.extraServerNode;
//                }
//            }
//        }
//        Node node = getNodeDeadOrAlive(host, port, transport);
//        if(node != null) {
//            return node;
//        }
//        return null;
//    }

    private Node getSenderNode(Response response) {
        SIPResponse resp = (SIPResponse) response;
        String host = resp.getRemoteAddress().getHostAddress();
        
        ViaHeader viaHeader = ((ViaHeader)response.getHeader(ViaHeader.NAME));
		String currentTransport = viaHeader.getTransport();
		String transport = null;
		if(balancerRunner.balancerContext.internalTransport!=null)
        {
			if(logger.isDebugEnabled()) {
				logger.debug("Set internal transport for getting sender Node: " +  balancerRunner.balancerContext.internalTransport);
			}
        	transport = balancerRunner.balancerContext.internalTransport;
        }
		else if (balancerRunner.balancerContext.terminateTLSTraffic) 
        {
        	if (currentTransport.equalsIgnoreCase(ListeningPoint.TLS))
				transport = ListeningPoint.TCP;
			else if (currentTransport.equalsIgnoreCase(ListeningPointExt.WSS))
				transport = ListeningPointExt.WS;
			else
				transport = currentTransport;
		} 
		else 
		{
			transport = currentTransport;
		}
        if(transport == null) transport = ListeningPoint.UDP;
        int port = resp.getRemotePort();
        if(extraServerAddresses != null) {
            for(int q=0; q<extraServerAddresses.length; q++) {
                if(extraServerAddresses[q].equals(host) && extraServerPorts[q] == port) {
                    return ExtraServerNode.extraServerNode;
                }
            }
        }
        Node node = getNodeDeadOrAlive(host, port, transport);
        if(node != null) {
            return node;
        }
        return null;
    }

    public SipURI getLoopbackUri(Request request, boolean isIpv6) {
    	if(logger.isDebugEnabled())
    		logger.debug("Check request for loop. Request: " + request);
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
        if(uri.getHost().matches(".*[a-zA-Z]+.*"))
        {
        	if(logger.isDebugEnabled())
        		logger.debug("We are going to patch URI because it has domain name instead of IP : " + uri);
			try 
        	{
				uri.setHost(InetAddress.getByName(uri.getHost()).getHostAddress());
			} catch (UnknownHostException | ParseException e) {
				e.printStackTrace();
			}
        }
        if(uri != null) 
        {
        	if(!isIpv6)
        	{
        		if(uri.getHost().equals(balancerRunner.balancerContext.externalHost) 
        				|| uri.getHost().equals(balancerRunner.balancerContext.publicIP) 
        				|| uri.getHost().equals(balancerRunner.balancerContext.externalIpLoadBalancerAddresses))
        		{
        			int port = uri.getPort();
        			for(int externalPort : balancerRunner.balancerContext.externalPorts)
        				if(port == externalPort)
        					return uri;
        		}
                
        		if(uri.getHost().equals(balancerRunner.balancerContext.internalHost) 
        				|| uri.getHost().equals(balancerRunner.balancerContext.publicIP)
        				|| uri.getHost().equals(balancerRunner.balancerContext.internalIpLoadBalancerAddresses))
        		{
        			int port = uri.getPort();
        			for(int internalPort : balancerRunner.balancerContext.internalPorts)
        				if(port == internalPort)
        					return uri;
        		}
        	}
        	else
        	{
        		if(uri.getHost().equals(balancerRunner.balancerContext.externalIpv6Host) 
        				|| uri.getHost().equals(balancerRunner.balancerContext.publicIPv6) 
        				|| uri.getHost().equals(balancerRunner.balancerContext.externalIpv6LoadBalancerAddresses))
        		{
        			int port = uri.getPort();
        			for(int externalIpv6Port : balancerRunner.balancerContext.externalIpv6Ports)
        				if(port == externalIpv6Port)
        					return uri;
        		}
                
        		if(uri.getHost().equals(balancerRunner.balancerContext.internalIpv6Host) 
        				|| uri.getHost().equals(balancerRunner.balancerContext.publicIPv6)
        				|| uri.getHost().equals(balancerRunner.balancerContext.internalIpv6LoadBalancerAddresses))
        		{
        			int port = uri.getPort();
        			for(int internalIpv6Port : balancerRunner.balancerContext.internalIpv6Ports)
        				if(port == internalIpv6Port)
        					return uri;
        		}
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
    private void forwardRequest(SipProvider sipProvider, Request request, boolean isIpv6)
                    throws ParseException, InvalidArgumentException, SipException, TransactionUnavailableException {
        if(logger.isDebugEnabled()) {
            logger.debug("got request:\n"+request);
        } 

        boolean isRequestFromServer = false;
        if(!balancerRunner.balancerContext.isTwoEntrypoints()) {
            isRequestFromServer = isViaHeaderFromServer(request);
        } else {
            isRequestFromServer = sipProvider.equals(balancerRunner.balancerContext.internalSipProvider) || sipProvider.equals(balancerRunner.balancerContext.internalIpv6SipProvider);
        }

        if(isRequestFromServer)
        {
        	ViaHeader viaHeader = (ViaHeader)request.getHeader(ViaHeader.NAME);
        	String host = viaHeader.getHost();
        	if(host.matches(".*[a-zA-Z]+.*"))
            {
    			try 
            	{
    				host = InetAddress.getByName(host).getHostAddress();
    			} catch (UnknownHostException e) {
    				e.printStackTrace();
    			}
            }
        	int port = viaHeader.getPort();
        	String transport = viaHeader.getTransport().toLowerCase();
        	Node node =	getNodeDeadOrAlive(host, port, transport);
        	if(node!=null)
        	{
        		if(logger.isDebugEnabled())
        			logger.debug("Updating Timestamp of node: " + node + " because of request from it");
        		node.updateTimerStamp();
        	}
        }

        final boolean isCancel = Request.CANCEL.equals(request.getMethod());

        if(!isCancel) {
            decreaseMaxForwardsHeader(sipProvider, request);
        }

		String outerTransport = ((ViaHeader) request.getHeader(ViaHeader.NAME))
				.getTransport().toLowerCase();
		if (isRequestFromServer) {
			Boolean hasTransport = false;
			if (request.getRequestURI().isSipURI()) {
				if (((SipUri) request.getRequestURI()).getTransportParam() != null) {
					outerTransport = ((SipUri) request.getRequestURI())
							.getTransportParam();
					hasTransport = true;
				}
			}

			if (!hasTransport) {
				outerTransport = getRouteHeadersMeantForLB(request, isIpv6);
				if (outerTransport == null)
					outerTransport = ((ViaHeader) request
							.getHeader(ViaHeader.NAME)).getTransport()
							.toLowerCase();
			}
		}

        RouteHeaderHints hints = removeRouteHeadersMeantForLB(request,isIpv6);

        String version = hints.version;

        if(version == null) {
            version = register.getLatestVersion();
            hints.version = version;
        }

        InvocationContext ctx = balancerRunner.getInvocationContext(version);

        final String callID = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();

		String transport = null;
		
		if(balancerRunner.balancerContext.internalTransport!=null)
        {
			if(logger.isDebugEnabled()) {
				logger.debug("Set internal transport for NODE looking: " +  balancerRunner.balancerContext.internalTransport);
			}
        	transport = balancerRunner.balancerContext.internalTransport.toLowerCase();
        }else if (balancerRunner.balancerContext.terminateTLSTraffic)
		{
        	switch (((ViaHeader) request.getHeader(ViaHeader.NAME))
					.getTransport()) {
				case ListeningPoint.TLS:
					transport = ListeningPoint.TCP.toLowerCase();
					break;
				case ListeningPointExt.WSS:
					transport = ListeningPointExt.WS.toLowerCase();
					break;
				case ListeningPointExt.WS:
				case ListeningPointExt.TCP:
				case ListeningPointExt.UDP:
					transport = ((ViaHeader) request.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
			} 
			if(logger.isDebugEnabled()) {
         		logger.debug("Terminate TLS traffic, isRequestFromServer: " + isRequestFromServer + 
         				" transport before " + ((ViaHeader) request.getHeader(ViaHeader.NAME))
						.getTransport() + ", transport after " + transport);
         	}
		}
		else 
		{
			transport = ((ViaHeader) request.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
		}

        if(hints.serverAssignedNode !=null) {
        	String headerKey = null;
        	if(balancerRunner.balancerContext.sipHeaderAffinityKey.equalsIgnoreCase(ToHeader.NAME))
        	{
        		URI currURI=((HeaderAddress)request.getHeader(balancerRunner.balancerContext.sipHeaderAffinityKey)).getAddress().getURI();
        		if(currURI.isSipURI())
        			headerKey = ((SipURI)currURI).getUser();
        		else
        			headerKey = ((TelURL)currURI).getPhoneNumber();
        		
        		if(balancerRunner.balancerContext.sipHeaderAffinityKeyExclusionPattern != null && balancerRunner.balancerContext.sipHeaderAffinityKeyExclusionPattern.matcher(headerKey).matches()) {
        			headerKey = ((HeaderExt) request.getHeader(balancerRunner.balancerContext.sipHeaderAffinityFallbackKey)).getValue();
        		}
        	}
        	else if(balancerRunner.balancerContext.sipHeaderAffinityKey.equalsIgnoreCase(FromHeader.NAME)) {
        		headerKey = ((HeaderAddress) request.getHeader(balancerRunner.balancerContext.sipHeaderAffinityKey)).getAddress().getURI().toString();
        		if(balancerRunner.balancerContext.sipHeaderAffinityKeyExclusionPattern != null && balancerRunner.balancerContext.sipHeaderAffinityKeyExclusionPattern.matcher(headerKey).matches()) {
        			headerKey = ((HeaderExt) request.getHeader(balancerRunner.balancerContext.sipHeaderAffinityFallbackKey)).getValue();
        		}
        	}
        	else
        	{
        		headerKey = ((HeaderExt) request.getHeader(balancerRunner.balancerContext.sipHeaderAffinityKey)).getValue();
        	}
        	if(logger.isDebugEnabled()) {
         		logger.debug("headerKey " + headerKey);
         	}
        	if(!request.getMethod().equalsIgnoreCase(Request.ACK))
        		ctx.balancerAlgorithm.assignToNode(headerKey, hints.serverAssignedNode);
            if(logger.isDebugEnabled()) {
                logger.debug("Following node information has been found in one of the route Headers " + hints.serverAssignedNode);
            }

//            SipURI loopbackUri = getLoopbackUri(request);
//            if(loopbackUri != null) {
//            	loopbackUri.setHost(hints.serverAssignedNode.getIp());
//            	loopbackUri.setPort((Integer) hints.serverAssignedNode.getProperties().get(transport + "Port"));
//            }
        }

        Node nextNode = null;

        if(isRequestFromServer) {
        	if(logger.isDebugEnabled()) {
        		logger.debug("Request from server");
        	}
            Header initialAddrHeader = request.getHeader("X-Sip-Balancer-InitialRemoteAddr");
            Header initialPortHeader = request.getHeader("X-Sip-Balancer-InitialRemotePort");
            if(initialAddrHeader != null)
                request.removeHeader(initialAddrHeader.getName());
            if(initialPortHeader != null)
                request.removeHeader(initialPortHeader.getName());
            ctx.balancerAlgorithm.processInternalRequest(request);
            if (request.getMethod().equalsIgnoreCase(Request.INVITE) && ctx.balancerAlgorithm.blockInternalRequest(request)) {
                Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.FORBIDDEN, request);          
                response.setReasonPhrase("Destination not allowed");
                sipProvider.sendResponse(response);
                return;
            }
            nextNode = hints.serverAssignedNode;
            if(logger.isDebugEnabled()) {
        		logger.debug("nexNode " + nextNode);
        	}
        } else {
        	if(logger.isDebugEnabled()) {
        		logger.debug("Request not from server");
        	}
        	if(hints.serverAssignedNode !=null){
        		SipURI loopbackUri = getLoopbackUri(request, isIpv6);
        		if(loopbackUri != null) {
        			loopbackUri.setHost(hints.serverAssignedNode.getIp());
        			loopbackUri.setPort(Integer.parseInt(hints.serverAssignedNode.getProperties().get(transport + "Port")));
            }
        	}
            // Request is NOT from app server, first check if we have hints in Route headers
            Node assignedNode = hints.serverAssignedNode;

            // If there are no hints see if there is route header pointing existing node
            if(assignedNode == null) {
                RouteHeader nextNodeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
                if(nextNodeHeader != null) {
                    URI uri = nextNodeHeader.getAddress().getURI();
                    if(uri instanceof SipURI) {
                        SipURI sipUri = (SipURI) uri;
                        assignedNode = getAliveNode(sipUri.getHost(), sipUri.getPort(), transport, ctx,isIpv6);
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
                            assignedNode = getAliveNode(sipUri.getHost(), sipUri.getPort(), transport, ctx,isIpv6);
                        }
                    }
                    if(logger.isDebugEnabled()) {
                        logger.debug("Subsequent request -> Found Route Header " + header + " |Next node is " + assignedNode);
                    }
                } else if(request.getRequestURI() instanceof SipURI) {
                    SipURI sipUri =(SipURI) request.getRequestURI();
                    //nextNodeInRequestUri = true;
                    assignedNode = getAliveNode(sipUri.getHost(), sipUri.getPort(), transport, ctx,isIpv6);
                    if(logger.isDebugEnabled()) {
                        logger.debug("NOT Subsequent request -> using sipUri " + sipUri + " |Next node is " + assignedNode);
                    }
                }
            }

            if(assignedNode == null) {
                if(logger.isDebugEnabled()) {
                    logger.debug("assignedNode is null");
                }
                if (!securityCheck(request)){
                    logger.warn("Request failed at the security check:\n"+request);
                } else {
                    nextNode = ctx.balancerAlgorithm.processExternalRequest(request,isIpv6);
                }
                if(nextNode instanceof NullServerNode) {
                    if(logger.isDebugEnabled()) {
                        logger.debug("Algorithm returned a NullServerNode. We will not attempt to forward this request " + request);
                    }
                }
                if(nextNode != null) {
                    if(logger.isDebugEnabled()) {
                        String nodesString = "";
                        //Object[] nodes = ctx.nodes.toArray();
                        Object[] nodes = ctx.sipNodeMap(isIpv6).values().toArray();
                        
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
                        Integer port = Integer.parseInt(nextNode.getProperties().get(transport + "Port"));
                        if(port == null) {
                            throw new RuntimeException("Port is null in the node properties for transport="
                                    + transport);
                        }
                        routeSipUri.setPort(port);
                        routeSipUri.setTransportParam(transport);
                        routeSipUri.setLrParam();

                        SipURI uri = (SipURI) request.getRequestURI();
                        RouteHeader header = (RouteHeader) request.getHeader(RouteHeader.NAME);
                        if (isHeaderExternal(uri.getHost(), uri.getPort(), ((ViaHeader)request.getHeader("Via")).getTransport(), isIpv6) || header != null)
                        {
	                        final RouteHeader route = balancerRunner.balancerContext.headerFactory.createRouteHeader(
	                                balancerRunner.balancerContext.addressFactory.createAddress(routeSipUri));
	                        request.addFirst(route);
	
	                        // If the request is meant for the AS it must recognize itself in the ruri, so update it too
	                        // For http://code.google.com/p/mobicents/issues/detail?id=2132
	                        if(originalRouteHeaderUri != null && request.getRequestURI().isSipURI()) {
	                            // we will just compare by hostport id
	                            String rurihostid = uri.getHost() + uri.getPort();
	                            String originalhostid = originalRouteHeaderUri.getHost() + originalRouteHeaderUri.getPort();
	                            if(rurihostid.equals(originalhostid)) {
	                                uri.setPort(routeSipUri.getPort());
	                                uri.setHost(routeSipUri.getHost());
	                            }
	                        }
                        }
                        else
                        {
                            //should not add any routes , packet is destinated to lb
                            uri.setPort(routeSipUri.getPort());
                            uri.setHost(routeSipUri.getHost());
                        }

                    } catch (Exception e) {
                        throw new RuntimeException("Error adding route header", e);
                    }
                }
            } else {
                nextNode = ctx.balancerAlgorithm.processAssignedExternalRequest(request, assignedNode);
                if(logger.isDebugEnabled()) {
                    logger.debug("Next node " + nextNode + " from assignedNode " + assignedNode);
                }
                //add Route header for using it for transferring instead of using uri
                if(nextNode!=null && hints.subsequentRequest && !isRequestFromServer)
                {
                	if(request.getRequestURI().isSipURI()) 
                	{
                        SipURI sipUri =(SipURI) request.getRequestURI();                                             
                        SipURI routeSipUri = balancerRunner.balancerContext.addressFactory.createSipURI(null, nextNode.getIp());
                        Integer port = Integer.parseInt(nextNode.getProperties().get(transport + "Port"));
                	 
                        //port should not be null since it subsequent request
                        if(port != null) 
                        {
                        	routeSipUri.setPort(port);
                        	routeSipUri.setTransportParam(transport);
                        	routeSipUri.setLrParam();                     
                     
                        	if(!sipUri.getHost().equals(routeSipUri.getHost()) || sipUri.getPort()!=routeSipUri.getPort())
                        	{
                        		Boolean oldHeaderMatch=false;
                        		Header oldHeader=request.getHeader(RouteHeader.NAME);
                        		if(oldHeader!=null)
                        		{
                        			RouteHeader oldRouteHeader=(RouteHeader)oldHeader;
                        			if(oldRouteHeader.getAddress().getURI().isSipURI())
                        			{
                        				SipURI oldURI=(SipURI)oldRouteHeader.getAddress().getURI();
                        				if(oldURI.getHost().equals(routeSipUri.getHost()) && oldURI.getPort()==routeSipUri.getPort())
                        					oldHeaderMatch=true;                        				
                        			}
                        		}
                        		
                        		if(!oldHeaderMatch)
                        		{
                        			final RouteHeader route = balancerRunner.balancerContext.headerFactory.createRouteHeader(balancerRunner.balancerContext.addressFactory.createAddress(routeSipUri));
                        			request.addFirst(route);
                        		}
                        	}
                        }
                	}
                }
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
            if(balancerRunner.balancerContext.maxRequestNumberWithoutResponse!=null 
            		&& balancerRunner.balancerContext.maxResponseTime!=null)
            	nodeHealthcheck(ctx, nextNode);
        }
        if(logger.isDebugEnabled()) {
            logger.debug("Next node " + nextNode);
        }

        String requestMethod=request.getMethod();
        //100 Trying should be sent only if has indent really forwarding request, othewise should send 500 error
        // https://telestax.atlassian.net/browse/LB-25 improve performance by sending back 100 Trying right away to tame retransmissions.
        if(balancerRunner.balancerContext.isSendTrying)
        {
        	logger.debug("Load balancer sends 100 TRYING");
        	if(requestMethod.equals(Request.INVITE) || requestMethod.equals(Request.SUBSCRIBE) || 
        			requestMethod.equals(Request.NOTIFY) || requestMethod.equals(Request.MESSAGE) || 
        			requestMethod.equals(Request.REFER) || requestMethod.equals(Request.PUBLISH) || 
        			requestMethod.equals(Request.UPDATE)) {
        		try {
        			Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.TRYING, request);
        			RouteList routeList = ((SIPMessage)request).getRouteHeaders();
        			if (routeList != null) {
        				Route route = (Route)routeList.getFirst();
        				SipUri sipUri = (SipUri)route.getAddress().getURI();
        				if (sipUri.toString().contains("node_host") || sipUri.toString().contains("node_port")) {
        					String nodeHost = sipUri.getParameter("node_host");
        					int nodePort = Integer.parseInt(sipUri.getParameter("node_port"));
        					ViaHeader viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
        					viaHeader.setHost(nodeHost);
        					viaHeader.setPort(nodePort);
        				}
        			}
        			sipProvider.sendResponse(response);
        		} catch (SipException e) {
        			logger.error("Unexpected exception while sending TRYING", e);
        		} catch (ParseException e) {
        			logger.error("Unexpected exception while sending TRYING", e);
        		} catch (NumberFormatException e) {
        			logger.error("Unexpected exception while sending TRYING", e);
        		} catch (InvalidArgumentException e) {
        			logger.error("Unexpected exception while sending TRYING", e);
        		}
        	}
        	}
        else
        {
        	logger.debug("Load balancer do not send 100 TRYING, this option is disabled");
        }
        
        hints.serverAssignedNode = nextNode;
        if(!hints.subsequentRequest && dialogCreationMethods.contains(request.getMethod())) {
        	addLBRecordRoute(sipProvider, request, hints, version, isIpv6);
        }

        // Stateless proxies must not use internal state or ransom values when creating branch because they
        // must repeat exactly the same branches for retransmissions
        final ViaHeader via = (ViaHeader) request.getHeader(ViaHeader.NAME);
        String newBranch = via.getBranch() + callID.substring(0, Math.min(callID.length(), 5));
        // Add the via header to the top of the header list.
		ViaHeader viaHeaderExternal = null;
		ViaHeader viaHeaderInternal = null;
		String externalViaHost = null;
		String internalViaHost = null;
		if(!isIpv6)
		{
			externalViaHost = balancerRunner.balancerContext.externalViaHost;
			internalViaHost = balancerRunner.balancerContext.internalViaHost;
		}
		else
		{
			externalViaHost = balancerRunner.balancerContext.externalIpv6ViaHost;
			internalViaHost = balancerRunner.balancerContext.internalIpv6ViaHost;
		}

		if (!isRequestFromServer) {
			
			
			viaHeaderExternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
					externalViaHost,balancerRunner.balancerContext.getExternalViaPortByTransport(outerTransport,isIpv6),outerTransport, newBranch + "_" + version);

			String innerTransport = transport;
			if(balancerRunner.balancerContext.internalTransport!=null)
			{
				if(logger.isDebugEnabled()) {
					logger.debug("Set internal transport for for creating Via header : " +  balancerRunner.balancerContext.internalTransport);
				}
				innerTransport = balancerRunner.balancerContext.internalTransport;
			}
			else if (balancerRunner.balancerContext.terminateTLSTraffic) 
			{
				if(logger.isDebugEnabled()) {
	         		logger.debug("Terminate TLS traffic, isRequestFromServer: " + isRequestFromServer + 
	         				" transport before " + innerTransport);
	         	}
				
				if (innerTransport.equalsIgnoreCase(ListeningPoint.TLS))
					innerTransport = ListeningPoint.TCP;
				else if (innerTransport.equalsIgnoreCase(ListeningPointExt.WSS))
					innerTransport = ListeningPointExt.WS;
				
				if(logger.isDebugEnabled()) {
	         		logger.debug("Terminate TLS traffic, transport after " + innerTransport);
	         	}
			}

			if (balancerRunner.balancerContext.isTwoEntrypoints())
				viaHeaderInternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
								internalViaHost,balancerRunner.balancerContext.getInternalViaPortByTransport(innerTransport,isIpv6),innerTransport, newBranch + "zsd" + "_"	+ version);
			else
				viaHeaderInternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
								externalViaHost,balancerRunner.balancerContext.getExternalViaPortByTransport(innerTransport,isIpv6),innerTransport, newBranch + "zsd" + "_" + version);
		} else {
			if (balancerRunner.balancerContext.isTwoEntrypoints())
				viaHeaderInternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
								internalViaHost,balancerRunner.balancerContext.getInternalViaPortByTransport(transport,isIpv6),transport, newBranch + "zsd" + "_" + version);
			else
				viaHeaderInternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
								externalViaHost,balancerRunner.balancerContext.getExternalViaPortByTransport(transport,isIpv6),transport, newBranch + "zsd" + "_" + version);

			// https://github.com/RestComm/load-balancer/issues/67
			if (balancerRunner.balancerContext.terminateTLSTraffic) {
				if(logger.isDebugEnabled()) {
	         		logger.debug("Terminate TLS traffic, isRequestFromServer: " + isRequestFromServer + 
	         				" transport before " + outerTransport);
	         	}
			
				if (outerTransport.equalsIgnoreCase(ListeningPoint.TCP))
					outerTransport = ListeningPoint.TLS;
				else if (outerTransport.equalsIgnoreCase(ListeningPointExt.WS))
					outerTransport = ListeningPointExt.WSS;
				
				if(logger.isDebugEnabled()) {
	         		logger.debug("Terminate TLS traffic, transport after " + outerTransport);
	         	}
			}
			viaHeaderExternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
							externalViaHost,balancerRunner.balancerContext.getExternalViaPortByTransport(outerTransport,isIpv6),outerTransport, newBranch + "_" + version);
		}

        if(logger.isDebugEnabled()) {
            logger.debug("ViaHeaders will be added " + viaHeaderExternal + " and " + viaHeaderInternal);
            logger.debug("Sending the request:\n" + request + "\n on the other side");
        }
        if(getLoopbackUri(request, isIpv6) != null) {
            logger.warn("Drop. Cannot forward to loopback the following request: " + request);
            return;
        }

        try 
        {
        	if (!isRequestFromServer) {
        		request.addHeader(viaHeaderExternal);
        		if (viaHeaderInternal != null)
        			request.addHeader(viaHeaderInternal);

        		if(balancerRunner.balancerContext.terminateTLSTraffic) {
        			// https://github.com/RestComm/load-balancer/issues/67
        			// Patching the contact header for incoming requests so that requests coming out of nodes will use the non secure version
        			ContactHeader contactHeader = (ContactHeader) request.getHeader(ContactHeader.NAME);
	        		if (contactHeader != null) {
	                    final URI contactURI = contactHeader.getAddress().getURI();
	                    if(logger.isDebugEnabled()) {
	        	            logger.debug("Patching the contact header " + contactURI + 
	        	            		" so that requests coming out of nodes will use the non secure protocol");
	        	        }
	                    
	                    if(contactURI instanceof SipUri) {
	                    	((SipUri) contactURI).setTransportParam(transport);
	                    	logger.debug("new transport " + contactURI +
	        	            		" so that requests coming out of nodes will use the non secure protocol");
	                    }
	                }
        		}
        		else if(balancerRunner.balancerContext.internalTransport!=null) {
        			// https://github.com/RestComm/load-balancer/issues/67
        			// Patching the contact header for incoming requests so that requests coming out of nodes will use the non secure version
        			ContactHeader contactHeader = (ContactHeader) request.getHeader(ContactHeader.NAME);
	        		if (contactHeader != null) {
	                    final URI contactURI = contactHeader.getAddress().getURI();
	                    if(logger.isDebugEnabled()) {
	        	            logger.debug("Patching the contact header " + contactURI + 
	        	            		" so that requests coming out of nodes will use correct protocol");
	        	        }
	                    
	                    if(contactURI instanceof SipUri) {
	                    	((SipUri) contactURI).setTransportParam(outerTransport);
	                    	logger.debug("new transport " + contactURI +
	        	            		" so that requests coming out of nodes will use correct protocol");
	                    }
	                }
        		}
        		
        		if(logger.isDebugEnabled()) {
                    logger.debug("Sending the request:\n" + request);
                }
        		if (balancerRunner.balancerContext.isTwoEntrypoints())
        		{
        			if(!isIpv6)
        				balancerRunner.balancerContext.internalSipProvider.sendRequest(request);
        			else
        			{
        				balancerRunner.balancerContext.internalIpv6SipProvider.sendRequest(request);
        			}
        		}
        		else
        		{
        			if(!isIpv6)
        				balancerRunner.balancerContext.externalSipProvider.sendRequest(request);
        			else
        				balancerRunner.balancerContext.externalIpv6SipProvider.sendRequest(request);
        		}
        	} else {
        		// Check if the next hop is actually the load balancer again
        		if(viaHeaderInternal != null) request.addHeader(viaHeaderInternal); 
        		if(viaHeaderExternal != null) request.addHeader(viaHeaderExternal);
        		
        		if(balancerRunner.balancerContext.terminateTLSTraffic) {
        			// https://github.com/RestComm/load-balancer/issues/67
        			if(logger.isDebugEnabled()) {
        	            logger.debug("terminateTLSTraffic, Patching the request URI and Route Header if present");
        	        }
        			if(request.getRequestURI() instanceof SipUri) {
        				if(logger.isDebugEnabled()) {
            	            logger.debug("terminateTLSTraffic, Patching the request URI to use transport " + outerTransport);
            	        }
        				((SipUri)request.getRequestURI()).setTransportParam(outerTransport);
        			}
        			RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
        			if(routeHeader != null && routeHeader.getAddress().getURI() instanceof SipUri) {
        				if(logger.isDebugEnabled()) {
            	            logger.debug("terminateTLSTraffic, Patching the Route Header to use transport " + outerTransport);
            	        }
        				((SipUri)routeHeader.getAddress().getURI()).setTransportParam(outerTransport);
        			}
        			ContactHeader contactHeader = (ContactHeader) request.getHeader(ContactHeader.NAME);
        			if(contactHeader != null && contactHeader.getAddress().getURI() instanceof SipUri) {
        				if(logger.isDebugEnabled()) {
            	            logger.debug("terminateTLSTraffic, Patching the Contact Header to use transport " + outerTransport);
            	        }
        				((SipUri)contactHeader.getAddress().getURI()).setTransportParam(outerTransport);
        			}
        		}
        		if(logger.isDebugEnabled()) {
                    logger.debug("Sending the request:\n" + request);
                }
        		if(!isIpv6)
        			balancerRunner.balancerContext.externalSipProvider.sendRequest(request);
        		else
        			balancerRunner.balancerContext.externalIpv6SipProvider.sendRequest(request);
        	}
        }
        catch (SipException e) 
        {
        	if(request.getMethod().equalsIgnoreCase(Request.OPTIONS)&&isRequestFromServer)
        			logger.warn("Problem with sending OPTIONS to external side possibly due to closed window in broweser");
        		else
        			logger.error("Unexpected exception while forwarding the request \n" + request, e);
        	if(balancerRunner.balancerContext.isSend5xxResponse)
        		try {
        			Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.SERVICE_UNAVAILABLE, request);
					response.removeFirst(ViaHeader.NAME);
					response.removeFirst(ViaHeader.NAME);
        			if(balancerRunner.balancerContext.isSend5xxResponseReasonHeader!=null)
        			{
        				HeaderFactory hf=SipFactory.getInstance().createHeaderFactory();
        				ReasonHeader reasonHeader = hf.createReasonHeader(transport, 
        								balancerRunner.balancerContext.isSend5xxResponseSatusCode, 
        								balancerRunner.balancerContext.isSend5xxResponseReasonHeader);
        				response.setHeader(reasonHeader);
        			}
        			sipProvider.sendResponse(response);
        		} catch (SipException ex) {
        			logger.error("Unexpected exception while sending SERVICE_UNAVAILABLE", ex);
        		} catch (ParseException ex) {
        			logger.error("Unexpected exception while sending SERVICE_UNAVAILABLE", ex);
        		} catch (NumberFormatException ex) {
        			logger.error("Unexpected exception while sending SERVICE_UNAVAILABLE", ex);
        		} catch (InvalidArgumentException ex) {
        			logger.error("Unexpected exception while sending SERVICE_UNAVAILABLE", ex);
        		}
			}
   
    }

    /**
     * @param request
     * @return
     */
    private boolean securityCheck(Request request) {
        //        User-Agent: sipcli/v1.8
        //        User-Agent: friendly-scanner
        //        To: "sipvicious" <sip:100@1.1.1.1>
        //        From: "sipvicious" <sip:100@1.1.1.1>;tag=3336353363346565313363340133313330323436343236
        //        From: "1" <sip:1@87.202.36.237>;tag=3e7a78de
        Header userAgentHeader = request.getHeader("User-Agent");
        Header toHeader = request.getHeader("To");
        Header fromHeader = request.getHeader("From");

        for (String blockedValue: balancerRunner.balancerContext.blockedList){
            if(userAgentHeader != null && userAgentHeader.toString().toLowerCase().contains(blockedValue.toLowerCase())) {
                return false;
            } else if (toHeader != null && toHeader.toString().toLowerCase().contains(blockedValue.toLowerCase())) {
                return false;
            } else if (fromHeader != null && fromHeader.toString().toLowerCase().contains(blockedValue.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private RecordRouteHeader stampRecordRoute(RecordRouteHeader rrh, RouteHeaderHints hints, String transport) {
        SipURI uri = (SipURI) rrh.getAddress().getURI();
        try {
        	if(hints.serverAssignedNode != null) {
	        	if(logger.isDebugEnabled()) {
	        		logger.debug("About to stamp RecordRoute for hints:\n"+hints.serverAssignedNode.toString()+"\n");
	        	}
	            uri.setParameter(ROUTE_PARAM_NODE_HOST, hints.serverAssignedNode.getIp());
	            uri.setParameter(ROUTE_PARAM_NODE_PORT, hints.serverAssignedNode.getProperties().get(transport.toLowerCase()+"Port").toString());
	            uri.setParameter(ROUTE_PARAM_NODE_VERSION, hints.version);
        	} else {
        		if(logger.isDebugEnabled()) {
	        		logger.debug("No serverAssignedNode could be found, not stamping the record route\n");
	        	}
        	}
        } catch (ParseException e) {
            logger.warn("Problem adding rrh" ,e);
        }
        return rrh;
    }

    private void addTwoRecordRoutes(Request request, RecordRouteHeader first,RecordRouteHeader second, RouteHeaderHints hints, String transport) {
        if(logger.isDebugEnabled()) {
            logger.debug("adding Record Router Header :" + first);
        }

        
        try
        {
        	request.addFirst(stampRecordRoute((RecordRouteHeader) first.clone(), hints, transport));        	
        }
        catch(Exception ex)
        {
        	//should not occure
        }
        
        if(logger.isDebugEnabled()) {
            logger.debug("adding Record Router Header :" + second);
        }

        try
        {
        	request.addFirst(stampRecordRoute((RecordRouteHeader) second.clone(), hints, transport));        	
        }
        catch(Exception ex)
        {
        	//should not occure
        }
    }

    /**
     * @param sipProvider
     * @param request
     * @param hints 
     * @throws ParseException
     */
    private void addLBRecordRoute(SipProvider sipProvider, Request request, RouteHeaderHints hints, String version, boolean isIpv6)
            throws ParseException {	
    	if(logger.isDebugEnabled()) {
    		if(!isIpv6)
    			logger.debug("adding Record Router Header :" + balancerRunner.balancerContext.activeExternalHeader);
    		else
    			logger.debug("adding IPv6 Record Router Header :" + Arrays.toString(balancerRunner.balancerContext.activeExternalIpv6Header));
        }
                
		String transport = ((ViaHeader) request.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();

		if (sipProvider.equals(balancerRunner.balancerContext.externalSipProvider) || sipProvider.equals(balancerRunner.balancerContext.externalIpv6SipProvider)) {
			
			int transportIndex = 0;
			int internalTransportIndex = 0;
			if(balancerRunner.balancerContext.internalTransport!=null)
			{
				if(logger.isDebugEnabled()) {
					logger.debug("Set internal transport for adding Record Route): " +  balancerRunner.balancerContext.internalTransport);
				}
				String currExternalTransport = transport.toUpperCase();
				switch (currExternalTransport) 
				{
	            case ListeningPoint.UDP:  transportIndex = UDP;
	            break;
	            case ListeningPoint.TCP:  transportIndex = TCP;
        		break;
	            case ListeningPoint.TLS:  transportIndex = TLS;
        		break;
	            case ListeningPointExt.WS:  transportIndex = WS;
        		break;
	            case ListeningPointExt.WSS:  transportIndex = WSS;
        		break;
				}
				String currInternalTransport = balancerRunner.balancerContext.internalTransport.toUpperCase();
				switch (currInternalTransport) 
				{
	            case ListeningPoint.UDP:  internalTransportIndex = UDP;
	            break;
	            case ListeningPoint.TCP:  internalTransportIndex = TCP;
        		break;
	            case ListeningPoint.TLS:  internalTransportIndex = TLS;
        		break;
	            case ListeningPointExt.WS:  internalTransportIndex = WS;
        		break;
	            case ListeningPointExt.WSS:  internalTransportIndex = WSS;
        		break;
				}
			}else
			{
				transportIndex = TLS;
				internalTransportIndex = TLS;
				if (balancerRunner.balancerContext.terminateTLSTraffic) {
					internalTransportIndex = TCP;
				}

				if (transport.equalsIgnoreCase(ListeningPoint.UDP)) {
					transportIndex = UDP;
					internalTransportIndex = UDP;
				} else if (transport.equalsIgnoreCase(ListeningPoint.TCP)) {
					transportIndex = TCP;
					internalTransportIndex = TCP;
				} else if (transport.equalsIgnoreCase(ListeningPointExt.WS)) {
					transportIndex = WS;
					internalTransportIndex = WS;
				} else if (transport.equalsIgnoreCase(ListeningPointExt.WSS)) {
					transportIndex = WSS;
					if (balancerRunner.balancerContext.terminateTLSTraffic) {
						internalTransportIndex = WS;
					} else {
						internalTransportIndex = WSS;
					}
				}
			}
			// comes from client
			if(!isIpv6)
				addTwoRecordRoutes(request,balancerRunner.balancerContext.activeExternalHeader[transportIndex],
						balancerRunner.balancerContext.activeInternalHeader[internalTransportIndex],
						hints, transport);
			else
				addTwoRecordRoutes(request,balancerRunner.balancerContext.activeExternalIpv6Header[transportIndex],
						balancerRunner.balancerContext.activeInternalIpv6Header[internalTransportIndex],
						hints, transport);
		} else {
			int transportIndex = TLS;
			int externalTransportIndex = TLS;
			String externalTransport = transport;
			URI requestURI = ((Request) request).getRequestURI();
			if (requestURI.isSipURI()) {
				if (((SipUri) requestURI).getTransportParam() != null)
					externalTransport = ((SipUri) requestURI).getTransportParam();
			}

			if (transport.equalsIgnoreCase(ListeningPoint.UDP))
				transportIndex = UDP;
			else if (transport.equalsIgnoreCase(ListeningPoint.TCP))
				transportIndex = TCP;
			else if (transport.equalsIgnoreCase(ListeningPointExt.WS))
				transportIndex = WS;
			else if (transport.equalsIgnoreCase(ListeningPointExt.WSS))
				transportIndex = WSS;

			if (externalTransport.equalsIgnoreCase(ListeningPoint.UDP))
				externalTransportIndex = UDP;
			else if (externalTransport.equalsIgnoreCase(ListeningPoint.TCP))
				externalTransportIndex = TCP;
			else if (externalTransport.equalsIgnoreCase(ListeningPointExt.WS))
				externalTransportIndex = WS;
			else if (externalTransport.equalsIgnoreCase(ListeningPointExt.WSS))
				externalTransportIndex = WSS;

			// comes from app server
			if(!isIpv6)
				addTwoRecordRoutes(	request,
					balancerRunner.balancerContext.activeInternalHeader[transportIndex],
					balancerRunner.balancerContext.activeExternalHeader[externalTransportIndex],
					hints, transport);
			else
				addTwoRecordRoutes(	request,
						balancerRunner.balancerContext.activeInternalIpv6Header[transportIndex],
						balancerRunner.balancerContext.activeExternalIpv6Header[externalTransportIndex],
						hints, transport);
				
			if(logger.isInfoEnabled()) {
				logger.info("Will patch Request : \"" + request.getRequestURI()	+ "\" to provide public IP address for the RecordRoute header");
			}
			patchSipMessageForNAT(request,isIpv6);
        }
    }

    /**
     * This will check if in the route header there is information on which node from the cluster send the request.
     * If the request is not received from the cluster, this information will not be present. 
     * @param routeHeader the route header to check
     * @return the corresponding Sip Node
     */
    private Node checkRouteHeaderForSipNode(SipURI routeSipUri) {
        Node node = null;
        String hostNode = routeSipUri.getParameter(ROUTE_PARAM_NODE_HOST);
        String hostPort = routeSipUri.getParameter(ROUTE_PARAM_NODE_PORT);
        String hostVersion = routeSipUri.getParameter(ROUTE_PARAM_NODE_VERSION);
        if(hostNode != null && hostPort != null) {
            int port = Integer.parseInt(hostPort);
            String transport = routeSipUri.getTransportParam();
            if(transport == null) transport = ListeningPoint.UDP;
            node = register.getNode(hostNode, port, transport, hostVersion);                       
        }
        return node;
    }

	private String getRouteHeadersMeantForLB(Request request ,boolean isIpv6) {
		@SuppressWarnings("unchecked")
		ListIterator<RouteHeader> headers = request
				.getHeaders(RouteHeader.NAME);
		RouteHeader routeHeader = null;
		if (headers.hasNext())
			routeHeader = headers.next();

		if (routeHeader != null) {
			SipURI routeUri = (SipURI) routeHeader.getAddress().getURI();
			String transport = ((ViaHeader) request.getHeader("Via"))
					.getTransport();
			if (routeUri.getTransportParam() != null)
				transport = routeUri.getTransportParam();

			Boolean isRouteHeaderExternal = isHeaderExternal(routeUri.getHost(), routeUri.getPort(),transport,isIpv6);
			
			if (!isRouteHeaderExternal) {
				routeHeader = null;
				if (headers.hasNext())
					routeHeader = headers.next();

				if (routeHeader != null) {
					routeUri = (SipURI) routeHeader.getAddress().getURI();
					transport = ((ViaHeader) request.getHeader("Via"))
							.getTransport();
					if (routeUri.getTransportParam() != null)
						transport = routeUri.getTransportParam();

						isRouteHeaderExternal=isHeaderExternal(routeUri.getHost(), routeUri.getPort(),transport,isIpv6);
					
					if (!isRouteHeaderExternal) 
						return transport;					
				}
				
				return transport;
			}
		}

		return null;
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
    private RouteHeaderHints removeRouteHeadersMeantForLB(Request request, boolean isIpv6) {
        if(logger.isDebugEnabled()) {
            logger.debug("Checking if there is any route headers meant for the LB to remove...");
        }
        Node node = null;
        String callVersion = null;
        int numberOfRemovedRouteHeaders = 0;
        if(balancerRunner.balancerContext.matchingHostnameForRoute!=null)
        {
        	RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
        	if(routeHeader!=null)
        	{
        		if(logger.isDebugEnabled())
        		{
        			logger.debug("Matching host name for route is : " + balancerRunner.balancerContext.matchingHostnameForRoute);
        			logger.debug("Matching host name and subdomain: " + balancerRunner.balancerContext.isFilterSubdomain);
        		}
        		if(!balancerRunner.balancerContext.isFilterSubdomain)
        		{
        			if(((SipURI)routeHeader.getAddress().getURI()).getHost().equals(balancerRunner.balancerContext.matchingHostnameForRoute))
        				request.removeFirst(RouteHeader.NAME);
        		}
        		else
        		{
        			if(((SipURI)routeHeader.getAddress().getURI()).getHost().endsWith("."+balancerRunner.balancerContext.matchingHostnameForRoute))
        				request.removeFirst(RouteHeader.NAME);
        		}
        			
        	}
        }

        //Removing first routeHeader if it is for the sip balancer
        RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
        if(routeHeader != null) {
            SipURI routeUri = (SipURI)routeHeader.getAddress().getURI();
            callVersion = routeUri.getParameter(ROUTE_PARAM_NODE_VERSION);

			String transport = ((ViaHeader) request.getHeader("Via")).getTransport();
			if (routeUri.getTransportParam() != null)
				transport = routeUri.getTransportParam();

			if (!isHeaderExternal(routeUri.getHost(), routeUri.getPort(),	transport, isIpv6)) {
				if (logger.isDebugEnabled()) {
					logger.debug("this route header is for the LB removing it "	+ routeUri);
				}

                numberOfRemovedRouteHeaders ++;

                request.removeFirst(RouteHeader.NAME);
                routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
                //since we used double record routing we may have 2 routes corresponding to us here
                // for ACK and BYE from caller for example
                node = checkRouteHeaderForSipNode(routeUri);
                if(routeHeader != null) {

                    routeUri = (SipURI)routeHeader.getAddress().getURI();
					transport = ((ViaHeader) request.getHeader("Via")).getTransport();
					if (routeUri.getTransportParam() != null)
						transport = routeUri.getTransportParam();
						
					if (!isHeaderExternal(routeUri.getHost(),routeUri.getPort(), transport, isIpv6)) {
						if (logger.isDebugEnabled()) {
							logger.debug("this route header is for the LB removing it "	+ routeUri);
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

								transport = ((ViaHeader) request.getHeader("Via")).getTransport();
								if (u.getTransportParam() != null)
									transport = u.getTransportParam();

								if (!isHeaderExternal(u.getHost(),u.getPort(), transport, isIpv6)) {
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
    private boolean isHeaderExternal(String host, int port,String transport, boolean isIpv6) 
    {  

    	if(!isIpv6)
    	{
    		if (host.equalsIgnoreCase(balancerRunner.balancerContext.externalHost))
    		{
    			if(balancerRunner.balancerContext.getExternalPortByTransport(transport,false)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getExternalLoadBalancerPortByTransport(transport,false)==port)
    				return false;
    		}

    		if(host.equalsIgnoreCase(balancerRunner.balancerContext.internalHost))
    		{
    			if(balancerRunner.balancerContext.getInternalPortByTransport(transport,false)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getInternalLoadBalancerPortByTransport(transport,false)==port)
    				return false;
    		}
		
    		if(balancerRunner.balancerContext.externalIpLoadBalancerAddresses!=null&&
    				balancerRunner.balancerContext.externalIpLoadBalancerAddresses.contains(host))
    		{
    			if(balancerRunner.balancerContext.getExternalPortByTransport(transport,false)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getExternalLoadBalancerPortByTransport(transport,false)==port)
    				return false;
    		}
		
    		if(balancerRunner.balancerContext.internalIpLoadBalancerAddresses!=null&&
    				balancerRunner.balancerContext.internalIpLoadBalancerAddresses.contains(host))
    		{
    			if(balancerRunner.balancerContext.getInternalPortByTransport(transport,false)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getInternalLoadBalancerPortByTransport(transport,false)==port)
    				return false;
    		}
		
    		if(host.equalsIgnoreCase(balancerRunner.balancerContext.publicIP))
    		{
    			if(balancerRunner.balancerContext.getExternalPortByTransport(transport,false)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getExternalLoadBalancerPortByTransport(transport,false)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getInternalPortByTransport(transport,false)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getInternalLoadBalancerPortByTransport(transport,false)==port)
    				return false;
    		}

    		return true;
    	}
    	else
    	{
    		String cleanHost=host;
    		if(cleanHost.startsWith("["))
    			cleanHost=cleanHost.substring(1);
    		
    		if(cleanHost.endsWith("]"))
    			cleanHost=cleanHost.substring(0,cleanHost.length()-1);
    		
    		InetAddress address=null;
    		try
    		{
    			address=InetAddress.getByName(cleanHost);
    		}
    		catch(UnknownHostException ex)
    		{
    			
    		}
    		
    		if(address==null)
    			return false;
    		
    		if (address.equals(balancerRunner.balancerContext.externalIpv6HostAddress))
    		{    			
    			if(balancerRunner.balancerContext.getExternalPortByTransport(transport,true)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getExternalLoadBalancerPortByTransport(transport,true)==port)
    				return false;
    		}
    			

    		if(address.equals(balancerRunner.balancerContext.internalIpv6HostAddress))
    		{
    			if(balancerRunner.balancerContext.getInternalPortByTransport(transport,true)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getInternalLoadBalancerPortByTransport(transport,true)==port)
    				return false;
    		}
    		
    		if(balancerRunner.balancerContext.externalIpv6LoadBalancerAddressHosts.contains(address))
    		{
    			if(balancerRunner.balancerContext.getExternalPortByTransport(transport,true)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getExternalLoadBalancerPortByTransport(transport,true)==port)
    				return false;
    		}
    		
    		if(balancerRunner.balancerContext.internalIpv6LoadBalancerAddressHosts.contains(address))
    		{
    			if(balancerRunner.balancerContext.getInternalPortByTransport(transport,true)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getInternalLoadBalancerPortByTransport(transport,true)==port)
    				return false;
    		}
    		
    		if(address.equals(balancerRunner.balancerContext.publicIPv6Host))
    		{
    			if(balancerRunner.balancerContext.getExternalPortByTransport(transport,true)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getExternalLoadBalancerPortByTransport(transport,true)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getInternalPortByTransport(transport,true)==port)
    				return false;
			
    			if(balancerRunner.balancerContext.getInternalLoadBalancerPortByTransport(transport,true)==port)
    				return false;
    		}

		return true;
    	}
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
    	BalancerAppContent content=(BalancerAppContent)responseEvent.getSource();
    	boolean isIpv6 = content.isIpv6();
        SipProvider sipProvider = content.getProvider();
        
        Response originalResponse = responseEvent.getResponse();
        if(logger.isDebugEnabled()) {
            logger.debug("got response :\n" + originalResponse);
        }

        updateStats(originalResponse);

        final Response response = (Response) originalResponse; 
        Node senderNode = getSenderNode(response);
        if(senderNode != null) {
        	if(logger.isDebugEnabled()) {
    			logger.debug("Updating Timestamp of sendernode: " + senderNode);
    		}
            senderNode.updateTimerStamp();
        }

        // Topmost via headers is me. As it is response to external request
        ViaHeader viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);

        String branch = viaHeader.getBranch();
        int versionDelimiter = branch.lastIndexOf('_');
        String version = branch.substring(versionDelimiter + 1);

        InvocationContext ctx = balancerRunner.getInvocationContext(version);

        if(viaHeader!=null && !isHeaderExternal(viaHeader.getHost(), viaHeader.getPort(), viaHeader.getTransport(),isIpv6)) {
            response.removeFirst(ViaHeader.NAME);
        }
        
        viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
        String transport=viaHeader.getTransport();
        
        if(viaHeader!=null && !isHeaderExternal(viaHeader.getHost(), viaHeader.getPort(), viaHeader.getTransport(),isIpv6)) {
            response.removeFirst(ViaHeader.NAME);
        }
        
       
        
        boolean fromServer = false;
        if(balancerRunner.balancerContext.isTwoEntrypoints()) {
        	if(!isIpv6)
        		fromServer = sipProvider.equals(balancerRunner.balancerContext.internalSipProvider);
        	else
        		fromServer = sipProvider.equals(balancerRunner.balancerContext.internalIpv6SipProvider);
            if(logger.isDebugEnabled()) {
            	if(!isIpv6)
            		logger.debug("fromServer : "+ fromServer + ", sipProvider " + sipProvider + ", internalSipProvider " + balancerRunner.balancerContext.internalSipProvider);
            	else
            		logger.debug("fromServer : "+ fromServer + ", sipProvider " + sipProvider + ", internalIpv6SipProvider " + balancerRunner.balancerContext.internalIpv6SipProvider);
            		
    		}
        } else {
            fromServer = senderNode == null;
            if(logger.isDebugEnabled()) {
    			logger.debug("fromServer : "+ fromServer + ", senderNode " + senderNode);
    		}
        }
        //removes rport and received from last Via header because of NEXMO patches it
        //only for external responses
        if(balancerRunner.balancerContext.isUseWithNexmo&&!fromServer)
        {
        	viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
        	if(viaHeader!=null) 
        	{
        		if(logger.isDebugEnabled())
        			logger.debug("We are going to remove rport and received parametres from :" + viaHeader + " from external response");
        		response.removeFirst(ViaHeader.NAME);
        		viaHeader.removeParameter("rport");
        		viaHeader.removeParameter("received");
			
        		try {
        			response.addFirst(viaHeader);
        		} catch (NullPointerException | SipException e) {
        			e.printStackTrace();
        		}
        		if(logger.isDebugEnabled())
        			logger.debug("After removing :" + response);
        	}
        }

        if(fromServer) {
        	if(senderNode!=null&&senderNode.getIp()!=null)
        	{
        		if(balancerRunner.balancerContext.maxRequestNumberWithoutResponse!=null
      			&& balancerRunner.balancerContext.maxResponseTime!=null)
        		{
        			senderNode.setLastTimeResponse(System.currentTimeMillis());
        			senderNode.setRequestNumberWithoutResponse(0);
        		}
        		mediaFailureDetection(response, ctx, senderNode);
        	}
            /*
			if("true".equals(balancerRunner.balancerContext.properties.getProperty("removeNodesOn500Response")) && response.getStatusCode() == 500) {
				// If the server is broken remove it from the list and try another one with the next retransmission
				if(!(sourceNode instanceof ExtraServerNode)) {
					if(balancerRunner.balancerContext.nodes.size()>1) {
						balancerRunner.balancerContext.nodes.remove(sourceNode);
						balancerRunner.balancerContext.balancerAlgorithm.nodeRemoved(sourceNode);
					}
				}
			} 
        	*/
        	String publicIp = null;
        	if(!isIpv6)
        		publicIp = balancerRunner.balancerContext.publicIP;
        	else
        		publicIp = balancerRunner.balancerContext.publicIPv6;
        	if(publicIp != null && publicIp.trim().length() > 0) {
        		if(logger.isDebugEnabled()) {
        			logger.debug("Will add Record-Route header to response with public IP Address: "+publicIp);
        		}
                patchSipMessageForNAT(response, isIpv6);
            }
            // https://github.com/RestComm/load-balancer/issues/45 Adding sender node for the algorithm to be available
        	((ResponseExt)response).setApplicationData(senderNode);
        	
            ctx.balancerAlgorithm.processInternalResponse(response,isIpv6);
            try {	
                if(logger.isDebugEnabled()) {
                    logger.debug("from server sending response externally " + response);
                }
                if(!isIpv6)
                	balancerRunner.balancerContext.externalSipProvider.sendResponse(response);
                else
                	balancerRunner.balancerContext.externalIpv6SipProvider.sendResponse(response);

            } catch (Exception ex) {
                logger.error("Unexpected exception while forwarding the response \n" + response, ex);
            }
        } else {
        	try {
                SIPMessage message = (SIPMessage)response;

                String initialRemoteAddr = message.getPeerPacketSourceAddress().getHostAddress();
                String initialRemotePort = String.valueOf(message.getPeerPacketSourcePort());

                Header remoteAddrHeader = null;
                Header remotePortHeader = null;
                try {
                	HeaderFactory hf=SipFactory.getInstance().createHeaderFactory();
                    remoteAddrHeader = hf.createHeader("X-Sip-Balancer-InitialRemoteAddr", initialRemoteAddr);
                    remotePortHeader = hf.createHeader("X-Sip-Balancer-InitialRemotePort", initialRemotePort);
                } catch (PeerUnavailableException e) {
                    logger.error("Unexpected exception while creating custom headers for REGISTER message ", e);
                } catch (ParseException e) {
                    logger.error("Unexpected exception while creating custom headers for REGISTER message ", e);
                }
                if (remoteAddrHeader != null)
                    response.addHeader(remoteAddrHeader);
                if (remotePortHeader != null)
                    response.addHeader(remotePortHeader);
                                
                if(balancerRunner.balancerContext.isTwoEntrypoints()) {
            	    ctx.balancerAlgorithm.processExternalResponse(response,isIpv6);
                   
                    if(logger.isDebugEnabled()) {
                        logger.debug("two entry points: from external sending response " + response);
                    }
                    if(!isIpv6)
                    	balancerRunner.balancerContext.internalSipProvider.sendResponse(response);
                    else
                    	balancerRunner.balancerContext.internalIpv6SipProvider.sendResponse(response);
                } else {
                	if(!comesFromInternalNode(response,ctx,initialRemoteAddr,message.getPeerPacketSourcePort(),transport,isIpv6))
                		ctx.balancerAlgorithm.processExternalResponse(response,isIpv6);
                	else
                		ctx.balancerAlgorithm.processInternalResponse(response,isIpv6);
                	
                    if(logger.isDebugEnabled()) {
                        logger.debug("one entry point: from external sending response " + response);
                    }
                    if(!isIpv6)
                    	balancerRunner.balancerContext.externalSipProvider.sendResponse(response);
                    else
                    	balancerRunner.balancerContext.externalIpv6SipProvider.sendResponse(response);
                }
            } catch (Exception ex) {
                logger.error("Unexpected exception while forwarding the response \n" + response, ex);
            }
        }
    }

    private void mediaFailureDetection(Response response, InvocationContext ctx, Node node)
    {
    	Boolean isIpV6=LbUtils.isValidInet6Address(node.getIp());        	        
    	KeySip keySip = new KeySip(node,isIpV6);
    	if(balancerRunner.balancerContext.responsesStatusCodeNodeRemoval.contains(response.getStatusCode()))
    		// adding null check for https://github.com/RestComm/load-balancer/issues/83
    		if(ctx.sipNodeMap(isIpV6).get(keySip) != null && 
    				ctx.sipNodeMap(isIpV6).get(keySip).getAndIncrementFailCounter()>balancerRunner.balancerContext.maxNumberResponsesWithError) {
					logger.error("mediaFailureDetection on keysip " + keySip + ", removing node " + node);
					ctx.sipNodeMap(isIpV6).get(keySip).setBad(true);
					ctx.balancerAlgorithm.nodeRemoved(node);
    		}
    }
    
    private void nodeHealthcheck(InvocationContext ctx, Node node)
    {
    	Boolean isIpV6=LbUtils.isValidInet6Address(node.getIp());        	        
    	KeySip keySip = new KeySip(node,isIpV6);
      	if(node.getRequestNumberWithoutResponse().incrementAndGet() > balancerRunner.balancerContext.maxRequestNumberWithoutResponse
      			&& balancerRunner.balancerContext.maxResponseTime < System.currentTimeMillis()-
      			node.getLastTimeResponse().get())
      	{
      		logger.error("health check failed for " + keySip + ", removing node " + node);
      		ctx.sipNodeMap(isIpV6).get(keySip).setBad(true);
			ctx.balancerAlgorithm.nodeRemoved(node);
      	}
    }

    //need to verify that comes from external in case of single leg
    protected Boolean comesFromInternalNode(Response externalResponse,InvocationContext ctx,String host,Integer port,String transport,Boolean isIpV6)
	{
		boolean found = false;
		if(host!=null && port!=null)
		{
			if(ctx.sipNodeMap(isIpV6).containsKey(new KeySip(host, port,isIpV6)))
				found = true;
//			for(Node node : ctx.nodes) {
//				if(node.getIp().equals(host)) {
//					if(port.equals(node.getProperties().get(transport+"Port"))) {
//						found = true;
//						break;
//					}
//				}
//			}
		}
		return found;
	}
    
    /**
     * Patch Response for NAT Environment where the Load Balancer runs on a private IP but UAs need to know the LB public IP to 
     * send back subsequent requests to it.
     * @param sipMessage the response to patch
     * @throws PeerUnavailableException 
     * @throws ParseException 
     */
    public void patchSipMessageForNAT(javax.sip.message.Message sipMessage, boolean isIpv6) {
        //Need to patch the response so the subsequent requests are directly correctly at the public IP Address of the LB
        // Useful for NAT environment such as Amazon EC2
    	String currentPublicIp = null;
    	if(!isIpv6)
    		currentPublicIp = balancerRunner.balancerContext.publicIP;
    	else
    		currentPublicIp = balancerRunner.balancerContext.publicIPv6;
        if (currentPublicIp != null && !currentPublicIp.isEmpty()) {
        	int [] ports = new int[5];
//        	int udpPort = 0;
//    		int tcpPort = 0;
//    		int tlsPort = 0;
//    		int wsPort = 0;
//    		int wssPort = 0;
        	if(!isIpv6)
        	{
        		for(int i = 0; i < 5; i++)
        			ports[i] = balancerRunner.balancerContext.externalPorts[i];
//        		udpPort = balancerRunner.balancerContext.externalUdpPort;
//        		tcpPort = balancerRunner.balancerContext.externalTcpPort;
//        		tlsPort = balancerRunner.balancerContext.externalTlsPort;
//        		wsPort = balancerRunner.balancerContext.externalWsPort;
//        		wssPort = balancerRunner.balancerContext.externalWssPort;
        	}
        	else
        	{
        		for(int i = 0; i < 5; i++)
        			ports[i] = balancerRunner.balancerContext.externalIpv6Ports[i];
//        		udpPort = balancerRunner.balancerContext.externalIpv6UdpPort;
//        		tcpPort = balancerRunner.balancerContext.externalIpv6TcpPort;
//        		tlsPort = balancerRunner.balancerContext.externalIpv6TlsPort;
//        		wsPort = balancerRunner.balancerContext.externalIpv6WsPort;
//        		wssPort = balancerRunner.balancerContext.externalIpv6WssPort;
        	}

			String transport = null;
			if (sipMessage instanceof Response)
				transport = ((ViaHeader) sipMessage.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
			else {
				URI requestURI = ((Request) sipMessage).getRequestURI();
				transport = ((ViaHeader) sipMessage.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
				if (requestURI.isSipURI()) {
					if (((SipUri) requestURI).getTransportParam() != null)
						transport = ((SipUri) requestURI).getTransportParam();
				}
			}

            String privateIp = balancerRunner.balancerContext.host;
            @SuppressWarnings("unchecked")
			ListIterator<RecordRouteHeader> recordRouteHeaderList = sipMessage.getHeaders(RecordRouteHeader.NAME);
            
            try {
                HeaderFactory headerFactory = SipFactory.getInstance().createHeaderFactory(); 
                Header contactHeader = null;
                
                if (!recordRouteHeaderList.hasNext()) {
                	if(logger.isDebugEnabled()) {
                		logger.debug("Record Route header list is empty");
                	}
                }

                while (recordRouteHeaderList.hasNext()) {
                    RecordRouteHeader recordRouteHeader = (RecordRouteHeader) recordRouteHeaderList.next();
                    
                    if(logger.isDebugEnabled()) {
                    	logger.debug("About to check Record-Route header: "+recordRouteHeader.toString());
                    }
                    if (((SipURI)recordRouteHeader.getAddress().getURI()).getHost().equals(privateIp)) { //If this RecordRoute header is from LB
                    	for(int port : ports)
                    		if(((SipURI)recordRouteHeader.getAddress().getURI()).getPort()==port)
                    		{ // And if the port is the external Port
                    			SipURI sipURI = (SipURI) recordRouteHeader.getAddress().getURI();
                    			sipURI.setHost(currentPublicIp);                            
                    		}                        
                    } else {
                    	if(logger.isDebugEnabled()) {
                    		logger.debug("Didn't patched the Record-Route because ip address is not the private one: "+((SipURI)recordRouteHeader.getAddress().getURI()).getHost());
                    	}
                    }
                }

                if (sipMessage.getHeader(ContactHeader.NAME) != null) {                	
                    final String displayedName = ((ContactHeader)sipMessage.getHeader("Contact")).getAddress().getDisplayName();
                    
                    int currPort=balancerRunner.balancerContext.getExternalPortByTransport(transport,isIpv6);
                    
                    if (displayedName != null && !displayedName.isEmpty()) {
                        final String contactURI = "sip:"+displayedName+"@"+currentPublicIp+":"+currPort;
                        contactHeader = headerFactory.createHeader("Contact", contactURI);
                        ((ContactHeader)contactHeader).getAddress().setDisplayName(displayedName);
                    } else {
                        final String contactURI = "sip:"+currentPublicIp+":"+currPort;
                        contactHeader = headerFactory.createHeader("Contact", contactURI);
                    }
                    if (contactHeader != null) {
                        sipMessage.removeFirst("Contact");
                        sipMessage.addHeader(contactHeader);
                    }
                }

                if(logger.isDebugEnabled() && contactHeader != null) {
                		logger.debug("Patched the Contact header with : "+contactHeader.toString());
                }
            } catch (PeerUnavailableException peerUnavailableException) {
                logger.error("Unexpected exception while forwarding the response \n" + sipMessage, peerUnavailableException);
            } catch (ParseException parseException) {
                logger.error("Unexpected exception while forwarding the response \n" + sipMessage, parseException);
            } catch (NullPointerException e) {
                logger.error("Unexpected exception while forwarding the response \n" + sipMessage, e);
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
     * @return the responsesProcessed
     */
    public long getNumberOfResponsesProcessed() {
        return balancerRunner.balancerContext.responsesProcessed.get();
    }
    
    /**
     * @return the bytesTransfered
     */
    public long getNumberOfBytesTransferred()
    {
    	return balancerRunner.balancerContext.bytesTransferred.get();
    }

    /**
     * @return the requestsProcessedByMethod
     */
    public long getRequestsProcessedByMethod(String method) {
        AtomicLong requestsProcessed = balancerRunner.balancerContext.requestsProcessedByMethod.get(method);
        if(requestsProcessed != null) {
            return requestsProcessed.get();
        }
        return 0;
    }

    /**
     * @return the responsesProcessedByStatusCode
     */
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
    private String getTransportById(int i)
    {
    	String transport = null;
		switch (i) 
		{
        	case 0:  transport = ListeningPoint.UDP;
        	break;
        	case 1:  transport = ListeningPoint.TCP;
        	break;
        	case 2:  transport = ListeningPoint.TLS;
        	break;
        	case 3:  transport = ListeningPointExt.WS;
        	break;
        	case 4:  transport = ListeningPointExt.WSS;
        	break;
		}
		return transport;
    }
    private int getIndexByTransport(String transport)
    {
        int index = 0;
        switch (transport) {
        case ListeningPoint.UDP:  index = UDP;
                 break;
        case ListeningPoint.TCP:  index = TCP;
                 break;
        case ListeningPoint.TLS:  index = TLS;
                 break;
        case ListeningPointExt.WS:  index = WS;
                 break;
        case ListeningPointExt.WSS:  index = WSS;
                 break;
        }
       return index;
    }
    private void setLoadBalacerHostsPorts()
    {
        balancerRunner.balancerContext.host = balancerRunner.balancerContext.lbConfig.getCommonConfiguration().getHost();
        balancerRunner.balancerContext.internalHost = (balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getHost()==null||
        		balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getHost().equals(""))?
        		balancerRunner.balancerContext.host:balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getHost(); 
        balancerRunner.balancerContext.externalHost = (balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getHost()==null||
        		balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getHost().equals(""))?
        		balancerRunner.balancerContext.host:balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getHost();

        balancerRunner.balancerContext.ipv6Host = balancerRunner.balancerContext.lbConfig.getCommonConfiguration().getIpv6Host();
        balancerRunner.balancerContext.externalIpv6Host = (balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getIpv6Host()==null||
				balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getIpv6Host().equals(""))?
				balancerRunner.balancerContext.ipv6Host:balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getIpv6Host();
        balancerRunner.balancerContext.internalIpv6Host = (balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getIpv6Host()==null||
				balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getIpv6Host().equals(""))?
				balancerRunner.balancerContext.ipv6Host:balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getIpv6Host();
        
        if(balancerRunner.balancerContext.externalIpv6Host!=null)
        {
        	try
        	{
        		balancerRunner.balancerContext.externalIpv6HostAddress=InetAddress.getByName(balancerRunner.balancerContext.externalIpv6Host);	
        	}
        	catch(UnknownHostException ex)
        	{
        	}
        }
        if(balancerRunner.balancerContext.internalIpv6Host!=null)
        {
        	try
        	{
        		balancerRunner.balancerContext.internalIpv6HostAddress=InetAddress.getByName(balancerRunner.balancerContext.internalIpv6Host);	
        	}
        	catch(UnknownHostException ex)
        	{
        	}
        }

        balancerRunner.balancerContext.externalPorts = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getPorts();
        balancerRunner.balancerContext.externalIpv6Ports = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getIpv6Ports();
        balancerRunner.balancerContext.internalPorts = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getPorts();
        balancerRunner.balancerContext.internalIpv6Ports = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getIpv6Ports();
        
        balancerRunner.balancerContext.externalIpLoadBalancerAddresses = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getIpLoadBalancerAddress();
        balancerRunner.balancerContext.internalIpLoadBalancerAddresses = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getIpLoadBalancerAddress();
        balancerRunner.balancerContext.externalIpv6LoadBalancerAddresses = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getIpv6LoadBalancerAddress();
        balancerRunner.balancerContext.internalIpv6LoadBalancerAddresses = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getIpv6LoadBalancerAddress();

        if(balancerRunner.balancerContext.externalIpv6LoadBalancerAddresses!=null)
        {
        	try
        	{
        		for(String externalIpv6LoadBalancerAddress : balancerRunner.balancerContext.externalIpv6LoadBalancerAddresses)
        			balancerRunner.balancerContext.externalIpv6LoadBalancerAddressHosts.add(InetAddress.getByName(externalIpv6LoadBalancerAddress));	
        	}
        	catch(UnknownHostException ex)
        	{
        	}
        }
        
        if(balancerRunner.balancerContext.internalIpv6LoadBalancerAddresses!=null)
        {
        	try
        	{	
        		for(String internalIpv6LoadBalancerAddress : balancerRunner.balancerContext.internalIpv6LoadBalancerAddresses)
        			balancerRunner.balancerContext.internalIpv6LoadBalancerAddressHosts.add(InetAddress.getByName(internalIpv6LoadBalancerAddress));	
        	}
        	catch(UnknownHostException ex)
        	{
        	}
        }
        
        balancerRunner.balancerContext.externalIpLoadBalancerPorts = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getIPLoadBalancerPorts();
        balancerRunner.balancerContext.externalIpv6LoadBalancerPorts = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getExternalLegConfiguration().getIpv6LoadBalancerPorts();
        balancerRunner.balancerContext.internalIpLoadBalancerPorts = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getIPLoadBalancerPorts();
        balancerRunner.balancerContext.internalIpv6LoadBalancerPorts = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalLegConfiguration().getIpv6LoadBalancerPorts();
        
		// https://github.com/RestComm/load-balancer/issues/43 don't use host by default if public-ip is not set or it will result in contact header responses being badly patched
		
		balancerRunner.balancerContext.publicIP = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getPublicIp();
		balancerRunner.balancerContext.publicIPv6 = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getPublicIpv6();
		if(balancerRunner.balancerContext.publicIPv6!=null&&!balancerRunner.balancerContext.publicIPv6.equals(""))
        {
        	try
        	{
        		balancerRunner.balancerContext.publicIPv6Host=InetAddress.getByName(balancerRunner.balancerContext.publicIPv6);	
        	}
        	catch(UnknownHostException ex)
        	{
        		
        	}
        }
		//checking of settings
		if(balancerRunner.balancerContext.isTwoEntrypoints()) {
        	for(int i = 0; i < 5; i++)
        		if(balancerRunner.balancerContext.externalIpLoadBalancerPorts[i] > 0) {
        			if(balancerRunner.balancerContext.internalIpLoadBalancerPorts[i] <=0) {
        				throw new RuntimeException("External IP load balancer specified "+getTransportById(i)+" port, but not internal load balancer "+getTransportById(i)+" port");
        			}                
        		}
        	for(int i = 0; i < 5; i++)
        		if(balancerRunner.balancerContext.externalIpv6LoadBalancerPorts[i] > 0) {
        			if(balancerRunner.balancerContext.internalIpv6LoadBalancerPorts[i] <= 0) {
        				throw new RuntimeException("External IP load balancer specified "+getTransportById(i)+" port, but not internal load balancer "+getTransportById(i)+" port");
        			}                
        		}
        }
        int [] emptyArray = new int[5];
        if(balancerRunner.balancerContext.externalIpLoadBalancerAddresses != null)
        	if(Arrays.equals(balancerRunner.balancerContext.externalIpLoadBalancerPorts,emptyArray))
        		throw new RuntimeException("External load balancer address is specified, but none externalIpLoadBalancerPort ");

        if(balancerRunner.balancerContext.externalIpv6LoadBalancerAddresses != null)
            if(Arrays.equals(balancerRunner.balancerContext.externalIpv6LoadBalancerPorts,emptyArray)) 
                throw new RuntimeException("External load balancer ipv6 address is specified, but none externalIpv6LoadBalancerPort ");

        if(balancerRunner.balancerContext.internalIpLoadBalancerAddresses != null)
            if(Arrays.equals(balancerRunner.balancerContext.internalIpLoadBalancerPorts,emptyArray)) 
                throw new RuntimeException("Internal load balancer address is specified, but none internalIpLoadBalancerPort");

        if(balancerRunner.balancerContext.internalIpv6LoadBalancerAddresses != null)
            if(Arrays.equals(balancerRunner.balancerContext.internalIpv6LoadBalancerPorts,emptyArray)) 
                throw new RuntimeException("Internal load balancer ipv6 address is specified, but none internalIpv6LoadBalancerPort");
    }
    
    private void setExtraServerNodes(String extraServerNodesString)
    {
        ArrayList<Node> extraServerNodes = new ArrayList<Node>();
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
            HashMap<String, String> properties = new HashMap<String, String>();
            properties.put("udpPort",""+ extraServerPorts[q]);
            properties.put("tcpPort",""+ extraServerPorts[q]);
            properties.put("httpPort", "808"+q);
            properties.put("version","0");
            extraServerNode.setProperties(properties);
            extraServerNodes.add(extraServerNode);
            logger.info("Extra Server: " + extraServerAddresses[q] + ":" + extraServerPorts[q]);
        }
        if(balancerRunner.balancerContext.lbConfig.getSipConfiguration().isPerformanceTestingMode()){
            register.handlePingInRegister(extraServerNodes);
            logger.info("Extra Servers registered as active nodes!");
        }
    }
    
    private void createRecordRouteHeaders(ArrayList<ListeningPoint> listeningPoints, RecordRouteHeader[] activeHeaders) throws ParseException
    {
    	for (ListeningPoint listeningPoint : listeningPoints)
        {
    	  SipURI internalLocalUri = balancerRunner.balancerContext.addressFactory.createSipURI(null, listeningPoint.getIPAddress());
          internalLocalUri.setPort(listeningPoint.getPort());
          internalLocalUri.setTransportParam(listeningPoint.getTransport());
          //See RFC 3261 19.1.1 for lr parameter
          internalLocalUri.setLrParam();
          Address internalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(internalLocalUri);
          internalLocalAddress.setURI(internalLocalUri);
          if(logger.isDebugEnabled()) {
              logger.debug("adding internal Record Router Header :"+internalLocalAddress);
          }
          activeHeaders[getIndexByTransport(listeningPoint.getTransport())] = balancerRunner.balancerContext.headerFactory.createRecordRouteHeader(internalLocalAddress);     
        }
    }

    private void createRecordRouteHeaders(int[] ports, String address, RecordRouteHeader[] activeHeaders) throws ParseException
    {
         	for(int i = 0; i < 5; i++)
         		if(ports[i] > 0)
         		{
         			SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory.createSipURI(null, address);
                     ipLbSipUri.setPort(ports[i]);
                     ipLbSipUri.setTransportParam(getTransportById(i));
                     ipLbSipUri.setLrParam();
                     Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                     ipLbAdress.setURI(ipLbSipUri);
                     activeHeaders[i] = balancerRunner.balancerContext.headerFactory.createRecordRouteHeader(ipLbAdress);
         		}
    }
    
    private void printConfigInfo()
    {
    	StringBuilder portsInfo = new StringBuilder("Sip Balancer started on external address " + balancerRunner.balancerContext.externalHost);
    	for(int i = 0; i < 5; i++)
    		portsInfo.append(", external "+getTransportById(i)+" port : " + balancerRunner.balancerContext.externalPorts[i]);
    	for(int i = 0; i < 5; i++)
    		portsInfo.append(", internal "+getTransportById(i)+" port : " + balancerRunner.balancerContext.internalPorts[i]);
    	logger.info(portsInfo);
    	if(balancerRunner.balancerContext.externalIpv6Host!=null)
    	{
    		portsInfo = new StringBuilder("Sip Balancer started on external IPv6 address " + balancerRunner.balancerContext.externalIpv6Host);
    		for(int i = 0; i < 5; i++)
    			portsInfo.append(", external ipv6 "+getTransportById(i)+" port : " + balancerRunner.balancerContext.externalIpv6Ports[i]);
    		for(int i = 0; i < 5; i++)
    			portsInfo.append(", internal ipv6 "+getTransportById(i)+" port : " + balancerRunner.balancerContext.internalIpv6Ports[i]);
    		logger.info(portsInfo);
    	}
    }
    
    private void setSipLoadBalancerProperty() {
    	balancerRunner.balancerContext.isSendTrying = balancerRunner.balancerContext.lbConfig.getSipConfiguration().isSendTrying();
		balancerRunner.balancerContext.isSend5xxResponse = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getIsSend5xxResponse();
		balancerRunner.balancerContext.isSend5xxResponseReasonHeader = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getIsSend5xxResponseReasonHeader();
		balancerRunner.balancerContext.isSend5xxResponseSatusCode = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getIsSend5xxResponseSatusCode();
    	balancerRunner.balancerContext.sipHeaderAffinityKey = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getAlgorithmConfiguration().getSipHeaderAffinityKey();
    	balancerRunner.balancerContext.sipHeaderAffinityFallbackKey = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getAlgorithmConfiguration().getSipHeaderAffinityFallbackKey();
    	balancerRunner.balancerContext.sipHeaderAffinityKeyExclusionPattern = null;
    	if(balancerRunner.balancerContext.lbConfig.getSipConfiguration().getAlgorithmConfiguration().getSipHeaderAffinityKeyExclusionPattern() != null && !balancerRunner.balancerContext.lbConfig.getSipConfiguration().getAlgorithmConfiguration().getSipHeaderAffinityKeyExclusionPattern().trim().isEmpty()) {
    		balancerRunner.balancerContext.sipHeaderAffinityKeyExclusionPattern = Pattern.compile(balancerRunner.balancerContext.lbConfig.getSipConfiguration().getAlgorithmConfiguration().getSipHeaderAffinityKeyExclusionPattern());
    	}
    	balancerRunner.balancerContext.isUseWithNexmo = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getIsUseWithNexmo();
    	balancerRunner.balancerContext.responsesStatusCodeNodeRemoval = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getResponsesStatusCodeNodeRemoval();
    	balancerRunner.balancerContext.maxNumberResponsesWithError = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getMaxNumberResponsesWithError();
    	balancerRunner.balancerContext.matchingHostnameForRoute = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getMatchingHostnameForRoute();
    	balancerRunner.balancerContext.isFilterSubdomain = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getIsFilterSubdomain();
    	balancerRunner.balancerContext.internalTransport = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getInternalTransport();
    	balancerRunner.balancerContext.blockedList = new ArrayList<String>(Arrays.asList(balancerRunner.balancerContext.lbConfig.getSipConfiguration().getBlockedValues().split(",")));
    	balancerRunner.balancerContext.maxRequestNumberWithoutResponse = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getMaxRequestNumberWithoutResponse();
    	balancerRunner.balancerContext.maxResponseTime = balancerRunner.balancerContext.lbConfig.getSipConfiguration().getMaxResponseTime();
	}
    
    private void setViaHostsPorts()
    {
    	if (balancerRunner.balancerContext.lbConfig.getSipConfiguration().isUseIpLoadBalancerAddressInViaHeaders()) 
		{
    		if(balancerRunner.balancerContext.externalIpLoadBalancerAddresses!=null)
    			balancerRunner.balancerContext.externalViaHost = balancerRunner.balancerContext.externalIpLoadBalancerAddresses.get(0);
    		if(balancerRunner.balancerContext.internalIpLoadBalancerAddresses!=null)
    			balancerRunner.balancerContext.internalViaHost = balancerRunner.balancerContext.internalIpLoadBalancerAddresses.get(0);
			balancerRunner.balancerContext.externalViaPorts = balancerRunner.balancerContext.externalIpLoadBalancerPorts;
			balancerRunner.balancerContext.internalViaPorts = balancerRunner.balancerContext.internalIpLoadBalancerPorts;
			if(balancerRunner.balancerContext.externalIpv6LoadBalancerAddresses!=null)
				balancerRunner.balancerContext.externalIpv6ViaHost = balancerRunner.balancerContext.externalIpv6LoadBalancerAddresses.get(0);
			if(balancerRunner.balancerContext.internalIpv6LoadBalancerAddresses!=null)
				balancerRunner.balancerContext.internalIpv6ViaHost = balancerRunner.balancerContext.internalIpv6LoadBalancerAddresses.get(0);
			balancerRunner.balancerContext.externalIpv6ViaPorts = balancerRunner.balancerContext.externalIpv6LoadBalancerPorts;
			balancerRunner.balancerContext.internalIpv6ViaPorts = balancerRunner.balancerContext.internalIpv6LoadBalancerPorts;
		} 
		else 
		{
			balancerRunner.balancerContext.externalViaHost = balancerRunner.balancerContext.externalHost;
			balancerRunner.balancerContext.internalViaHost = balancerRunner.balancerContext.internalHost;
			balancerRunner.balancerContext.externalViaPorts = balancerRunner.balancerContext.externalPorts;
			balancerRunner.balancerContext.internalViaPorts = balancerRunner.balancerContext.internalPorts;
			balancerRunner.balancerContext.externalIpv6ViaHost = balancerRunner.balancerContext.externalIpv6Host;
			balancerRunner.balancerContext.internalIpv6ViaHost = balancerRunner.balancerContext.internalIpv6Host;
			balancerRunner.balancerContext.externalIpv6ViaPorts = balancerRunner.balancerContext.externalIpv6Ports;
			balancerRunner.balancerContext.internalIpv6ViaPorts = balancerRunner.balancerContext.internalIpv6Ports;
		}
    }
    
    private void createSipStack() throws PeerUnavailableException
    {
    	SipFactory sipFactory = null;
        try {
            sipFactory = SipFactory.getInstance();
            String pathName = balancerRunner.balancerContext.lbConfig.getSipStackConfiguration().getSipStackProperies().getProperty("pathName", "org.mobicents.ext");
            logger.info("SIP Stack pathName " + pathName);
            sipFactory.setPathName(pathName);
            balancerRunner.balancerContext.sipStack = (SipStackImpl) sipFactory.createSipStack(balancerRunner.balancerContext.lbConfig.getSipStackConfiguration().getSipStackProperies());

        } catch (PeerUnavailableException pue) {
            throw new IllegalStateException("Cant create stack due to["+pue.getMessage()+"]", pue);
        }
        balancerRunner.balancerContext.headerFactory = sipFactory.createHeaderFactory();
        if(balancerRunner.balancerContext.lbConfig.getSipConfiguration().isUsePrettyEncoding())
            ((HeaderFactoryImpl)balancerRunner.balancerContext.headerFactory).setPrettyEncoding(true);
        balancerRunner.balancerContext.addressFactory = sipFactory.createAddressFactory();
        balancerRunner.balancerContext.messageFactory = sipFactory.createMessageFactory();
        
    }
    
    private SipProvider createSipProviders(ArrayList<ListeningPoint> lps) throws ObjectInUseException, TransportAlreadySupportedException, TooManyListenersException
    {
    	SipProvider sipProvider = balancerRunner.balancerContext.sipStack.createSipProvider(lps.remove(0));
        for (ListeningPoint lp : lps)
        	sipProvider.addListeningPoint(lp);
        sipProvider.addSipListener(this);
        return sipProvider;
    }
    
    private void createListeningPoints(int [] ports, String host, ArrayList<ListeningPoint> lps) throws TransportNotSupportedException, InvalidArgumentException
    {
    	for(int i = 0; i < 5 ; i++)
    		if(ports[i] > 0)
        		lps.add(balancerRunner.balancerContext.sipStack.createListeningPoint(host, ports[i], getTransportById(i)));
    }
}
