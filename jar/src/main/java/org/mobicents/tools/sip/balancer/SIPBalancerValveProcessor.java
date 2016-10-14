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

package org.mobicents.tools.sip.balancer;

import java.net.Inet6Address;
import java.net.InetAddress;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.SIPMessageValve;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.message.Response;

import org.apache.log4j.Logger;

public class SIPBalancerValveProcessor implements SIPMessageValve {
	private static final Logger logger = Logger.getLogger(SIPBalancerValveProcessor.class
            .getCanonicalName());
	BalancerRunner balancerRunner;
	
	public boolean processRequest(SIPRequest request, MessageChannel messageChannel) {
		// https://telestax.atlassian.net/browse/LB-36
		// catching all exceptions so it doesn't make JAIN SIP to fail
		try {
			SipProvider p = null;
			Boolean isIpv6=false;
			InetAddress address = InetAddress.getByName(messageChannel.getHost());
			if (address instanceof Inet6Address) 
			{
				isIpv6=true;
				p = balancerRunner.balancerContext.externalIpv6SipProvider;
				if(messageChannel.getPort() != balancerRunner.balancerContext.getExternalPortByTransport(messageChannel.getTransport(),isIpv6)) 
				{
					if(balancerRunner.balancerContext.isTwoEntrypoints())
						p = balancerRunner.balancerContext.internalIpv6SipProvider;
				}
			} 
			else 
			{
				p = balancerRunner.balancerContext.externalSipProvider;
				if(messageChannel.getPort() != balancerRunner.balancerContext.getExternalPortByTransport(messageChannel.getTransport(),isIpv6)) 
				{
					if(balancerRunner.balancerContext.isTwoEntrypoints())
						p = balancerRunner.balancerContext.internalSipProvider;
				}
			}
			
			RequestEvent event = new RequestEvent(new BalancerAppContent(p,isIpv6), null, null, request);			
			balancerRunner.balancerContext.forwarder.processRequest(event);
		} catch (Exception e) {
			logger.error("A Problem happened in the BalancerValve on request " + request, e);
			return false;
		}
		return false;
	}

	public boolean processResponse(Response response,
			MessageChannel messageChannel) {
		// https://telestax.atlassian.net/browse/LB-36
		// catching all exceptions so it doesn't make JAIN SIP to fail
		try {
			SipProvider p = null;
			Boolean isIpv6=false;
			InetAddress address = InetAddress.getByName(messageChannel.getHost());
			if (address instanceof Inet6Address) 
			{
				isIpv6=true;
				p = balancerRunner.balancerContext.externalIpv6SipProvider;
				if(messageChannel.getPort() != balancerRunner.balancerContext.getExternalPortByTransport(messageChannel.getTransport(),isIpv6)) 
				{
					if(balancerRunner.balancerContext.isTwoEntrypoints())
						p = balancerRunner.balancerContext.internalIpv6SipProvider;
				}
			} 
			else 
			{
					p = balancerRunner.balancerContext.externalSipProvider;
					if(messageChannel.getPort() != balancerRunner.balancerContext.getExternalPortByTransport(messageChannel.getTransport(),isIpv6)) 
					{
						if(balancerRunner.balancerContext.isTwoEntrypoints())
							p = balancerRunner.balancerContext.internalSipProvider;
					}
			}
			
			ResponseEvent event = new ResponseEvent(new BalancerAppContent(p,isIpv6), null, null, response);			
			balancerRunner.balancerContext.forwarder.processResponse(event);
		} catch (Exception e) {
			logger.error("A Problem happened in the BalancerValve on response " + response, e);
			return false;
		}
		return false;
	}

	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	public void init(SipStack stack) {
		
		
	}
}