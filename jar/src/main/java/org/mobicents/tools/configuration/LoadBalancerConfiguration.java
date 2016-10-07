package org.mobicents.tools.configuration;

import java.util.Properties;

public class LoadBalancerConfiguration {

    private final CommonConfiguration commonConfiguration;
    private final SipConfiguration sipConfiguration;
    private final HttpConfiguration httpConfiguration;
    private final SmppConfiguration smppConfiguration;
    private final SslConfiguration sslConfiguration;
    private final SipStackConfiguration sipStackConfiguration;

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

}
