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

package org.mobicents.tools.sip.balancer.operation;

import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.header.From;
import gov.nist.javax.sip.stack.NioMessageProcessorFactory;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Random;
import java.util.TimerTask;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
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
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.mobicents.tools.smpp.balancer.ConfigInit;

import junit.framework.TestCase;
import static junit.framework.TestCase.fail;

public class Shootist implements SipListener {
    public AddressFactory addressFactory;

    private MessageFactory messageFactory;

    public HeaderFactory headerFactory;

    private  SipProvider sipProvider;

    private SipStack sipStack;

    private ContactHeader contactHeader;

    private ListeningPoint listeningPoint;

    private ClientTransaction inviteTid;

    private Dialog dialog;

    public boolean callerSendsBye;
    
    public Request inviteRequest;
    
    boolean started = false;
    
    private String localIPAddress="127.0.0.1";
    public String peerHostPort ="127.0.0.1:5060";
    public String transport ="udp";
    
    public LinkedList<Request> requests = new LinkedList<Request>();
    public LinkedList<Response> responses = new LinkedList<Response>();

    private int localPort=5033;
    private int randomizer;
    
    public Shootist()
    {
    	randomizer=(new Random()).nextInt(Integer.MAX_VALUE)+1;
    }
    
    public Shootist(String transport,int port)
    {
    	this();
    	this.transport=transport;
    	this.peerHostPort = "127.0.0.1:" + port;
    	
    }
    
    public Shootist(String transport,int port,int localPort)
    {
    	this();
    	this.transport=transport;
    	this.peerHostPort = "127.0.0.1:" + port;
    	this.localPort=localPort;
    }
    
    public Shootist(String transport,int port,int localPort, boolean ipv6)
    {
    	this();
    	this.transport=transport;
    	this.peerHostPort = "[::1]:" + port;
    	this.localIPAddress = "[::1]";
    	this.localPort=localPort;
    }
    
    class ByeTask  extends TimerTask {
        Dialog dialog;
        public ByeTask(Dialog dialog)  {
            this.dialog = dialog;
        }
        public void run () {
            try {
               Request byeRequest = this.dialog.createRequest(Request.BYE);
               ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
               dialog.sendRequest(ct);
            } catch (Exception ex) {
                ex.printStackTrace();
                fail("Unexpected exception ");
            }

        }

    }
    public void processInvite(Request request, ServerTransaction stx) {
    	try {
    		inviteRequest = request;
    		Response response = messageFactory.createResponse(180, request);
    		contactHeader = headerFactory.createContactHeader(addressFactory.createAddress("sip:here@" + localIPAddress + ":" + localPort));
    		response.addHeader(contactHeader);
    		dialog = stx.getDialog();
    		//check removing incorrect patching last via https://github.com/RestComm/load-balancer/issues/97
    		FromHeader fromHeader = (FromHeader)response.getHeader(From.NAME);
    		URI currUri = fromHeader.getAddress().getURI();
    		String fromUser = null;
    		if(currUri.isSipURI())
    		{
    			fromUser = ((SipURI)currUri).getUser();
    			if(fromUser.equals("senderToNexmo"))
    			{
    				ViaHeader lastViaHeader = null;
    				ListIterator<ViaHeader> it = response.getHeaders(ViaHeader.NAME);
    				while(it.hasNext())
    					lastViaHeader = it.next();
    				lastViaHeader.setReceived("127.0.0.2");
    				lastViaHeader.setParameter("rport", "1111");
    				
    			}	
    		}
    		stx.sendResponse(response);
    		try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		response = messageFactory.createResponse(200, request);
    		contactHeader = headerFactory.createContactHeader(addressFactory.createAddress("sip:here@" + localIPAddress + ":" + localPort));
    		response.addHeader(contactHeader);
    		dialog = stx.getDialog();
    		stx.sendResponse(response );
    	} catch (SipException e) {
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    	} catch (InvalidArgumentException e) {
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    	} catch (ParseException e) {
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    	}
    }
    public void processAck(Request request, ServerTransaction stx) {
    }

