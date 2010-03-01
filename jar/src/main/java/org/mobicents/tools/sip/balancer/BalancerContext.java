package org.mobicents.tools.sip.balancer;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
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
	public ConcurrentHashMap<String, SIPNode> jvmRouteToSipNode;
	
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
	
	public RecordRouteHeader[] externalRecordRouteHeader = new RecordRouteHeader[2];
	public RecordRouteHeader[] externalIpBalancerRecordRouteHeader = new RecordRouteHeader[2]; 
	public RecordRouteHeader[] internalRecordRouteHeader = new RecordRouteHeader[2];
	public RecordRouteHeader[] internalIpBalancerRecordRouteHeader = new RecordRouteHeader[2]; 
	public RecordRouteHeader[] activeExternalHeader = new RecordRouteHeader[2];
	public RecordRouteHeader[] activeInternalHeader = new RecordRouteHeader[2];
    
	//stats
	public boolean gatherStatistics = true;
	public AtomicLong requestsProcessed = new AtomicLong(0);
    public AtomicLong responsesProcessed = new AtomicLong(0);
    private static final String[] METHODS_SUPPORTED = 
		{"REGISTER", "INVITE", "ACK", "BYE", "CANCEL", "MESSAGE", "INFO", "SUBSCRIBE", "NOTIFY", "UPDATE", "PUBLISH", "REFER", "PRACK", "OPTIONS"};
	private static final String[] RESPONSES_PER_CLASS_OF_SC = 
		{"1XX", "2XX", "3XX", "4XX", "5XX", "6XX", "7XX", "8XX", "9XX"};
    
	final Map<String, AtomicLong> requestsProcessedByMethod = new ConcurrentHashMap<String, AtomicLong>();
	final Map<String, AtomicLong> responsesProcessedByStatusCode = new ConcurrentHashMap<String, AtomicLong>();
    
    public boolean isTwoEntrypoints() {
    	return internalPort>0;
    }
    
    public static BalancerContext balancerContext = new BalancerContext();

    public BalancerContext() {
    	for (String method : METHODS_SUPPORTED) {
			requestsProcessedByMethod.put(method, new AtomicLong(0));
		}
    	for (String classOfSc : RESPONSES_PER_CLASS_OF_SC) {
			responsesProcessedByStatusCode.put(classOfSc, new AtomicLong(0));
		}
	}
	
    
    public BalancerAlgorithm balancerAlgorithm = null;
}
