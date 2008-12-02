package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.stack.SIPServerTransaction;

import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sip.ClientTransaction;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.Transaction;
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
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

	private static final HashSet<String> dialogCreationMethods=new HashSet<String>(2);
    
    static{
    	dialogCreationMethods.add(Request.INVITE);
    	dialogCreationMethods.add(Request.SUBSCRIBE);
    }      
	/*
	 * Those parameters is to indicate to the SIP Load Balancer, from which node comes from the request
	 * so that it can stick the Call Id to this node and correctly route the subsequent requests. 
	 */
	public static final String ROUTE_PARAM_NODE_HOST = "node_host";

	public static final String ROUTE_PARAM_NODE_PORT = "node_port";
	
	private SipProvider internalSipProvider;

    private SipProvider externalSipProvider;

    private String myHost;

    private int myPort;

    private int myExternalPort;

    private static AddressFactory addressFactory;
    private static HeaderFactory headerFactory;
    private static MessageFactory messageFactory;

    private SipStack sipStack;
	
	private NodeRegister register;

	private Properties properties;  
	
	private RecordRouteHeader internalRecordRouteHeader;
	private RecordRouteHeader externalRecordRouteHeader;
	
	public SIPBalancerForwarder(Properties properties, NodeRegister register) throws IllegalStateException{
		super();
		this.properties = properties;
		this.register=register;		
	}

	public void start() {
		
		SipFactory sipFactory = null;
        sipStack = null;
        
        this.myHost = properties.getProperty("host");        
		this.myPort = Integer.parseInt(properties.getProperty("internalPort"));
		this.myExternalPort = Integer.parseInt(properties.getProperty("externalPort"));
        
        try {
            // Create SipStack object
        	sipFactory = SipFactory.getInstance();
	        sipFactory.setPathName("gov.nist");
            sipStack = sipFactory.createSipStack(properties);
           
        } catch (PeerUnavailableException pue) {
            // could not find
            // gov.nist.jain.protocol.ip.sip.SipStackImpl
            // in the classpath
            throw new IllegalStateException("Cant create stack due to["+pue.getMessage()+"]", pue);
        }

        try {
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();

            ListeningPoint internalLp = sipStack.createListeningPoint(myHost, myPort, ListeningPoint.UDP);
            internalSipProvider = sipStack.createSipProvider(internalLp);
            internalSipProvider.addSipListener(this);

            ListeningPoint externalLp = sipStack.createListeningPoint(myHost, myExternalPort, ListeningPoint.UDP);
            externalSipProvider = sipStack.createSipProvider(externalLp);
            externalSipProvider.addSipListener(this);

            //Creating the Record Route headers on startup since they can't be changed at runtime and this will avoid the overhead of creating them
            //for each request
            
            // Record route the invite so the bye comes to me. FIXME: Add check, on reINVITE we wont add ourselvses twice
    		SipURI sipUri = addressFactory
    		        .createSipURI(null, internalLp.getIPAddress());
    		sipUri.setPort(internalLp.getPort());
    		//See RFC 3261 19.1.1 for lr parameter
    		sipUri.setLrParam();
    		Address address = addressFactory.createAddress(sipUri);
    		address.setURI(sipUri);
    		                    
    		internalRecordRouteHeader = headerFactory
    		        .createRecordRouteHeader(address);                    
    		//We need to use double record (better option than record route rewriting) routing otherwise it is impossible :
    		//a) to forward BYE from the callee side to the caller
    		//b) to support different transports		
    		SipURI sendingSipUri = addressFactory
    		        .createSipURI(null, externalLp.getIPAddress());
    		sendingSipUri.setPort(externalLp.getPort());
    		//See RFC 3261 19.1.1 for lr parameter
    		sendingSipUri.setLrParam();
    		Address sendingAddress = addressFactory.createAddress(sendingSipUri);
    		sendingAddress.setURI(sendingSipUri);
    		if(logger.isLoggable(Level.FINEST)) {
    			logger.finest("adding Record Router Header :"+sendingAddress);
    		}                    
    		externalRecordRouteHeader = headerFactory
    		        .createRecordRouteHeader(sendingAddress);    
            
            sipStack.start();
        } catch (Exception ex) {
        	throw new IllegalStateException("Can't create sip objects and lps due to["+ex.getMessage()+"]", ex);
        }
        if(logger.isLoggable(Level.INFO)) {
        	logger.info("Sip Balancer started on address " + myHost + ", external port : " + myExternalPort + ", port : "+ myPort);
        }              
	}
	
	public void stop() {
		Iterator<SipProvider> sipProviderIterator = sipStack.getSipProviders();
		try{
			while (sipProviderIterator.hasNext()) {
				SipProvider sipProvider = sipProviderIterator.next();
				ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
				for (ListeningPoint listeningPoint : listeningPoints) {
					if(logger.isLoggable(Level.INFO)) {
						logger.info("Removing the following Listening Point " + listeningPoint);
					}
					sipProvider.removeListeningPoint(listeningPoint);
					sipStack.deleteListeningPoint(listeningPoint);
				}
				if(logger.isLoggable(Level.INFO)) {
					logger.info("Removing the sip provider");
				}
				sipProvider.removeSipListener(this);	
				sipStack.deleteSipProvider(sipProvider);			
			}
		} catch (Exception e) {
			throw new IllegalStateException("Cant remove the listening points or sip providers", e);
		}
		
		sipStack.stop();
		sipStack = null;
		if(logger.isLoggable(Level.INFO)) {
			logger.info("Sip Balancer stopped");
		}
	}
	
	public void processDialogTerminated(
			DialogTerminatedEvent dialogTerminatedEvent) {
		// We wont see those
		register.unStickSessionFromNode(dialogTerminatedEvent.getDialog().getCallId().getCallId());
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
		SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request originalRequest = requestEvent.getRequest();
        ServerTransaction serverTransaction = requestEvent.getServerTransaction();
         
        Request request = (Request) originalRequest.clone();
        
        if(logger.isLoggable(Level.FINEST)) {
        	logger.finest("transaction " + serverTransaction);
        	logger.finest("dialog " + requestEvent.getDialog());
        }
		try {
			if(!Request.ACK.equals(request.getMethod()) && serverTransaction == null) {
	         	serverTransaction = sipProvider.getNewServerTransaction(originalRequest);
	        }			
			//send a Trying to stop retransmissions
			if(Request.INVITE.equals(request.getMethod())) {
				Response tryingResponse = messageFactory.createResponse
	        		(Response.TRYING,request);
				serverTransaction.sendResponse(tryingResponse);
			}
			
            if (dialogCreationMethods.contains(request.getMethod())) {
                processDialogCreatingRequest(sipProvider,
						originalRequest, serverTransaction, request);
            } else {
            	processNonDialogCreatingRequest(sipProvider,
						originalRequest, serverTransaction, request);
            }
		} catch (TransactionAlreadyExistsException taex ) {
			// Already processed this request so just return.
			return;				
        } catch (Throwable throwable) {
            logger.log(Level.SEVERE, "Unexpected exception while forwarding the request " + request, throwable);
            if(!Request.ACK.equalsIgnoreCase(request.getMethod())) {
	            try {
	            	Response response=messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR,originalRequest);			
	                if (serverTransaction !=null) {
	                	serverTransaction.sendResponse(response);
	                } else { 
	                	if (sipProvider == this.externalSipProvider) {
	                		externalSipProvider.sendResponse(response);	
	                	} else {
	                		internalSipProvider.sendResponse(response);	
	                	}
	                }	
	            } catch (Exception e) {
	            	logger.log(Level.SEVERE, "Unexpected exception while trying to send the error response for this " + request, e);
				}
            }
        }
	}

	/**
	 * @param serverTransaction
	 * @param request
	 * @throws ParseException
	 * @throws InvalidArgumentException
	 * @throws TransactionUnavailableException
	 * @throws SipException
	 */
	private void processNonDialogCreatingRequest(SipProvider sipProvider, Request originalRequest,
			ServerTransaction serverTransaction, Request request) throws ParseException, InvalidArgumentException,
			TransactionUnavailableException, SipException {		
		// CANCEL is hop by hop, so replying to the CANCEL by generating a 200 OK and sending a CANCEL
		if (request.getMethod().equals(Request.CANCEL)) {
			processCancel(sipProvider, originalRequest, serverTransaction);
			return;
		}
		if(logger.isLoggable(Level.FINEST)) {
        	logger.finest("got NON dialog creating request:\n " + request);
        }
		decreaseMaxForwardsHeader(sipProvider, request);
		
		SIPNode sipNode = removeRouteHeadersMeantForLB(request);   
		if (!request.getMethod().equals(Request.ACK)) {			
		    // Check if the node is still alive for subsequent requests
			RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
			Map<String, String> parameters = null;
			boolean isSIPNodePresent = true;
			String callID = ((CallID) request.getHeader(CallID.NAME)).getCallId();			
			if(routeHeader != null ) {			
				SipURI route = ((SipURI)routeHeader.getAddress().getURI());				
				isSIPNodePresent = register.isSIPNodePresent(route.getHost(), route.getPort(), route.getTransportParam());
				if(!isSIPNodePresent) {
					if(logger.isLoggable(Level.FINEST)) {
			    		logger.finest("node " + route + " is not alive anymore, picking another one ");
			    	}
					parameters = new HashMap<String, String>();
					Iterator<String> routeParametersIt = route.getParameterNames();
					while(routeParametersIt.hasNext()) {
						String routeParameterName = routeParametersIt.next();
						String routeParameterValue = route.getParameter(routeParameterName);
						parameters.put(routeParameterName, routeParameterValue);
					}					
					register.unStickSessionFromNode(callID);
					request.removeFirst(RouteHeader.NAME);
					addRouteToNode(originalRequest, serverTransaction, request, parameters);
				}				
			} else {
				SIPNode node = register.getGluedNode(callID);
				// checking if the gleued node is still alive, if not we pick a new node
				if(node == null || !register.isSIPNodePresent(node.getIp(), node.getPort(), node.getTransports()[0])) {
					if(logger.isLoggable(Level.FINEST)) {
			    		logger.finest("node " + node + " is not alive anymore, picking another one ");
			    	}
					register.unStickSessionFromNode(callID);
					node = register.stickSessionToNode(callID, null);					
				} 
				//we change the request uri only if the request is coming from the external side
				if(sipProvider == externalSipProvider && node != null) {
					if(logger.isLoggable(Level.FINEST)) {
			    		logger.finest("request coming from external, setting the request URI to the one of the node " + node);
			    	}	
					SipURI requestURI = (SipURI)request.getRequestURI();
					requestURI.setHost(node.getIp());
					requestURI.setPort(node.getPort());
					requestURI.setTransportParam(node.getTransports()[0]);
				} else if(sipProvider == externalSipProvider) {
					//No node present yet to forward the request to, thus sending 500 final error response
					Response response = messageFactory.createResponse
				    	(Response.SERVER_INTERNAL_ERROR,originalRequest);			
				    serverTransaction.sendResponse(response);
				}
			}
		}		            			    
	    // BYE coming from the callee by example
	    ViaHeader viaHeader = headerFactory.createViaHeader(
	            this.myHost, this.myPort, ListeningPoint.UDP, null);	    
	    // Add the via header to the top of the header list.
	    request.addHeader(viaHeader);
	    if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("ViaHeader added " + viaHeader);
    	}
	    SipProvider sendingSipProvider = externalSipProvider;
		if(sipProvider == externalSipProvider) {
			sendingSipProvider = internalSipProvider;
		}
		if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("sending the request:\n" + request + "\n on the other side");
    	}
		if(Request.ACK.equalsIgnoreCase(request.getMethod())) {
			sendingSipProvider.sendRequest(request);
		} else {	    
		    ClientTransaction ctx = sendingSipProvider.getNewClientTransaction(request);
		    serverTransaction.setApplicationData(ctx);
		    ctx.setApplicationData(serverTransaction);
		    ctx.sendRequest();
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
	private void processDialogCreatingRequest(
			SipProvider sipProvider, Request originalRequest,
			ServerTransaction serverTransaction, Request request)
			throws ParseException, InvalidArgumentException, SipException,
			TransactionUnavailableException {
		if(logger.isLoggable(Level.FINEST)) {
			logger.finest("got dialog creating request:\n"+request);
		}
		
		decreaseMaxForwardsHeader(sipProvider, request);
		addLBRecordRoute(sipProvider, request);
		
		SIPNode sipNode = removeRouteHeadersMeantForLB(request);    
		if(sipNode == null) {
			//if the sip node is null it means the request comes from external
			//thus we need to add a route header
			if(logger.isLoggable(Level.FINEST)) {
	    		logger.finest("request is for UAS or proxy case");
	    	}
			addRouteToNode(originalRequest, serverTransaction, request, null);
		} else {
			if(logger.isLoggable(Level.FINEST)) {
	    		logger.finest("request is coming from UAC");
	    	}
			//if the sip node is not null it means the request comes from internal
			//so we stick the node to the call id but don't add a route
			String callID = ((CallID) request.getHeader(CallID.NAME)).getCallId();
			SIPNode node = register.stickSessionToNode(callID, null);
			if(node == null) {							
				//No node present yet to forward the request to, thus sending 500 final error response
				Response response = messageFactory.createResponse
			    	(Response.SERVER_INTERNAL_ERROR,originalRequest);			
			    serverTransaction.sendResponse(response);
			}
		}		
		// Add the via header to the top of the header list.
		ViaHeader viaHeader = headerFactory.createViaHeader(
		        this.myHost, this.myPort, ListeningPoint.UDP, null);
		request.addHeader(viaHeader); 
		if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("ViaHeader added " + viaHeader);
    	}		
		SipProvider sendingSipProvider = internalSipProvider;
		if(sipProvider == internalSipProvider) {
			sendingSipProvider = externalSipProvider;
		}
		if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("sending the request:\n" + request + "\n on the other side");
    	}
		//sending request
		if(Request.ACK.equalsIgnoreCase(request.getMethod())) {
			sendingSipProvider.sendRequest(request);
		} else {
			ClientTransaction ctx = sendingSipProvider.getNewClientTransaction(request);
		    serverTransaction.setApplicationData(ctx);
		    ctx.setApplicationData(serverTransaction);		                		               
		    ctx.sendRequest();
		}
	}

	/**
	 * @param sipProvider
	 * @param request
	 * @throws ParseException
	 */
	private void addLBRecordRoute(SipProvider sipProvider, Request request)
			throws ParseException {				
		if(sipProvider == internalSipProvider) {
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding Record Router Header :" + externalRecordRouteHeader);
			}
			request.addHeader(externalRecordRouteHeader);
			
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding Record Router Header :" + internalRecordRouteHeader);
			}
			request.addHeader(internalRecordRouteHeader);
		} else {
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding Record Router Header :" + internalRecordRouteHeader);
			}
			request.addHeader(internalRecordRouteHeader);
			
			if(logger.isLoggable(Level.FINEST)) {
				logger.finest("adding Record Router Header :" + externalRecordRouteHeader);
			}
			request.addHeader(externalRecordRouteHeader);			
		}		
	}
	
	/**
	 * @param originalRequest
	 * @param serverTransaction
	 * @param request
	 * @param parameters 
	 * @throws ParseException
	 * @throws SipException
	 * @throws InvalidArgumentException
	 */
	private void addRouteToNode(Request originalRequest,
			ServerTransaction serverTransaction, Request request, Map<String, String> parameters)
			throws ParseException, SipException, InvalidArgumentException {
		
		String callID = ((CallID) request.getHeader(CallID.NAME)).getCallId();
		SIPNode node = register.stickSessionToNode(callID, null);
		if(node != null) {
			//Adding Route Header pointing to the node the sip balancer wants to forward to
			SipURI routeSipUri = addressFactory
		    	.createSipURI(null, node.getIp());
			routeSipUri.setPort(node.getPort());
			routeSipUri.setLrParam();
			if(parameters != null) {
				Set<Entry<String, String>> routeParameters= parameters.entrySet();
				for (Entry<String, String> entry : routeParameters) {
					routeSipUri.setParameter(entry.getKey(), entry.getValue());	
				}				
			}
			RouteHeader route = headerFactory.createRouteHeader(addressFactory.createAddress(routeSipUri));
			request.addFirst(route);
		} else {
			//No node present yet to forward the request to, thus sending 500 final error response
			Response response = messageFactory.createResponse
		    	(Response.SERVER_INTERNAL_ERROR,originalRequest);			
		    serverTransaction.sendResponse(response);
		}
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
		    	node = checkRouteHeaderForSipNode(routeHeader);
		    	//since we used double record routing we may have 2 routes corresponding to us here
		        // for ACK and BYE from caller for example
		        routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
		        if(routeHeader != null) {
		            routeUri = (SipURI)routeHeader.getAddress().getURI();
		            //FIXME check against a list of host we may have too
		            if(!isRouteHeaderExternal(routeUri.getHost(), routeUri.getPort())) {
		            	if(logger.isLoggable(Level.FINEST)) {
		            		logger.finest("this route header is for the LB removing it " + routeUri);
		            	}
		            	request.removeFirst(RouteHeader.NAME);
		            	if(node == null) {
		            		node = checkRouteHeaderForSipNode(routeHeader);
		            	}
		            }
		            
		        }
		    }	                
		}
		if(node !=null) {
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
		 //FIXME check against a list of host we may have too
		if(host.equalsIgnoreCase(myHost) && (port == myExternalPort || port == myPort)) {
			return false;
		}
		return true;
	}
	
	/**
	 * This will check if in the route header there is information on which node from the cluster send the request.
	 * If the request is not received from the cluster, this information will not be present. 
	 * @param routeHeader the route header to check
	 * @return the corresponding Sip Node
	 */
	private SIPNode checkRouteHeaderForSipNode(RouteHeader routeHeader) {
		SIPNode node = null;
		SipURI routeSipUri = (SipURI)routeHeader.getAddress().getURI();
		String hostNode = routeSipUri.getParameter(ROUTE_PARAM_NODE_HOST);
		String hostPort = routeSipUri.getParameter(ROUTE_PARAM_NODE_PORT);
		if(hostNode != null && hostPort != null) {
			int port = Integer.parseInt(hostPort);
			node = register.getNode(hostNode, port, routeSipUri.getTransportParam());
		}
		return node;
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
			maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);
			request.addHeader(maxForwardsHeader);
		} else {
			if(maxForwardsHeader.getMaxForwards() - 1 > 0) {
				maxForwardsHeader.setMaxForwards(maxForwardsHeader.getMaxForwards() - 1);
			} else {
				//Max forward header equals to 0, thus sending too many hops response
				Response response = messageFactory.createResponse
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
	private void processCancel(SipProvider sipProvider, Request originalRequest, ServerTransaction serverTransaction) throws ParseException,
			SipException, InvalidArgumentException,
			TransactionUnavailableException {

		if(logger.isLoggable(Level.FINEST)) {
        	logger.finest("process Cancel " + originalRequest);
        }
		Transaction inviteTransaction = ((SIPServerTransaction) serverTransaction).getCanceledInviteTransaction();
		String callID = ((CallID) originalRequest.getHeader(CallID.NAME)).getCallId();
		
		SIPNode node = register.getGluedNode(callID);
		if (node == null) {
			node = register.getNextNode();
		}
		if(node != null) {
			Response response = messageFactory.createResponse
				(Response.OK,originalRequest);
			response.setReasonPhrase("Cancelling");
			serverTransaction.sendResponse(response);
			//
			ClientTransaction ctx = (ClientTransaction)inviteTransaction.getApplicationData();
			Request cancelRequest = ctx.createCancel();
			
//        	cancelRequest.addHeader(viaHeader);
			
			SipURI routeSipUri = addressFactory
		    	.createSipURI(null, node.getIp());
			routeSipUri.setPort(node.getPort());
			routeSipUri.setLrParam();
			RouteHeader route = headerFactory.createRouteHeader(addressFactory.createAddress(routeSipUri));
			cancelRequest.addFirst(route);
			
			SipProvider sendingSipProvider = internalSipProvider;
			if(sipProvider == internalSipProvider) {
				sendingSipProvider = externalSipProvider;
			}
			
			ClientTransaction cancelClientTransaction = sendingSipProvider
				.getNewClientTransaction(cancelRequest);
			cancelClientTransaction.sendRequest();             
		} else {
			//No node present yet to forward the request to, thus sending 500 final error response
			logger.severe("No node present yet to forward the request " + originalRequest);
			Response response = messageFactory.createResponse
		    	(Response.SERVER_INTERNAL_ERROR,originalRequest);			
		    serverTransaction.sendResponse(response);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.SipListener#processResponse(javax.sip.ResponseEvent)
	 */
	public void processResponse(ResponseEvent responseEvent) {
//		SipProvider sipProvider = (SipProvider) responseEvent.getSource();
        Response originalResponse = responseEvent.getResponse();
        ClientTransaction clientTransaction = responseEvent.getClientTransaction();
        if(logger.isLoggable(Level.FINEST)) {
    		logger.finest("got response :\n" + originalResponse);
    	}
        //stateful proxy must not forward 100 Trying
        if (originalResponse.getStatusCode() == 100) {
        	if(logger.isLoggable(Level.FINEST)) {
         		logger.finest("dropping 100 response");
         	}
        	return;	
        }		
        //we drop retransmissions since the proxy tx will retransmit for us
        if(clientTransaction == null) {
        	if(logger.isLoggable(Level.FINEST)) {
         		logger.finest("dropping retransmissions");
         	}
        	return;
        }
        
        if(!Request.CANCEL.equalsIgnoreCase(((CSeqHeader)originalResponse.getHeader(CSeqHeader.NAME)).getMethod())) {
        	
	        Response response = (Response) originalResponse.clone();
	        // Topmost via header is me. As it is reponse to external request
        	ViaHeader viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
        	//FIXME check against a list of host we may have too
		    if(!isRouteHeaderExternal(viaHeader.getHost(), viaHeader.getPort())) {
		    	response.removeFirst(ViaHeader.NAME);
		    }
		    			
//            if (sipProvider == this.internalSipProvider) {
//            	if(logger.isLoggable(Level.FINEST)) {
//            		logger.finest("GOT RESPONSE INTERNAL:\n"+response);
//            	}
//                //Register will be cleaned in the processXXXTerminated jsip callback 
//                //Here if we get response other than 100-2xx we have to clean register from this session                
////                if(dialogCreationMethods.contains(method) && !(100<=status && status<300)) {
////                	register.unStickSessionFromNode(callID);
////                }
//            } else {
//                //Topmost via header is proxy, we leave it
//            	//This happens as proxy sets RR to external interface, but it sets Via to itself.
//            	if(logger.isLoggable(Level.FINEST)) {
//            		logger.finest("GOT RESPONSE INTERNAL, FOR UAS REQ:\n"+response);
//            	}
//            	//Register will be cleaned in the processXXXTerminated jsip callback
//            	//Here we should care only for BYE, all other are send without any change
//            	//We dont even bother status, as BYE means that UAS wants to terminate.
////            	if(method.equals(Request.BYE)) {
////            		register.unStickSessionFromNode(callID);
////            	}
//            }
            
            try {	           
	            if(clientTransaction != null) {
		            ServerTransaction serverTransaction = (ServerTransaction)clientTransaction.getApplicationData();
		            serverTransaction.sendResponse(response);
	            } 
	        } catch (Exception ex) {
	        	logger.log(Level.SEVERE, "Unexpected exception while forwarding the response " + response + 
	        			" (transaction=" + clientTransaction + " / dialog=" + responseEvent.getDialog() + "", ex);
	        }
        } else {
        	if(logger.isLoggable(Level.FINEST)) {
         		logger.finest("dropping CANCEL responses, snce it hop by hop");
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
}
