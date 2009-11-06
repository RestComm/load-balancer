package org.mobicents.tools.sip.balancer;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;

public class BalancerContext {
	public CopyOnWriteArrayList<SIPNode> nodes;
	public ConcurrentHashMap<String, SIPNode> jvmRouteToSipNode;
	
	public String externalTransport = "UDP";
	public String internalTransport = "UDP";
	
	public Object parameters;

	public SipProvider externalSipProvider;
	public SipProvider internalSipProvider;

	public String host;
	public int externalPort;
	public int internalPort;
	
	public String internalIpLoadBalancerAddress;
	public int internalLoadBalancerPort;
	
	public String externalIpLoadBalancerAddress;
	public int externalLoadBalancerPort;
	
	public AddressFactory addressFactory;
	public HeaderFactory headerFactory;
	public MessageFactory messageFactory;

	public SipStack sipStack;

	public Properties properties;  
	
	public RecordRouteHeader externalRecordRouteHeader;
	public RecordRouteHeader externalIpBalancerRecordRouteHeader; 
	public RecordRouteHeader internalRecordRouteHeader;
	public RecordRouteHeader internalIpBalancerRecordRouteHeader; 
	public RecordRouteHeader activeExternalHeader;
	public RecordRouteHeader activeInternalHeader;
    
	public AtomicLong requestsProcessed = new AtomicLong(0);
	
    public AtomicLong responsesProcessed = new AtomicLong(0);
    
    public boolean isTwoEntrypoints() {
    	return internalPort>0;
    }
    
    public static BalancerContext balancerContext = new BalancerContext();

	
    
    public BalancerAlgorithm balancerAlgorithm = null;
}
