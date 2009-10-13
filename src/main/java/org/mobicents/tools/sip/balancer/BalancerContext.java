package org.mobicents.tools.sip.balancer;

import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.message.MessageFactory;

public class BalancerContext {
	public CopyOnWriteArrayList<SIPNode> nodes;
	
	public Object parameters;
	
	public SipProvider internalSipProvider;

	public SipProvider externalSipProvider;

	public String myHost;

	public int myPort;

	public int myExternalPort;

	public AddressFactory addressFactory;
	public HeaderFactory headerFactory;
	public MessageFactory messageFactory;

	public SipStack sipStack;

	public Properties properties;  
	
	public RecordRouteHeader internalRecordRouteHeader;
	public RecordRouteHeader externalRecordRouteHeader;
    
	public AtomicLong requestsProcessed = new AtomicLong(0);
	
    public AtomicLong responsesProcessed = new AtomicLong(0);
}
