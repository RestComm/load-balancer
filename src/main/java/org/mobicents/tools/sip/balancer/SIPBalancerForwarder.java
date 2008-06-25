package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.header.CSeq;
import gov.nist.javax.sip.header.CallID;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * A stateless UDP Forwarder that listens at a port and forwards to multiple
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
	private static Logger logger = Logger.getLogger(SIPBalancerForwarder.class
			.getCanonicalName());
	
	private SipProvider internalSipProvider;

    private SipProvider externalSipProvider;

    private String myHost;

    private int myPort;

    private int myExternalPort;

    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory;

    private SipStack sipStack;
	
	private NodeRegister register;
    
    private static final HashSet<String> dialogCreationMethods=new HashSet<String>(2);
    
    static{
    	dialogCreationMethods.add(Request.INVITE);
    	dialogCreationMethods.add(Request.SUBSCRIBE);
    }
	
	public SIPBalancerForwarder(String myHost, int myPort, int myExternalPort, NodeRegister register) throws IllegalStateException{
		super();
		this.myHost = myHost;
		this.myPort = myPort;
		this.myExternalPort = myExternalPort;
		this.register=register;		
	}

	public void start() throws IllegalStateException {
		
		SipFactory sipFactory = null;
        sipStack = null;
        
        Properties properties = new Properties();
        properties.setProperty("javax.sip.RETRANSMISSION_FILTER", "true");
        properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
        properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
        // You need 16 for logging traces. 32 for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "logs/sipbalancerforwarderdebug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "logs/sipbalancerforwarder.xml");
//        properties.setProperty("javax.sip.ROUTER_PATH", "org.mobicents.tools.sip.balancer.RouterImpl");
//        properties.setProperty("javax.sip.OUTBOUND_PROXY", Integer
//                .toString(myPort));

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

            ListeningPoint lp = sipStack.createListeningPoint(myHost, myPort, "udp");
            internalSipProvider = sipStack.createSipProvider(lp);
            internalSipProvider.addSipListener(this);

            lp = sipStack.createListeningPoint(myHost, myExternalPort, "udp");
            externalSipProvider = sipStack.createSipProvider(lp);
            externalSipProvider.addSipListener(this);

            sipStack.start();
        } catch (Exception ex) {
        	throw new IllegalStateException("Cant create sip objects and lps due to["+ex.getMessage()+"]", ex);
        }
        logger.info("Sip Balancer started on address " + myHost + ", external port : " + myExternalPort + ", port : "+ myPort);
	}
	
	public void stop() {
		Iterator<SipProvider> sipProviderIterator = sipStack.getSipProviders();
		try{
			while (sipProviderIterator.hasNext()) {
				SipProvider sipProvider = sipProviderIterator.next();
				ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
				for (ListeningPoint listeningPoint : listeningPoints) {
					logger.info("Removing the following Listening Point " + listeningPoint);
					sipProvider.removeListeningPoint(listeningPoint);
					sipStack.deleteListeningPoint(listeningPoint);
				}
				logger.info("Removing the sip provider");
				sipProvider.removeSipListener(this);	
				sipStack.deleteSipProvider(sipProvider);			
			}
		} catch (Exception e) {
			throw new IllegalStateException("Cant remove the listening points or sip providers", e);
		}
		
		sipStack.stop();
		sipStack = null;
		logger.info("Sip Balancer stopped");
	}
	
	public void processDialogTerminated(
			DialogTerminatedEvent dialogTerminatedEvent) {
		// We wont see those
	}

	public void processIOException(IOExceptionEvent exceptionEvent) {
		// Hopefully we wont see those either
	}

	public void processRequest(RequestEvent requestEvent) {
		// This will be invoked only by external endpoint
		
		try {
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();
            Request request = requestEvent.getRequest();
            if (sipProvider == this.externalSipProvider) {
                if(logger.isLoggable(Level.FINEST)) {
                	logger.finest("GOT EXTERNAL REQUEST:\n"+request);
                }
                //if(request.getMethod().equals(Request.BYE))
                //	return;
                // Tack our own via header to the request.
                // Tack on our internal port so the other side responds to me.
                ViaHeader viaHeader = headerFactory.createViaHeader(
                        this.myHost, this.myPort, "udp", "z9hG4bK"+Math.random()*31+""+System.currentTimeMillis());
                // Decreasing the Max Forward Header
                MaxForwardsHeader maxForwardsHeader = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);
                if (maxForwardsHeader == null) {
                	maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);
    				request.addHeader(maxForwardsHeader);
    			} else {
    				maxForwardsHeader.setMaxForwards(maxForwardsHeader.getMaxForwards() - 1);
    			}
                // Add the via header to the top of the header list.
                request.addHeader(viaHeader);    
                //Removing first routeHeader if it is for the sip balancer
                RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
                if(routeHeader != null) {
	                SipURI routeUri = (SipURI)routeHeader.getAddress().getURI();
	                //FIXME check against a list of host we may have too
	                if(routeUri.getHost().equalsIgnoreCase(myHost) && (routeUri.getPort() == myExternalPort || routeUri.getPort() == myPort)) {
	                	if(logger.isLoggable(Level.FINEST)) {
	                		logger.finest("this route header is for us removing it " + routeUri);
	                	}
	                	request.removeFirst(RouteHeader.NAME);
	                	//since we used double record routing we may have 2 routes corresponding to us here
	                    // for ACK and BYE from caller for example
	                    routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
	                    if(routeHeader != null) {
	    	                routeUri = (SipURI)routeHeader.getAddress().getURI();
	    	                //FIXME check against a list of host we may have too
	    	                if(routeUri.getHost().equalsIgnoreCase(myHost) && (routeUri.getPort() == myExternalPort || routeUri.getPort() == myPort)) {
	    	                	logger.finest("this route header is for us removing it " + routeUri);
	    	                	request.removeFirst(RouteHeader.NAME);
	    	                }
	    	                
	                    }
	                }	                
                }                
                
                // Record route the invite so the bye comes to me. FIXME: Add check, on reINVITE we wont add ourselvses twice
                if (request.getMethod().equals(Request.INVITE) || request.getMethod().equals(Request.SUBSCRIBE)) {
                    SipURI sipUri = this.addressFactory
                            .createSipURI(null, sipProvider.getListeningPoint(
                                    ListeningPoint.UDP).getIPAddress());
                    sipUri.setPort(sipProvider.getListeningPoint(ListeningPoint.UDP).getPort());
                    //See RFC 3261 19.1.1 for lr parameter
                    sipUri.setLrParam();
                    Address address = this.addressFactory.createAddress(sipUri);
                    address.setURI(sipUri);
                    if(logger.isLoggable(Level.FINEST)) {
                    	logger.finest("ADDING RRH:"+address);
                    }                    
                    RecordRouteHeader recordRoute = headerFactory
                            .createRecordRouteHeader(address);                    
                    request.addHeader(recordRoute);
                    //We need to use double record (better option than record route rewriting) routing otherwise it is impossible :
                    //a) to forward BYE from the callee side to the caller
                    //b) to support different transports
		            SipURI internalSipUri = this.addressFactory
		                    .createSipURI(null, internalSipProvider.getListeningPoint(
		                            ListeningPoint.UDP).getIPAddress());
		            internalSipUri.setPort(internalSipProvider.getListeningPoint(ListeningPoint.UDP).getPort());
		            //See RFC 3261 19.1.1 for lr parameter
		            internalSipUri.setLrParam();
		            Address internalAddress = this.addressFactory.createAddress(internalSipUri);
		            internalAddress.setURI(internalSipUri);
		            if(logger.isLoggable(Level.FINEST)) {
		            	logger.finest("ADDING RRH:"+internalAddress);
		            }                    
		            RecordRouteHeader internalRecordRoute = headerFactory
		                    .createRecordRouteHeader(internalAddress);                    
		            request.addHeader(internalRecordRoute);
                } /* else if ( request.getMethod().equals(Request.BYE)) {
                    // Should have me on the route list.
                    request.removeFirst(RouteHeader.NAME);
                } */
                
                //Adding Route Header pointing to the node the sip balancer wants to forward to 
                String callID = ((CallID) request.getHeader(CallID.NAME)).getCallId();
        		SIPNode node = null;
        		String method = request.getMethod();
        		if (method.equals(Request.INVITE) || method.equals(Request.SUBSCRIBE)) {
        			node = register.stickSessionToNode(callID);
        			if(node != null) {
		        		SipURI routeSipUri = this.addressFactory
		                	.createSipURI(null, node.getIp());
		        		routeSipUri.setPort(node.getPort());
		        		routeSipUri.setLrParam();
		        		RouteHeader route = headerFactory.createRouteHeader(addressFactory.createAddress(routeSipUri));
		        		request.addFirst(route);
        			} else {
        				//No node present yet to forward the request to, thus sending 500 final error response
        				Response response = messageFactory.createResponse
	        	        	(Response.SERVER_INTERNAL_ERROR,request);			
	        	        sipProvider.sendResponse(response);
        			}
        		} else if (method.equals(Request.CANCEL)) {
        			node = register.getGluedNode(callID);
        			if (node == null) {
        				for (int i = 0; i < 5 && node == null; i++) {
        					try {
        						node = register.getNextNode();
        					} catch (IndexOutOfBoundsException ioobe) {
        					}
        				}
        			}
        			if(node != null) {
	        			SipURI routeSipUri = this.addressFactory
		                	.createSipURI(null, node.getIp());
		        		routeSipUri.setPort(node.getPort());
		        		routeSipUri.setLrParam();
		        		RouteHeader route = headerFactory.createRouteHeader(addressFactory.createAddress(routeSipUri));
		        		request.addFirst(route);
        			} else {
        				//No node present yet to forward the request to, thus sending 500 final error response
        				Response response = messageFactory.createResponse
	        	        	(Response.SERVER_INTERNAL_ERROR,request);			
	        	        sipProvider.sendResponse(response);
        			}
        		} 
                if(logger.isLoggable(Level.FINEST)) {
                	logger.finest("SENDING TO INTERNAL:"+request);
                }
                //sending request
                this.internalSipProvider.sendRequest(request);

            } else {
            	//We wont see any dialog creating reqs from this side, we will only see responses
            	//Proxy sets RR to IP_ExternalPort of LB
            	if (request.getMethod().equals(Request.INVITE) || request.getMethod().equals(Request.SUBSCRIBE)) {
            		throw new IllegalStateException("Illegal state!! unexpected dialog creating request " + request);
            	} else {
            		// BYE coming from the callee by example
            		ViaHeader viaHeader = headerFactory.createViaHeader(
                            this.myHost, this.myPort, "udp", "z9hG4bK"+Math.random()*31+""+System.currentTimeMillis());
                    // Add the via header to the top of the header list.
                    request.addHeader(viaHeader);
                    //Removing first routeHeader if it is for the sip balancer
                    RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
                    if(routeHeader != null) {
    	                SipURI routeUri = (SipURI)routeHeader.getAddress().getURI();
    	                //FIXME check against a list of host we may have too
    	                if(routeUri.getHost().equalsIgnoreCase(myHost) && (routeUri.getPort() == myExternalPort || routeUri.getPort() == myPort)) {
    	                	logger.finest("this route header is for us removing it " + routeUri);
    	                	request.removeFirst(RouteHeader.NAME);
    	                	//since we used double record routing we may have 2 routes corresponding to us here 
    	                    routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
    	                    if(routeHeader != null) {
    	    	                routeUri = (SipURI)routeHeader.getAddress().getURI();
    	    	                //FIXME check against a list of host we may have too
    	    	                if(routeUri.getHost().equalsIgnoreCase(myHost) && (routeUri.getPort() == myExternalPort || routeUri.getPort() == myPort)) {
    	    	                	logger.finest("this route header is for us removing it " + routeUri);
    	    	                	request.removeFirst(RouteHeader.NAME);
    	    	                }
    	                    }
    	                }    	               
                    }                    
                    this.externalSipProvider.sendRequest(request);
            	}
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
	}

	public void processResponse(ResponseEvent responseEvent) {
		try {
            SipProvider sipProvider = (SipProvider) responseEvent.getSource();
            Response response = responseEvent.getResponse();
            SipProvider sender=null;
            
            int status=response.getStatusCode();
            String method=((CSeq)response.getHeader(CSeq.NAME)).getMethod();
            String callID=((CallID)response.getHeader(CallID.NAME)).getCallId();
            if (sipProvider == this.internalSipProvider) {
            	if(logger.isLoggable(Level.FINEST)) {
            		logger.finest("GOT RESPONSE INTERNAL:\n"+response);
            	}
            	 // Topmost via header is me. As it is reposne to external reqeust
                response.removeFirst(ViaHeader.NAME);
            	
                sender=this.externalSipProvider;
                
                //Here if we get response other than 100-2xx we have to clean register from this session
                if(dialogCreationMethods.contains(method) && !(100<=status && status<300)) 
                {
                	register.unStickSessionFromNode(callID);
                }
            } else {
                //Topmost via header is proxy, we leave it
            	//This happens as proxy sets RR to external interface, but it sets Via to itself.
            	if(logger.isLoggable(Level.FINEST)) {
            		logger.finest("GOT RESPONSE INTERNAL, FOR UAS REQ:\n"+response);
            	}
            	sender=this.internalSipProvider;
            	
            	//Here we should care only for BYE, all other are send without any change
            	//We dont even bother status, as BYE means that UAS wants to terminate.
            	if(method.equals(Request.BYE))
            	{
            		register.unStickSessionFromNode(callID);
            	}
            }
            sender.sendResponse(response);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
	}

	public void processTimeout(TimeoutEvent timeoutEvent) {
		// TODO Auto-generated method stub
	}

	public void processTransactionTerminated(
			TransactionTerminatedEvent transactionTerminatedEvent) {
		// TODO Auto-generated method stub
	}
}
