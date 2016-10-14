package org.mobicents.tools.sip.balancer;

import javax.sip.SipProvider;

public class BalancerAppContent 
{
	private boolean isIpv6 = false;
	private SipProvider provider;
	
	public BalancerAppContent(SipProvider provider, boolean isIpv6) 
	{
		this.isIpv6=isIpv6;
		this.provider=provider;
	}

	public boolean isIpv6() 
	{
		return isIpv6;
	}

	public SipProvider getProvider() 
	{
		return provider;
	}	
}
