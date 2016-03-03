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
package gov.nist.javax.sip.stack;

import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.MessageProcessor;
import gov.nist.javax.sip.stack.MessageProcessorFactory;
import gov.nist.javax.sip.stack.SIPTransactionStack;

import java.io.IOException;
import java.net.InetAddress;

import javax.sip.ListeningPoint;

/**
 * Load balancer wrapper for NIO implementation
 * 
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 *
 */

public class LoadBalancerNioMessageProcessorFactory implements MessageProcessorFactory {

    @Override  
    public MessageProcessor createMessageProcessor(
                SIPTransactionStack sipStack, InetAddress ipAddress, int port,
                String transport) throws IOException {
            if (transport.equalsIgnoreCase(ListeningPoint.UDP)) {
            	LoadBalancerUDPMessageProcessor udpMessageProcessor = new LoadBalancerUDPMessageProcessor(ipAddress, sipStack, port);         
                sipStack.udpFlag = true;
                return udpMessageProcessor;
            } else if (transport.equalsIgnoreCase(ListeningPoint.TCP)) {
            	LoadBalancerNioTcpMessageProcessor nioTcpMessageProcessor = new LoadBalancerNioTcpMessageProcessor(ipAddress, sipStack, port);         
                // this.tcpFlag = true;
                return nioTcpMessageProcessor;
            } else if (transport.equalsIgnoreCase(ListeningPoint.TLS)) {
            	LoadBalancerNioTlsMessageProcessor tlsMessageProcessor = new LoadBalancerNioTlsMessageProcessor(ipAddress, sipStack, port);         
                // this.tlsFlag = true;
                return tlsMessageProcessor;
            } else if (transport.equalsIgnoreCase(ListeningPoint.SCTP)) {

                // Need Java 7 for this, so these classes are packaged in a separate
                // jar
                // Try to load it indirectly, if fails report an error
                try {
                    Class<?> mpc = ClassLoader.getSystemClassLoader().loadClass(
                            "gov.nist.javax.sip.stack.sctp.SCTPMessageProcessor");
                    MessageProcessor mp = (MessageProcessor) mpc.newInstance();
                    mp.initialize(ipAddress, port, sipStack);               
                    return mp;
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(
                            "SCTP not supported (needs Java 7 and SCTP jar in classpath)");
                } catch (InstantiationException ie) {
                    throw new IllegalArgumentException("Error initializing SCTP",
                            ie);
                } catch (IllegalAccessException ie) {
                    throw new IllegalArgumentException("Error initializing SCTP",
                            ie);
                }
            } else if (transport.equalsIgnoreCase(ListeningPointExt.WS)) {
            	if("true".equals(((SipStackImpl)sipStack).getConfigurationProperties().getProperty("gov.nist.javax.sip.USE_TLS_GATEWAY"))) {
            		MessageProcessor mp = new LoadBalancerNioTlsWebSocketMessageProcessor(ipAddress, sipStack, port, "WS");
            		return mp;
            	} else {
            		MessageProcessor mp = new LoadBalancerNioWebSocketMessageProcessor(ipAddress, sipStack, port, "WS");
            		return mp;
            	}
            	 
            } else if (transport.equalsIgnoreCase("WSS")) {

            	if("true".equals(((SipStackImpl)sipStack).getConfigurationProperties().getProperty("gov.nist.javax.sip.USE_TLS_GATEWAY"))) {
            		MessageProcessor mp = new LoadBalancerNioWebSocketMessageProcessor(ipAddress, sipStack, port, "WSS");
            		return mp;
            	} else {
            		MessageProcessor mp = new LoadBalancerNioTlsWebSocketMessageProcessor(ipAddress, sipStack, port, "WSS");
            		return mp;
            	}
            } else {
            	throw new IllegalArgumentException("bad transport");
            }
     }
}
