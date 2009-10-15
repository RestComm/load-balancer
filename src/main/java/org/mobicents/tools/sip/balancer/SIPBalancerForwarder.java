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

import gov.nist.javax.sip.header.RecordRoute;
import gov.nist.javax.sip.header.SIPHeader;

import java.text.ParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
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
import javax.sip.header.CallIdHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RecordRouteHeader;
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

	protected static final HashSet<String> dialogCreationMethods=new HashSet<String>(2);
    
    static{
    	dialogCreationMethods.add(Request.INVITE);
    	dialogCreationMethods.add(Request.SUBSCRIBE);
    }      

	public NodeRegister register;
	
    static BalancerContext balancerContext = new BalancerContext();
    
    protected BalancerAlgorithm balancerAlgorithm;
	
	public SIPBalancerForwarder(Properties properties, NodeRegister register) throws IllegalStateException{
		super();
		balancerContext.properties = properties;
		this.register = register;		
	}

	public void start() {
		
		SipFactory sipFactory = null;
		balancerContext.sipStack = null;
        
		balancerContext.myHost = balancerContext.properties.getProperty("host");        
		balancerContext.myPort = Integer.parseInt(balancerContext.properties.getProperty("internalPort"));
		balancerContext.myExternalPort = Integer.parseInt(balancerContext.properties.getProperty("externalPort"));
		balancerContext.externalIpLoadBalancerAddress = balancerContext.properties.getProperty("externalIpLoadBalancerAddress");
		balancerContext.internalIpLoadBalancerAddress = balancerContext.properties.getProperty("internalIpLoadBalancerAddress");
		
        try {
            // Create SipStack object
        	sipFactory = SipFactory.getInstance();
	        sipFactory.setPathName("gov.nist");
	        balancerContext.sipStack = sipFactory.createSipStack(balancerContext.properties);
           
        } catch (PeerUnavailableException pue) {
            // could not find
            // gov.nist.jain.protocol.ip.sip.SipStackImpl
            // in the classpath
            throw new IllegalStateException("Cant create stack due to["+pue.getMessage()+"]", pue);
        }

        try {
        	balancerContext.headerFactory = sipFactory.createHeaderFactory();
        	balancerContext.addressFactory = sipFactory.createAddressFactory();
        	balancerContext.messageFactory = sipFactory.createMessageFactory();

            ListeningPoint internalLp = balancerContext.sipStack.createListeningPoint(balancerContext.myHost, balancerContext.myPort, ListeningPoint.UDP);
            balancerContext.internalSipProvider = balancerContext.sipStack.createSipProvider(internalLp);
            balancerContext.internalSipProvider.addSipListener(this);

            ListeningPoint externalLp = balancerContext.sipStack.createListeningPoint(balancerContext.myHost, balancerContext.myExternalPort, ListeningPoint.UDP);
            balancerContext.externalSipProvider = balancerContext.sipStack.createSipProvider(externalLp);
            balancerContext.externalSipProvider.addSipListener(this);

            //Creating the Record Route headers on startup since they can't be changed at runtime and this will avoid the overhead of creating them
            //for each request
            
            // Record route the invite so the bye comes to me. FIXME: Add check, on reINVITE we wont add ourselvses twice
    		SipURI sipUri = balancerContext.addressFactory
    		        .createSipURI(null, internalLp.getIPAddress());
    		sipUri.setPort(internalLp.getPort());
    		//See RFC 3261 19.1.1 for lr parameter
    		sipUri.setLrParam();
    		Address address = balancerContext.addressFactory.createAddress(sipUri);
    		address.setURI(sipUri);

    		balancerContext.internalRecordRouteHeader = balancerContext.headerFactory
    		.createRecordRouteHeader(address);            

    		//We need to use double record (better option than record route rewriting) routing otherwise it is impossible :
    		//a) to forward BYE from the callee side to the caller
    		//b) to support different transports		
    		SipURI sendingSipUri = balancerContext.addressFactory
    		        .createSipURI(null, externalLp.getIPAddress());
    		sendingSipUri.setPort(externalLp.getPort());
    		//See RFC 3261 19.1.1 for lr parameter
    		sendingSipUri.setLrParam();
    		Address sendingAddress = balancerContext.addressFactory.createAddress(sendingSipUri);
    		sendingAddress.setURI(sendingSipUri);
    		if(logger.isLoggable(Level.FINEST)) {
    			logger.finest("adding Record Router Header :"+sendingAddress);
    		}                    
    		balancerContext.externalRecordRouteHeader = balancerContext.headerFactory
    		        .createRecordRouteHeader(sendingAddress);    
    		
    		if(balancerContext.externalIpLoadBalancerAddress != null) {
    			SipURI ipLbSipUri = balancerContext.addressFactory
    			.createSipURI(null, balancerContext.externalIpLoadBalancerAddress);
    			String portString = balancerContext.properties.getProperty("externalIpLoadBalancerPort", "5060");
    			balancerContext.externalLoadBalancerPort = Integer.parseInt(portString);
    			ipLbSipUri.setPort(balancerContext.externalLoadBalancerPort);
    			ipLbSipUri.setLrParam();
    			Address ipLbAdress = balancerContext.addressFactory.createAddress(ipLbSipUri);
    			address.setURI(ipLbSipUri);
    			balancerContext.externalIpBalancerRecordRouteHeader = balancerContext.headerFactory
    			.createRecordRouteHeader(ipLbAdress);
    		}
    		
    		if(balancerContext.internalIpLoadBalancerAddress != null) {
    			SipURI ipLbSipUri = balancerContext.addressFactory
    			.createSipURI(null, balancerContext.internalIpLoadBalancerAddress);
    			String portString = balancerContext.properties.getProperty("internalIpLoadBalancerPort", "5060");
    			balancerContext.internalLoadBalancerPort = Integer.parseInt(portString);
    			ipLbSipUri.setPort(balancerContext.internalLoadBalancerPort);
    			ipLbSipUri.setLrParam();
    			Address ipLbAdress = balancerContext.addressFactory.createAddress(ipLbSipUri);
    			address.setURI(ipLbSipUri);
    			balancerContext.internalIpBalancerRecordRouteHeader = balancerContext.headerFactory
    			.createRecordRouteHeader(ipLbAdress);
    		}
    		balancerContext.activeExternalHeader = balancerContext.externalIpBalancerRecordRouteHeader != null ?
    				balancerContext.externalIpBalancerRecordRouteHeader : balancerContext.externalRecordRouteHeader;
    		balancerContext.activeInternalHeader = balancerContext.internalIpBalancerRecordRouteHeader != null ?
    				balancerContext.internalIpBalancerRecordRouteHeader : balancerContext.internalRecordRouteHeader;
    		
    		balancerContext.sipStack.start();
        } catch (Exception ex) {
        	throw new IllegalStateException("Can't create sip objects and lps due to["+ex.getMessage()+"]", ex);
        }
        if(logger.isLoggable(Level.INFO)) {
        	logger.info("Sip Balancer started on address " + balancerContext.myHost + ", external port : " + balancerContext.myExternalPort + ", port : "+ balancerContext.myPort);
        }              
	}
	
	public void stop() {
		Iterator<SipProvider> sipProviderIterator = balancerContext.sipStack.getSipProviders();
		try{
			while (sipProviderIterator.hasNext()) {
				SipProvider sipProvider = sipProviderIterator.next();
				ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
				for (ListeningPoint listeningPoint : listeningPoints) {
					if(logger.isLoggable(Level.INFO)) {
						logger.info("Removing the following Listening Point " + listeningPoint);
					}
					sipProvider.removeListeningPoint(listeningPoint);
					balancerContext.sipStack.deleteListeningPoint(listeningPoint);
				}
				if(logger.isLoggable(Level.INFO)) {
					logger.info("Removing the sip provider");
				}
				sipProvider.removeSipListener(this);	
				balancerContext.sipStack.deleteSipProvider(sipProvider);			
			}
		} catch (Exception e) {
			throw new IllegalStateException("Cant remove the listening points or sip providers", e);
		}
		
		balancerContext.sipStack.stop();
		balancerContext.sipStack = null;
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
		final Request originalRequest = requestEvent.getRequest();
         
		final Request request = originalRequest;
		final String requestMethod = request.getMethod();
		try {	
			updateStats(request);
            forwardRequest(sipProvider,
						originalRequest, request);          						
        } catch (Throwable throwable) {
            logger.log(Level.SEVERE, "Unexpected exception while forwarding the request " + request, throwable);
            if(!Request.ACK.equalsIgnoreCase(requestMethod)) {
	            try {
	            	Response response = balancerContext.messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR,originalRequest);			
	                sipProvider.sendResponse(response);	
	            } catch (Exception e) {
	            	logger.log(Level.SEVERE, "Unexpected exception while trying to send the error response for this " + request, e);
				}
            }
        }
	}

	private void updateStats(Message message) {
		if(message instanceof Request) {
			balancerContext.requestsProcessed.incrementAndGet();
		} else {
			balancerContext.responsesProcessed.incrementAndGet();
		}		
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
			SipProvider sipProvider, Request originalRequest,
			Request request)
			throws ParseException, InvalidArgumentException, SipException,
			TransactionUnavailableException {
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("got request:\n"+request);
		}
		
		final boolean isCancel = Request.CANCEL.equals(request.getMethod());
		
		if(!isCancel) {
			decreaseMaxForwardsHeader(sipProvider, request);
		}
		
		if(dialogCreationMethods.contains(request.getMethod())) {
			addLBRecordRoute(sipProvider, request);
		}
		
		final String callID = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
		
		removeRouteHeadersMeantForLB(request);
		
		SIPNode nextNode = this.balancerAlgorithm.processRequest(sipProvider, request);
		
		if(nextNode == null && sipProvider.equals(balancerContext.externalSipProvider)) {
			throw new RuntimeException("No nodes available");
		}
		
		// Stateless proxies must not use internal state or ransom values when creating branch because they
		// must repeat exactly the same branches for retransmissions
		final ViaHeader via = (ViaHeader) request.getHeader(ViaHeader.NAME);
		String newBranch = via.getBranch() + callID.substring(0, Math.min(callID.length(), 5));
		// Add the via header to the top of the header list.
		final ViaHeader viaHeader = balancerContext.headerFactory.createViaHeader(
				balancerContext.myHost, balancerContext.myPort, ListeningPoint.UDP, newBranch);
		request.addHeader(viaHeader); 

		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("ViaHeader added " + viaHeader);
		}		

		SipProvider sendingSipProvider = balancerContext.internalSipProvider;
		if(sipProvider.equals(balancerContext.internalSipProvider)) {
			sendingSipProvider = balancerContext.externalSipProvider;
		}
		if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("Sending the request:\n" + request + "\n on the other side");
    	}
		sendingSipProvider.sendRequest(request);
	}

	/**
	 * @param sipProvider
	 * @param request
	 * @throws ParseException
	 */
	private void addLBRecordRoute(SipProvider sipProvider, Request request)
	throws ParseException {				
		if(sipProvider.equals(balancerContext.externalSipProvider)) {
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding Record Router Header :" + balancerContext.activeExternalHeader);
			}
			request.addHeader(balancerContext.activeExternalHeader);

			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding Record Router Header :" + balancerContext.activeInternalHeader);
			}
			request.addHeader(balancerContext.activeInternalHeader);
		} else {
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding Record Router Header :" + balancerContext.activeInternalHeader);
			}
			request.addHeader(balancerContext.activeInternalHeader);

			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding Record Router Header :" + balancerContext.activeExternalHeader);
			}
			request.addHeader(balancerContext.activeExternalHeader);			
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
	private SIPNode removeRouteHeadersMeantForLB(Request request) {
		if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("Checking if there is any route headers meant for the LB to remove...");
    	}
		SIPNode node = null; 
		//Removing first routeHeader if it is for the sip balancer
		RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
		if(routeHeader != null) {
		    SipURI routeUri = (SipURI)routeHeader.getAddress().getURI();
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
		            }
		        }
		    }	                
		}
		if(node !=null) {
			String callId = ((SIPHeader) request.getHeader("Call-ID")).getValue();
			balancerAlgorithm.assignToNode(callId, node);
			if(logger.isLoggable(Level.FINEST)) {
	    		logger.finest("Following node information has been found in one of the route Headers " + node);
	    	}
		}
		return node;
	}
	
	/**
	 * Check if the sip uri is meant for the LB same host and same port
	 * @param sipUri sip Uri to check 
	 * @return
	 */
	private boolean isRouteHeaderExternal(String host, int port) {
		 //FIXME check against a list of host we may have too and add transport
		if(host.equalsIgnoreCase(balancerContext.myHost) && (port == balancerContext.myExternalPort || port == balancerContext.myPort)) {
			return false;
		}
		if((host.equalsIgnoreCase(balancerContext.externalIpLoadBalancerAddress) && port == balancerContext.externalLoadBalancerPort)) {
			return false;
		}
		if((host.equalsIgnoreCase(balancerContext.internalIpLoadBalancerAddress) && port == balancerContext.internalLoadBalancerPort)) {
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
			maxForwardsHeader = balancerContext.headerFactory.createMaxForwardsHeader(70);
			request.addHeader(maxForwardsHeader);
		} else {
			if(maxForwardsHeader.getMaxForwards() - 1 > 0) {
				maxForwardsHeader.setMaxForwards(maxForwardsHeader.getMaxForwards() - 1);
			} else {
				//Max forward header equals to 0, thus sending too many hops response
				Response response = balancerContext.messageFactory.createResponse
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
		
		// Topmost via header is me. As it is response to external request
		final ViaHeader viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
		
		if(viaHeader!=null && !isRouteHeaderExternal(viaHeader.getHost(), viaHeader.getPort())) {
			response.removeFirst(ViaHeader.NAME);
		}

		try {	
			// retransmission case : we forward on the other sip provider
			if(sipProvider.equals(balancerContext.externalSipProvider)) {
				balancerContext.internalSipProvider.sendResponse(response);
			} else {
				balancerContext.externalSipProvider.sendResponse(response);
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, "Unexpected exception while forwarding the response \n" + response, ex);
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
		return balancerContext.requestsProcessed.get();
	}
	
	/**
	 * @return the requestsProcessed
	 */
	public long getNumberOfResponsesProcessed() {
		return balancerContext.responsesProcessed.get();
	}

	public BalancerContext getBalancerAlgorithmContext() {
		return balancerContext;
	}

	public void setBalancerAlgorithmContext(
			BalancerContext balancerAlgorithmContext) {
		this.balancerContext = balancerAlgorithmContext;
	}

	public BalancerAlgorithm getBalancerAlgorithm() {
		return balancerAlgorithm;
	}

	public void setBalancerAlgorithm(BalancerAlgorithm balancerAlgorithm) {
		this.balancerAlgorithm = balancerAlgorithm;
	}
}
