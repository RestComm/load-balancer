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

import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.ActiveStandbyAlgorithm;
import org.mobicents.tools.smpp.multiplexer.SmppToNodeRoundRobinAlgorithm;
import org.mobicents.tools.smpp.multiplexer.SmppToNodeSubmitToAllAlgorithm;
import org.mobicents.tools.smpp.multiplexer.SmppToProviderActiveStandbyAlgorithm;

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
		config.setHost("127.0.0.1");
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
	
	static LoadBalancerConfiguration getLbProperties(boolean isSsl, boolean terminateTLSTraffic, boolean isOneServer)
	{
		LoadBalancerConfiguration lbConfig = getLbProperties(isSsl,terminateTLSTraffic);
		if(isOneServer)
		{
			lbConfig.getSmppConfiguration().setRemoteServers("127.0.0.1:10021");
			lbConfig.getSmppConfiguration().setSmppToNodeAlgorithmClass(SmppToNodeRoundRobinAlgorithm.class.getName());
		}
		else
		{
			lbConfig.getSmppConfiguration().setRemoteServers("127.0.0.1:10021,127.0.0.1:10022");
			lbConfig.getSmppConfiguration().setSmppToProviderAlgorithmClass(SmppToProviderActiveStandbyAlgorithm.class.getName());
		}
		return lbConfig;
	}
	static LoadBalancerConfiguration getLbProperties(boolean isSsl, boolean terminateTLSTraffic)
	{
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSslConfiguration().setTerminateTLSTraffic(terminateTLSTraffic);
		//sip property
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTcpPort(5060);
		//smpp property
		lbConfig.getSmppConfiguration().setSmppHost("127.0.0.1");
		lbConfig.getSmppConfiguration().setSmppPort(2776);
		lbConfig.getSmppConfiguration().setRemoteServers("127.0.0.1:10021,127.0.0.1:10022,127.0.0.1:10023");
		lbConfig.getSmppConfiguration().setDefaultSessionCountersEnabled(true);
		lbConfig.getSmppConfiguration().setTimeoutResponse(3000);
		lbConfig.getSmppConfiguration().setTimeoutConnection(1000);
		lbConfig.getSmppConfiguration().setTimeoutEnquire(1000);
		lbConfig.getSmppConfiguration().setReconnectPeriod(500);
		lbConfig.getSmppConfiguration().setTimeoutConnectionCheckClientSide(2000);
		lbConfig.getSmppConfiguration().setTimeoutConnectionCheckServerSide(2000);
		lbConfig.getSmppConfiguration().setSmppToNodeAlgorithmClass(SmppToNodeSubmitToAllAlgorithm.class.getName());
		if(isSsl)
		{
			lbConfig.getSslConfiguration().setKeyStore(ConfigInit.class.getClassLoader().getResource("keystore").getFile());
			lbConfig.getSslConfiguration().setKeyStorePassword("123456");
			lbConfig.getSslConfiguration().setTrustStore(ConfigInit.class.getClassLoader().getResource("keystore").getFile());
			lbConfig.getSslConfiguration().setTrustStorePassword("123456");
			lbConfig.getSmppConfiguration().setSmppSslPort(2876);
			
		}
		
		return lbConfig;
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
