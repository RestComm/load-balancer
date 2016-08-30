package org.mobicents.tools.sip.balancer.btbua;

import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.SipListenerExt;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.mobicents.tools.sip.balancer.NodeRegisterRMIStub;
import org.mobicents.tools.sip.balancer.ProtocolObjects;
import org.mobicents.tools.sip.balancer.SIPNode;


public class BackToBackUserAgent implements SipListenerExt {
    
    private MessageFactory messageFactory;
    private ProtocolObjects protocolObjects;
    private ListeningPoint lp;
    private SipProvider sp;
    private Timer timer;
    private SIPNode appServerNode;
    AtomicBoolean stopFlag = new AtomicBoolean(false);
    protected String balancers;
    String lbAddress;
	int lbRMIport;
	int lbPort;
	private String transport;
	private int port;
	
	private Dialog incomingDialog,outgoingDialog;
	private ClientTransaction clientTransaction;
	
	public BackToBackUserAgent(int port,String transport,String lbAddress,int lbRMI,int lbPort) 
    {
		this.lbAddress = lbAddress;
		this.lbRMIport = lbRMI;
		this.transport=transport;
		this.port=port;
		this.lbPort=lbPort;
    }
	
	public void start()
	{
		SipFactory sipFactory = null;
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        this.protocolObjects = new ProtocolObjects("backtobackua","gov.nist",transport,true, true, false);
    
        try 
        {
            messageFactory = protocolObjects.messageFactory;
            lp = protocolObjects.sipStack.createListeningPoint("127.0.0.1", port, transport);
            sp = protocolObjects.sipStack.createSipProvider(lp);
            sp.addSipListener(this);
            protocolObjects.start();
            appServerNode = new SIPNode("Node", "127.0.0.1");		
    		appServerNode.getProperties().put(transport.toLowerCase() + "Port", port);		
    		appServerNode.getProperties().put("version", "0");
            timer = new Timer();
            timer.schedule(new TimerTask() {
    			
    			@Override
    			public void run() {
    				try {
    					ArrayList<SIPNode> nodes = new ArrayList<SIPNode>();
    					nodes.add(appServerNode);
    					appServerNode.getProperties().put("version", "0");
    					if(!stopFlag.get())
    						sendKeepAliveToBalancers(nodes);
    				} catch (Exception e) {
    					e.printStackTrace();
    				}
    			}
    		}, 1000, 1000);
        } 
        catch (Exception ex) 
        {
            
        }
    }
    
	public void setBalancers(String balancers) {
		this.balancers = balancers;
	}    
    
    private void replyToRequestEvent(Request event, ServerTransaction st,int status) {
		try {
			st.sendResponse(messageFactory.createResponse(status,event));
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private Dialog getPeerDialog(Dialog original) throws SipException {
		if (original.getDialogId().equals(incomingDialog.getDialogId())) 
			return outgoingDialog;
		else
			return incomingDialog;		
	}

	private void forwardResponse(ResponseEvent receivedResponse) throws SipException 
	{						
		try 
		{
			ServerTransaction serverTransaction = (ServerTransaction)receivedResponse.getClientTransaction().getApplicationData();
			Request stRequest = serverTransaction.getRequest();
            Response newResponse = this.messageFactory.createResponse(receivedResponse.getResponse().getStatusCode(),stRequest);
            ListeningPoint peerListeningPoint = sp.getListeningPoint(transport);
            ContactHeader peerContactHeader = ((ListeningPointExt)peerListeningPoint).createContactHeader();
            newResponse.setHeader(peerContactHeader);
            serverTransaction.sendResponse(newResponse);
		} 
		catch (InvalidArgumentException e)		
		{			
			throw new SipException("invalid response", e);
		}
		catch (ParseException e)		
		{			
			throw new SipException("invalid response", e);
		}
	}
   
    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) 
    {      
    }

    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) 
    {        
    }
 
    public void processIOException(IOExceptionEvent exceptionEvent) 
    {
        // TODO Auto-generated method stub        
    }

