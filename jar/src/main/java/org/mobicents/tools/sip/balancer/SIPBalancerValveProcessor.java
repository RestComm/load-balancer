package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.SIPMessageValve;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.message.Response;

public class SIPBalancerValveProcessor implements SIPMessageValve {
	BalancerRunner balancerRunner;
	
	public boolean processRequest(SIPRequest request,
			MessageChannel messageChannel) {
		SipProvider p = balancerRunner.balancerContext.externalSipProvider;
		if(messageChannel.getPort() != balancerRunner.balancerContext.externalPort) {
			if(balancerRunner.balancerContext.isTwoEntrypoints())
				p = balancerRunner.balancerContext.internalSipProvider;
		}
		
		RequestEvent event = new RequestEvent(p, null, null, request);
		balancerRunner.balancerContext.forwarder.processRequest(event);
		return false;
	}

	public boolean processResponse(Response response,
			MessageChannel messageChannel) {
		SipProvider p = balancerRunner.balancerContext.externalSipProvider;
		if(messageChannel.getPort() != balancerRunner.balancerContext.externalPort) {
			if(balancerRunner.balancerContext.isTwoEntrypoints())
				p = balancerRunner.balancerContext.internalSipProvider;
		}
		ResponseEvent event = new ResponseEvent(p, null, null, response);
		balancerRunner.balancerContext.forwarder.processResponse(event);
		return false;
	}

	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	public void init(SipStack stack) {
		
		
	}
}
