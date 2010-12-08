package org.mobicents.tools.sip.balancer.performance;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Timer;
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
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import junit.framework.TestCase;
import static junit.framework.TestCase.fail;

public class Shootist implements SipListener {
    private static AddressFactory addressFactory;

    private static MessageFactory messageFactory;

    private static HeaderFactory headerFactory;

    private  SipProvider sipProvider;

    private SipStack sipStack;

    private ContactHeader contactHeader;

    private ListeningPoint udpListeningPoint;

    private ClientTransaction inviteTid;

    private Dialog dialog;

    private boolean byeTaskRunning;
    
    public boolean callerSendsBye;
    
    public Request inviteRequest;

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
    		contactHeader = headerFactory.createContactHeader(addressFactory.createAddress("sip:here@127.0.0.1:5033"));
    		response.addHeader(contactHeader);
    		dialog = stx.getDialog();
    		stx.sendResponse(response );
    		try {
				Thread.sleep(4000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		response = messageFactory.createResponse(200, request);
    		contactHeader = headerFactory.createContactHeader(addressFactory.createAddress("sip:here@127.0.0.1:5033"));
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
    	new Timer().schedule(new ByeTask(dialog), 5000);
    }

    public void processRequest(RequestEvent requestReceivedEvent) {
        Request request = requestReceivedEvent.getRequest();
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
        if ( callerSendsBye && !byeTaskRunning) {
            byeTaskRunning = true;
            new Timer().schedule(new ByeTask(dialog), 4000) ;
        }
        System.out.println("transaction state is " + tid.getState());
        System.out.println("Dialog = " + tid.getDialog());
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
    boolean started = false;
    
    String peerHostPort ="127.0.0.1:5060";
    String transport ="udp";
    synchronized public void start() {
    	started = true;
        SipFactory sipFactory = null;
        sipStack = null;
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        Properties properties = new Properties();
        properties.setProperty("javax.sip.OUTBOUND_PROXY", peerHostPort + "/"
                + transport);
        // If you want to use UDP then uncomment this.
        properties.setProperty("javax.sip.STACK_NAME", "shootist");
        properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");

        // The following properties are specific to nist-sip
        // and are not necessarily part of any other jain-sip
        // implementation.
        // You can set a max message size for tcp transport to
        // guard against denial of service attack.
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "shootistdebug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "shootistlog.txt");

        // Drop the client connection after we are done with the transaction.
        properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS",
                "false");
        // Set to 0 (or NONE) in your production code for max speed.
        // You need 16 (or TRACE) for logging traces. 32 (or DEBUG) for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "DEBUG");

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
            udpListeningPoint = sipStack.createListeningPoint("127.0.0.1", 5033, transport);
            sipProvider = sipStack.createSipProvider(udpListeningPoint);
            Shootist listener = this;
            sipProvider.addSipListener(listener);
            sipStack.start();
        } catch(Exception e) {
        	e.printStackTrace();
        }
        
    }

    public void sendInitialInvite() {
    	try{
    		if(!started) start();
    		String fromName = "BigGuy";
    		String fromSipAddress = "here.com";
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

            ArrayList viaHeaders = new ArrayList();
            String ipAddress = udpListeningPoint.getIPAddress();
            ViaHeader viaHeader = headerFactory.createViaHeader(ipAddress,
                    sipProvider.getListeningPoint(transport).getPort(),
                    transport, null);

            // add via headers
            viaHeaders.add(viaHeader);

            // Create ContentTypeHeader
            ContentTypeHeader contentTypeHeader = headerFactory
                    .createContentTypeHeader("application", "sdp");

            // Create a new CallId header
            CallIdHeader callIdHeader = sipProvider.getNewCallId();

            // Create a new Cseq header
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L,
                    Request.INVITE);

            // Create a new MaxForwardsHeader
            MaxForwardsHeader maxForwards = headerFactory
                    .createMaxForwardsHeader(70);

            // Create the request.
            Request request = messageFactory.createRequest(requestURI,
                    Request.INVITE, callIdHeader, cSeqHeader, fromHeader,
                    toHeader, viaHeaders, maxForwards);
            // Create contact headers
            String host = "127.0.0.1";

            SipURI contactUrl = addressFactory.createSipURI(fromName, host);
            contactUrl.setPort(udpListeningPoint.getPort());
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

            // You can add extension headers of your own making
            // to the outgoing SIP request.
            // Add the extension header.
            Header extensionHeader = headerFactory.createHeader("My-Header",
                    "my header value");
            request.addHeader(extensionHeader);

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



    public void processIOException(IOExceptionEvent exceptionEvent) {
        System.out.println("IOException happened for "
                + exceptionEvent.getHost() + " port = "
                + exceptionEvent.getPort());

    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
    	
        System.out.println("Transaction terminated event recieved " + transactionTerminatedEvent.getClientTransaction().getRequest());
        throw new RuntimeException();
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