    public void processRequest(RequestEvent requestReceivedEvent) {
        Request request = requestReceivedEvent.getRequest();
        requests.add(request);
        ServerTransaction serverTransactionId = requestReceivedEvent
                .getServerTransaction();
        if(serverTransactionId == null) {
        	try {
				serverTransactionId = sipProvider.getNewServerTransaction(request);
			} catch (TransactionAlreadyExistsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TransactionUnavailableException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }

        System.out.println("\n\nRequest " + request.getMethod()
                + " received at " + sipStack.getStackName()
                + " with server transaction id " + serverTransactionId);

        if (request.getMethod().equals(Request.INVITE)) {
            processInvite(request, serverTransactionId);
        } else if (request.getMethod().equals(Request.BYE)) {
            processBye(request, serverTransactionId);
        } else if (request.getMethod().equals(Request.ACK)) {
            processAck(request, serverTransactionId);
        } else {
            try {
                serverTransactionId.sendResponse( messageFactory.createResponse(200,request) );
            } catch (Exception e) {
                e.printStackTrace();
                fail("Unxepcted exception ");
            }
        }

    }

    public void processBye(Request request,
            ServerTransaction serverTransactionId) {
        try {
            System.out.println("shootist:  got a bye .");
            if (serverTransactionId == null) {
                System.out.println("shootist:  null TID.");
                return;
            }
            Dialog dialog = serverTransactionId.getDialog();
            System.out.println("Dialog State = " + dialog.getState());
            Response response = messageFactory.createResponse(200, request);
            serverTransactionId.sendResponse(response);
            System.out.println("shootist:  Sending OK.");
            System.out.println("Dialog State = " + dialog.getState());

        } catch (Exception ex) {
            fail("Unexpected exception");

        }
    }

       // Save the created ACK request, to respond to retransmitted 2xx
       private Request ackRequest;

    public void processResponse(ResponseEvent responseReceivedEvent) {
        System.out.println("Got a response");
        Response response = (Response) responseReceivedEvent.getResponse();
        responses.add(response);
        ClientTransaction tid = responseReceivedEvent.getClientTransaction();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

        System.out.println("Response received : Status Code = "
                + response.getStatusCode() + " " + cseq);

        if (tid == null) {

            // RFC3261: MUST respond to every 2xx
            if (ackRequest!=null && dialog!=null) {
               System.out.println("re-sending ACK");
               try {
                  dialog.sendAck(ackRequest);
               } catch (SipException se) {
                  se.printStackTrace();
                  fail("Unxpected exception ");
               }
            }
            return;
        }
        // If the caller is supposed to send the bye
        
        System.out.println("transaction state is " + tid.getState());
        System.out.println("Dialog = " + tid.getDialog());
        if(tid.getDialog()!=null)
        	System.out.println("Dialog State is " + tid.getDialog().getState());

        TestCase.assertSame("Checking dialog identity",tid.getDialog(), this.dialog);

        try {
            if (response.getStatusCode() == Response.OK) {
                if (cseq.getMethod().equals(Request.INVITE)) {
                    System.out.println("Dialog after 200 OK  " + dialog);
                    System.out.println("Dialog State after 200 OK  " + dialog.getState());
                    Request ackRequest = dialog.createAck(cseq.getSeqNumber());
                    System.out.println("Sending ACK");
                    dialog.sendAck(ackRequest);

                } else if (cseq.getMethod().equals(Request.CANCEL)) {
                    if (dialog.getState() == DialogState.CONFIRMED) {
                        // oops cancel went in too late. Need to hang up the
                        // dialog.
                        System.out
                                .println("Sending BYE -- cancel went in too late !!");
                        Request byeRequest = dialog.createRequest(Request.BYE);
                        ClientTransaction ct = sipProvider
                                .getNewClientTransaction(byeRequest);
                        dialog.sendRequest(ct);

                    }

                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            
        }

    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {

        System.out.println("Transaction Time out");
    }

    public void sendCancel() {
        try {
            System.out.println("Sending cancel");
            Request cancelRequest = inviteTid.createCancel();
            ClientTransaction cancelTid = sipProvider
                    .getNewClientTransaction(cancelRequest);
            cancelTid.sendRequest();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    synchronized public void start() {
    	started = true;
        SipFactory sipFactory = null;
        sipStack = null;
        sipFactory = SipFactory.getInstance();
        sipFactory.resetFactory();		
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "shootist " + randomizer);
		properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NioMessageProcessorFactory.class.getName());
		properties.setProperty("javax.sip.OUTBOUND_PROXY", peerHostPort + "/"
                + transport);
        // If you want to use UDP then uncomment this.
        properties.setProperty("javax.sip.STACK_NAME", "shootist");
        properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
        
        if(transport.equals(ListeningPointExt.WS)||transport.equals(ListeningPointExt.WSS))
			properties.setProperty("gov.nist.javax.sip.PATCH_SIP_WEBSOCKETS_HEADERS", "false");
        
        if(transport.equalsIgnoreCase(ListeningPoint.TLS) || transport.equalsIgnoreCase(ListeningPointExt.WSS))
		{
        	properties.setProperty("javax.net.ssl.keyStore", ConfigInit.class.getClassLoader().getResource("keystore").getFile());
			properties.setProperty("javax.net.ssl.keyStorePassword", "123456");
			properties.setProperty("javax.net.ssl.trustStore", ConfigInit.class.getClassLoader().getResource("keystore").getFile());
			properties.setProperty("javax.net.ssl.trustStorePassword", "123456");
		}

        // The following properties are specific to nist-sip
        // and are not necessarily part of any other jain-sip
        // implementation.
        // You can set a max message size for tcp transport to
        // guard against denial of service attack.
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "logs/shootistdebug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "logs/shootistlog.txt");

        // Drop the client connection after we are done with the transaction.
        properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS",
                "false");
        // Set to 0 (or NONE) in your production code for max speed.
        // You need 16 (or TRACE) for logging traces. 32 (or DEBUG) for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "INFO");
        

        try {
            // Create SipStack object
            sipStack = sipFactory.createSipStack(properties);
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();
            System.out.println("createSipStack " + sipStack);
        } catch (PeerUnavailableException e) {
            // could not find
            // gov.nist.jain.protocol.ip.sip.SipStackImpl
            // in the classpath
            e.printStackTrace();
            System.err.println(e.getMessage());
            System.exit(0);
        }

        try {
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();
            listeningPoint = sipStack.createListeningPoint(localIPAddress, localPort, transport);
            sipProvider = sipStack.createSipProvider(listeningPoint);
            Shootist listener = this;
            sipProvider.addSipListener(listener);            
            sipStack.start();
        } catch(Exception e) {
        	e.printStackTrace();
        }
        
    }

    public void sendInitialInvite(){
    	sendInitial("BigGuy", "here.com", "INVITE",null, null, null);
    }
    
    public void sendInitial(String method) {
    	sendInitial("BigGuy", "here.com", method, null, null, null);
    }
    
    public void sendInitial(String method,RouteHeader route) {
    	sendInitial("BigGuy", "here.com", method, route, null, null);
    }
    
    public void sendInitial(String fromUser, String fromHost, String method, RouteHeader route, String[] headerNames, String[] headerContents) {
    	try{
    		if(!started) start();
    		String fromName = fromUser;
    		String fromSipAddress = fromHost;
    		String fromDisplayName = "The Master Blaster";

    		String toSipAddress = "there.com";
    		String toUser = "LittleGuy";
            String toDisplayName = "The Little Blister";

            // create >From Header
            SipURI fromAddress = addressFactory.createSipURI(fromName,
                    fromSipAddress);

            Address fromNameAddress = addressFactory.createAddress(fromAddress);
            fromNameAddress.setDisplayName(fromDisplayName);
            FromHeader fromHeader = headerFactory.createFromHeader(
                    fromNameAddress, "12345");

            // create To Header
            SipURI toAddress = addressFactory
                    .createSipURI(toUser, toSipAddress);
            Address toNameAddress = addressFactory.createAddress(toAddress);
            toNameAddress.setDisplayName(toDisplayName);
            ToHeader toHeader = headerFactory.createToHeader(toNameAddress,
                    null);

            // create Request URI
            SipURI requestURI = addressFactory.createSipURI(toUser,
                    peerHostPort);

            // Create ViaHeaders

            ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            
            String ipAddress = null;
            
            if(fromUser.equals("KostyaNosach"))
            	ipAddress = "127.0.0.2";
            else
            	ipAddress = listeningPoint.getIPAddress();
            ViaHeader viaHeader = headerFactory.createViaHeader(ipAddress,sipProvider.getListeningPoint(transport).getPort(), transport, null);

            // add via headers
            viaHeaders.add(viaHeader);

            // Create ContentTypeHeader
            ContentTypeHeader contentTypeHeader = headerFactory
                    .createContentTypeHeader("application", "sdp");

            // Create a new CallId header
            CallIdHeader callIdHeader = sipProvider.getNewCallId();

            // Create a new Cseq header
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L,
                    method);

            // Create a new MaxForwardsHeader
            MaxForwardsHeader maxForwards = headerFactory
                    .createMaxForwardsHeader(70);

            // Create the request.
            Request request = messageFactory.createRequest(requestURI,
                    method, callIdHeader, cSeqHeader, fromHeader,
                    toHeader, viaHeaders, maxForwards);
            // Create contact headers
            String host = localIPAddress;

            SipURI contactUrl = addressFactory.createSipURI(fromName, host);
            contactUrl.setPort(listeningPoint.getPort());
            contactUrl.setLrParam();

            // Create the contact name address.
            SipURI contactURI = addressFactory.createSipURI(fromName, host);
            contactURI.setPort(sipProvider.getListeningPoint(transport)
                    .getPort());

            Address contactAddress = addressFactory.createAddress(contactURI);

            // Add the contact address.
            contactAddress.setDisplayName(fromName);

            contactHeader = headerFactory.createContactHeader(contactAddress);
            request.addHeader(contactHeader);

            
            // to the outgoing SIP request.
            // Add the extension header.
            Header extensionHeader = headerFactory.createHeader("My-Header",
                    "my header value");
            request.addHeader(extensionHeader);
            
            if(headerNames != null) {
    			for(int q=0; q<headerNames.length; q++) {
    				Header h = headerFactory.createHeader(headerNames[q], headerContents[q]);
//    				if(setHeader) {
    					request.setHeader(h);
//    				} else {
//    					request.addHeader(h);
//    				}
    			}
    		}
            
            if(route!=null)
            	request.addHeader(route);
            
            String sdpData = "v=0\r\n"
                    + "o=4855 13760799956958020 13760799956958020"
                    + " IN IP4  129.6.55.78\r\n" + "s=mysession session\r\n"
                    + "p=+46 8 52018010\r\n" + "c=IN IP4  129.6.55.78\r\n"
                    + "t=0 0\r\n" + "m=audio 6022 RTP/AVP 0 4 18\r\n"
                    + "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:4 G723/8000\r\n"
                    + "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";
            byte[] contents = sdpData.getBytes();

            request.setContent(contents, contentTypeHeader);
            // You can add as many extension headers as you
            // want.

            extensionHeader = headerFactory.createHeader("My-Other-Header",
                    "my new header value ");
            request.addHeader(extensionHeader);

            Header callInfoHeader = headerFactory.createHeader("Call-Info",
                    "<http://www.antd.nist.gov>");
            request.addHeader(callInfoHeader);

            // Create the client transaction.
            inviteTid = sipProvider.getNewClientTransaction(request);

            // send the request out.
            inviteTid.sendRequest();

            dialog = inviteTid.getDialog();

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
            fail("Unxpected exception ");
        }
    }
    
    public void sendMessage() throws SipException {
    	Request r = dialog.createRequest("MESSAGE");
    	dialog.sendRequest(sipProvider.getNewClientTransaction(r));
    }
    
    public void sendBye() throws SipException {
    	Request r = dialog.createRequest("BYE");
    	dialog.sendRequest(sipProvider.getNewClientTransaction(r));
    }



    public void processIOException(IOExceptionEvent exceptionEvent) {
        System.out.println("IOException happened for "
                + exceptionEvent.getHost() + " port = "
                + exceptionEvent.getPort());

    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
    	

        
    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        System.out.println("dialogTerminatedEvent");

    }

    public void stop() {
    
    	if(sipStack != null)
        this.sipStack.stop();
    	started = false;
    }	
}
