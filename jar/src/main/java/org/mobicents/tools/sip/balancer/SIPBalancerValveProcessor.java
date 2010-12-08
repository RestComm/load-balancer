package org.mobicents.tools.sip.balancer;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.SIPMessageValve;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.message.Response;

public class SIPBalancerValveProcessor implements SIPMessageValve {
	
	
	@Override
	public boolean processRequest(SIPRequest request,
			MessageChannel messageChannel) {
		SipProvider p = BalancerContext.balancerContext.externalSipProvider;
		if(messageChannel.getPort() != BalancerContext.balancerContext.externalPort) {
			if(BalancerContext.balancerContext.isTwoEntrypoints())
				p = BalancerContext.balancerContext.internalSipProvider;
		}
		
		RequestEvent event = new RequestEvent(p, null, null, request);
		BalancerContext.balancerContext.forwarder.processRequest(event);
		return false;
	}

	@Override
	public boolean processResponse(Response response,
			MessageChannel messageChannel) {
		SipProvider p = BalancerContext.balancerContext.externalSipProvider;
		if(messageChannel.getPort() != BalancerContext.balancerContext.externalPort) {
			if(BalancerContext.balancerContext.isTwoEntrypoints())
				p = BalancerContext.balancerContext.internalSipProvider;
		}
		ResponseEvent event = new ResponseEvent(p, null, null, response);
		BalancerContext.balancerContext.forwarder.processResponse(event);
		return false;
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void init(SipStack stack) {
		// TODO Auto-generated method stub
		
	}
}
