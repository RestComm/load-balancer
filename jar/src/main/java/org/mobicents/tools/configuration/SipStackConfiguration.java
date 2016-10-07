package org.mobicents.tools.configuration;

import gov.nist.javax.sip.stack.LoadBalancerNioMessageProcessorFactory;

import java.util.Properties;

import org.mobicents.tools.sip.balancer.SIPBalancerValveProcessor;

public class SipStackConfiguration {
	
	private Properties sipStackProperies = new Properties();

	public SipStackConfiguration() 
	{
		sipStackProperies.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
		sipStackProperies.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", LoadBalancerNioMessageProcessorFactory.class.getName());
		sipStackProperies.setProperty("gov.nist.javax.sip.SIP_MESSAGE_VALVE", SIPBalancerValveProcessor.class.getName());
		sipStackProperies.setProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", "100");
		
	}
	public Properties getSipStackProperies() {
		return sipStackProperies;
	}

	public void setSipStackProperies(Properties sipStackProperies) {
		this.sipStackProperies = sipStackProperies;
	}

}
