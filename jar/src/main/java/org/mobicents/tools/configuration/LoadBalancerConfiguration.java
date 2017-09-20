package org.mobicents.tools.configuration;

import org.mobicents.tools.heartbeat.api.HeartbeatConfig;

public class LoadBalancerConfiguration {

    private final CommonConfiguration commonConfiguration;
    private final SipConfiguration sipConfiguration;
    private final HttpConfiguration httpConfiguration;
    private final SmppConfiguration smppConfiguration;
    private final SslConfiguration sslConfiguration;
    private final SipStackConfiguration sipStackConfiguration;
    private HeartbeatConfig heartbeatConfiguration;
    private String heartbeatConfigurationClass;
    
    public LoadBalancerConfiguration() {
        this.commonConfiguration = new CommonConfiguration();
        this.sipConfiguration = new SipConfiguration();
        this.httpConfiguration = new HttpConfiguration();
        this.smppConfiguration = new SmppConfiguration();
        this.sslConfiguration = new SslConfiguration();
        this.sipStackConfiguration = new SipStackConfiguration();
      }

    public CommonConfiguration getCommonConfiguration() {
        return commonConfiguration;
    }

    public SipConfiguration getSipConfiguration() {
        return sipConfiguration;
    }

    public HttpConfiguration getHttpConfiguration() {
        return httpConfiguration;
    }

    public SmppConfiguration getSmppConfiguration() {
        return smppConfiguration;
    }

	public SslConfiguration getSslConfiguration() {
		return sslConfiguration;
	}

	public SipStackConfiguration getSipStackConfiguration() {
		return sipStackConfiguration;
	}

	public void setHeartbeatConfigurationClass(String configClass) {
		this.heartbeatConfigurationClass=configClass;
	}
	
	public String getHeartbeatConfigurationClass()
	{
		return this.heartbeatConfigurationClass;
	}

	public HeartbeatConfig getHeartbeatConfiguration() {
		return heartbeatConfiguration;
	}

	public void setHeartbeatConfiguration(HeartbeatConfig heartbeatConfiguration) {
		this.heartbeatConfiguration = heartbeatConfiguration;
	}
}