    public void processRequest(RequestEvent requestEvent) 
    {
        try {
            Request request = requestEvent.getRequest();
            if (request.getMethod().equals(Request.INVITE)) 
            {
            	ServerTransaction st=sp.getNewServerTransaction(requestEvent.getRequest());
            	incomingDialog = st.getDialog();        				
				
            	try {
        			replyToRequestEvent(requestEvent.getRequest(), st, Response.TRYING);
        			
        			Request newRequest = (Request) request.clone();
    		        newRequest.removeHeader(RouteHeader.NAME);
    		        newRequest.removeHeader(RecordRouteHeader.NAME);
    		        FromHeader fromHeader = (FromHeader) newRequest.getHeader(FromHeader.NAME);
    		        fromHeader.setTag(Long.toString(Math.abs(new Random().nextLong())));
    		        ViaHeader viaHeader = ((ListeningPointExt) sp.getListeningPoint(transport)).createViaHeader();
    		        newRequest.setHeader(viaHeader);
    		        
    		        ContactHeader contactHeader = ((ListeningPointExt) sp.getListeningPoint(transport)).createContactHeader();
    		        newRequest.setHeader(contactHeader);
    		        
    		        SipURI route = this.protocolObjects.addressFactory.createSipURI("lbint", lbAddress + ":" + lbPort);
    				route.setParameter("node_host", "127.0.0.1");
    				route.setParameter("node_port", "" + port);
    				route.setLrParam();
    				
    				RouteHeader routeHeader=(RouteHeader)this.protocolObjects.headerFactory.createRouteHeader(this.protocolObjects.addressFactory.createAddress(route));
    				newRequest.setHeader(routeHeader);
    				
    		        clientTransaction = sp.getNewClientTransaction(newRequest);
    		        outgoingDialog=clientTransaction.getDialog();
    		        clientTransaction.setApplicationData(st);
    		        clientTransaction.sendRequest();    		        
        		} catch (Throwable e) {
        			e.printStackTrace();
        			replyToRequestEvent(request, st, Response.SERVICE_UNAVAILABLE);
        		}
            } 
            else if (request.getMethod().equals(Request.BYE)) 
            {
            	ServerTransaction st=requestEvent.getServerTransaction();
            	replyToRequestEvent(requestEvent.getRequest(), st, Response.OK);
        		Dialog peerDialog=getPeerDialog(requestEvent.getDialog());
            	Request outgoingRequest = peerDialog.createRequest(requestEvent.getRequest().getMethod());
        		final ClientTransaction ct = sp.getNewClientTransaction(outgoingRequest);
        		peerDialog.sendRequest(ct);        		
            }
            else if( request.getMethod().equals(Request.CANCEL))
            {
            	try 
            	{
        			final Dialog peerDialog = outgoingDialog;
        			final DialogState peerDialogState = peerDialog.getState();
        			if (peerDialogState == null || peerDialogState == DialogState.EARLY)
        			{
        				Request cancelRequest=clientTransaction.createCancel();
        				sp.sendRequest(cancelRequest);
        			}
        			else 
        			{
        				clientTransaction=sp.getNewClientTransaction(peerDialog.createRequest(Request.BYE));
        				clientTransaction.sendRequest();
        			}
        		} 
            	catch (Exception e) 
        		{
        			e.printStackTrace();                  
        		}
            }
        } 
        catch ( Exception ex) 
        {
            ex.printStackTrace();            
        }
    }

    
    public void processResponse(ResponseEvent responseEvent) 
    {    	
        try 
        {
        	if(responseEvent.getResponse().getStatusCode()>=100 && responseEvent.getResponse().getStatusCode()<200)
        	{
        		if (responseEvent.getResponse().getStatusCode() == Response.TRYING) 
        			return;
        		
        		forwardResponse(responseEvent);
        	}
        	else if(responseEvent.getResponse().getStatusCode()>=200 && responseEvent.getResponse().getStatusCode()<300)
        	{
        		final CSeqHeader cseq = (CSeqHeader) responseEvent.getResponse().getHeader(CSeqHeader.NAME);
        		if (cseq.getMethod().equals(Request.INVITE)) 
        		{
        			try 
        			{
        				final Request ack = responseEvent.getDialog().createAck(cseq.getSeqNumber());
        				responseEvent.getDialog().sendAck(ack);
        			} 
        			catch (Exception e) 
        			{
        				e.printStackTrace();
        			}
        		} 
        		else if (cseq.getMethod().equals(Request.BYE) || cseq.getMethod().equals(Request.CANCEL)) 
        			return;
        		
        		forwardResponse(responseEvent);
        	}
        	else
        		forwardResponse(responseEvent);        	            
        } 
        catch (Exception ex) 
        {
            ex.printStackTrace();            
        }
    }

    public void processTimeout(TimeoutEvent timeoutEvent) 
    {
        // TODO Auto-generated method stub        
    }

    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
         
    }
    
    public void stop()
    {
    	stopFlag.getAndSet(true);
		timer.cancel();
		
		if(protocolObjects != null)
			protocolObjects.sipStack.stop();
		
		protocolObjects=null;
    }

	private void sendKeepAliveToBalancers(ArrayList<SIPNode> info) 
	{
		Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
		if(balancers != null) {
			for(String balancer:balancers.replaceAll(" ","").split(",")) {
				if(balancer.length()<2) continue;
				String host;
				String port;
				int semi = balancer.indexOf(':');
				if(semi>0) {
					host = balancer.substring(0, semi);
					port = balancer.substring(semi+1);
				} else {
					host = balancer;
					port = "2000";
				}
				try {
					Registry registry = LocateRegistry.getRegistry(host, Integer.parseInt(port));
					NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
					reg.handlePing(info);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
		} else {
			try {
				Registry registry = LocateRegistry.getRegistry(lbAddress, lbRMIport);
				NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
				reg.handlePing(info);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void sendCleanShutdownToBalancers() {
		ArrayList<SIPNode> nodes = new ArrayList<SIPNode>();
		nodes.add(appServerNode);
		sendCleanShutdownToBalancers(nodes);
	}
	
	public void sendCleanShutdownToBalancers(ArrayList<SIPNode> info) {
		Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
		try {
			Registry registry = LocateRegistry.getRegistry(lbAddress, lbRMIport);
			NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
			reg.forceRemoval(info);
			stop();
			Thread.sleep(2000); // delay the OK for a while
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}