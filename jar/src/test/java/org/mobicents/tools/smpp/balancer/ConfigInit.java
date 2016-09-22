/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.tools.smpp.balancer;

import java.util.Properties;

import org.mobicents.tools.sip.balancer.ActiveStandbyAlgorithm;
import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.ssl.SslConfiguration;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.SmppInvalidArgumentException;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class ConfigInit {

	static SmppServerConfiguration getSmppServerConfiguration(int i, boolean isSslServer)
	{
		SmppServerConfiguration config = new SmppServerConfiguration();
		config.setName("SMPP Server "+i);
		config.setMaxConnectionSize(10);
		config.setNonBlockingSocketsEnabled(true);
		config.setDefaultRequestExpiryTimeout(30000);
		config.setDefaultWindowMonitorInterval(15000);
		config.setDefaultWindowSize(5);
		config.setDefaultWindowWaitTimeout(config.getDefaultRequestExpiryTimeout());
		config.setDefaultSessionCountersEnabled(true);
		config.setJmxEnabled(true);
		config.setPort(10021 + i);
		config.setHost("127.0.0.1");
		if(isSslServer)
		{
			SslConfiguration sslConfig = new SslConfiguration();			
	        sslConfig.setKeyStorePath(ConfigInit.class.getClassLoader().getResource("keystore").getFile());
	        sslConfig.setKeyStorePassword("123456");
	        sslConfig.setTrustStorePath(ConfigInit.class.getClassLoader().getResource("keystore").getFile());
	        sslConfig.setTrustStorePassword("123456");
	        config.setUseSsl(true);
	        config.setSslConfiguration(sslConfig);
		}
		return config;
	}
	
	static Properties getLbProperties(boolean isSsl, boolean terminateTLSTraffic, boolean isOneServer)
	{
		Properties properties = getLbProperties(isSsl,terminateTLSTraffic);
		if(isOneServer)
		{
		properties.setProperty("remoteServers","127.0.0.1:10021");
		properties.setProperty("isUseRrSendSmppRequestToClient","true");
		}
		else
		{
			properties.setProperty("remoteServers","127.0.0.1:10021,127.0.0.1:10022");
			properties.setProperty("remoteServers","127.0.0.1:10021,127.0.0.1:10022");
			properties.setProperty("algorithmClass", ActiveStandbyAlgorithm.class.getName());
		}
		return properties;
	}
	static Properties getLbProperties(boolean isSsl, boolean terminateTLSTraffic)
	{
		Properties properties = new Properties();
		properties.setProperty("terminateTLSTraffic",""+terminateTLSTraffic);
		//sip property
		properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
		properties.setProperty("host", "127.0.0.1");
		properties.setProperty("externalUdpPort", "5060");
		properties.setProperty("internalUdpPort", "5065");
		//smpp property
		properties.setProperty("smppName","SMPP Load Balancer");
		properties.setProperty("smppHost","127.0.0.1");
		properties.setProperty("smppPort","2776");
		properties.setProperty("remoteServers","127.0.0.1:10021,127.0.0.1:10022,127.0.0.1:10023");
		properties.setProperty("maxConnectionSize","10");
		properties.setProperty("nonBlockingSocketsEnabled","true");
		properties.setProperty("defaultSessionCountersEnabled","true");
		properties.setProperty("timeoutResponse","3000");
		properties.setProperty("timeoutConnection","1000");
		properties.setProperty("timeoutEnquire","5000");
		properties.setProperty("reconnectPeriod","500");
		properties.setProperty("timeoutConnectionCheckClientSide","1000");
		properties.setProperty("timeoutConnectionCheckServerSide","1000");
		if(isSsl)
		{
			properties.setProperty("javax.net.ssl.keyStore",ConfigInit.class.getClassLoader().getResource("keystore").getFile());
			properties.setProperty("javax.net.ssl.keyStorePassword","123456");
			properties.setProperty("javax.net.ssl.trustStore",ConfigInit.class.getClassLoader().getResource("keystore").getFile());
			properties.setProperty("javax.net.ssl.trustStorePassword","123456");
			properties.setProperty("smppSslPort","2876");
			
		}
		
		return properties;
	}
	
	static SmppSessionConfiguration getSmppSessionConfiguration(int i, boolean isSslClient)
	{
		SmppSessionConfiguration config  = new SmppSessionConfiguration();
		config.setWindowSize(1);
		config.setName("Client " + i);
		config.setType(SmppBindType.TRANSCEIVER);
		config.setHost("127.0.0.1");
		
		config.setConnectTimeout(10000);
		config.setSystemId("RestComm");
		config.setPassword("password");
		config.getLoggingOptions().setLogBytes(true);
        // to enable monitoring (request expiration)
		config.setRequestExpiryTimeout(30000);
		config.setWindowMonitorInterval(15000);
		config.setCountersEnabled(true);
		if(isSslClient)
		{
			config.setPort(2876);
			SslConfiguration sslConfig = new SslConfiguration();
	        sslConfig.setTrustAll(true);
	        sslConfig.setValidateCerts(true);
	        sslConfig.setValidatePeerCerts(true);
	        config.setSslConfiguration(sslConfig);
	        config.setUseSsl(true);
		}else
		{
			config.setPort(2776);
		}
		
		return config;
	}

	static SubmitSm getSubmitSm() throws SmppInvalidArgumentException
	{
		String text160 = "Hello world!";
        byte[] textBytes = CharsetUtil.encode(text160, CharsetUtil.CHARSET_GSM);
        SubmitSm submit = new SubmitSm();
        submit.setSourceAddress(new Address((byte)0x03, (byte)0x00, "40404"));
        submit.setDestAddress(new Address((byte)0x01, (byte)0x01, "44555519205"));
		submit.setShortMessage(textBytes);
        return submit;
	}
	
}
