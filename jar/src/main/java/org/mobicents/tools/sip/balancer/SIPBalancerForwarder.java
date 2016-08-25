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

import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.ResponseExt;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.LoadBalancerNioMessageProcessorFactory;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.Transaction;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.TelURL;
import javax.sip.address.URI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderAddress;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;

/**
 * A transaction stateful UDP Forwarder that listens at a port and forwards to multiple
 * outbound addresses. It keeps a timer thread around that pings the list of
 * proxy servers and sends to the first proxy server.
 * 
 * It uses double record routing to be able to listen on one transport and sends on another transport
 * or allows support for multihoming.
 * 
 * @author M. Ranganathan
 * @author baranowb 
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A>
 */
public class SIPBalancerForwarder implements SipListener {
    private static final Logger logger = Logger.getLogger(SIPBalancerForwarder.class
            .getCanonicalName());

    /*
     * Those parameters is to indicate to the SIP Load Balancer, from which node comes from the request
     * so that it can stick the Call Id to this node and correctly route the subsequent requests. 
     */
    public static final String ROUTE_PARAM_NODE_HOST = "node_host";

    public static final String ROUTE_PARAM_NODE_PORT = "node_port";

    public static final String ROUTE_PARAM_NODE_VERSION = "version";

    public static final int UDP = 0;
    public static final int TCP = 1;
    public static final int TLS = 2;
    public static final int WS = 3;
    public static final int WSS = 4;

    BalancerRunner balancerRunner;

    protected static final HashSet<String> dialogCreationMethods=new HashSet<String>(2);

    static{
        dialogCreationMethods.add(Request.INVITE);
        dialogCreationMethods.add(Request.SUBSCRIBE);
    }      

    public NodeRegister register;

    protected String[] extraServerAddresses;
    protected int[] extraServerPorts;

    public SIPBalancerForwarder(Properties properties, BalancerRunner balancerRunner, NodeRegister register) throws IllegalStateException{
        super();
        this.balancerRunner = balancerRunner;
        this.balancerRunner.balancerContext.forwarder = this;
        this.balancerRunner.balancerContext.properties = properties;
        this.register = register;		
    }

    public void start() {
    	
		balancerRunner.balancerContext.isSendTrying = Boolean.parseBoolean(balancerRunner.balancerContext.properties.getProperty("isSendTrying","true"));
    	balancerRunner.balancerContext.sipHeaderAffinityKey = balancerRunner.balancerContext.properties.getProperty("sipHeaderAffinityKey","Call-ID");

        SipFactory sipFactory = null;
        balancerRunner.balancerContext.sipStack = null;

        balancerRunner.balancerContext.host = balancerRunner.balancerContext.properties.getProperty("host");   
        balancerRunner.balancerContext.internalHost = balancerRunner.balancerContext.properties.getProperty("internalHost",balancerRunner.balancerContext.host); 
        balancerRunner.balancerContext.externalHost = balancerRunner.balancerContext.properties.getProperty("externalHost",balancerRunner.balancerContext.host);
        
        if(balancerRunner.balancerContext.properties.getProperty("externalUdpPort") != null)
        	balancerRunner.balancerContext.externalUdpPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalUdpPort"));
        if(balancerRunner.balancerContext.properties.getProperty("externalTcpPort") != null)
        	balancerRunner.balancerContext.externalTcpPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalTcpPort"));
        if(balancerRunner.balancerContext.properties.getProperty("externalTlsPort") != null)
        	balancerRunner.balancerContext.externalTlsPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalTlsPort"));
        if(balancerRunner.balancerContext.properties.getProperty("externalWsPort") != null)
        	balancerRunner.balancerContext.externalWsPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalWsPort"));
        if(balancerRunner.balancerContext.properties.getProperty("externalWssPort") != null)
        	balancerRunner.balancerContext.externalWssPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalWssPort"));
        
        if(balancerRunner.balancerContext.properties.getProperty("internalUdpPort") != null) 
            balancerRunner.balancerContext.internalUdpPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalUdpPort"));
        if(balancerRunner.balancerContext.properties.getProperty("internalTcpPort") != null) 
            balancerRunner.balancerContext.internalTcpPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalTcpPort"));
        if(balancerRunner.balancerContext.properties.getProperty("internalTlsPort") != null) 
            balancerRunner.balancerContext.internalTlsPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalTlsPort"));
        if(balancerRunner.balancerContext.properties.getProperty("internalWsPort") != null) 
            balancerRunner.balancerContext.internalWsPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalWsPort"));
        if(balancerRunner.balancerContext.properties.getProperty("internalWssPort") != null) 
            balancerRunner.balancerContext.internalWssPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalWssPort"));
        
        
        balancerRunner.balancerContext.externalIpLoadBalancerAddress = balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerAddress");
        balancerRunner.balancerContext.internalIpLoadBalancerAddress = balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerAddress");

        if(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerUdpPort") != null) {
          balancerRunner.balancerContext.externalLoadBalancerUdpPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerUdpPort"));
          }
        if(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerTcpPort") != null) {
            balancerRunner.balancerContext.externalLoadBalancerTcpPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerTcpPort"));
          }
        if(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerTlsPort") != null) {
            balancerRunner.balancerContext.externalLoadBalancerTlsPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerTlsPort"));
          }
        if(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerWsPort") != null) {
            balancerRunner.balancerContext.externalLoadBalancerWsPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerWsPort"));
          }
        if(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerWssPort") != null) {
            balancerRunner.balancerContext.externalLoadBalancerWssPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("externalIpLoadBalancerWssPort"));
          }
        
        if(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerUdpPort") != null) {
            balancerRunner.balancerContext.internalLoadBalancerUdpPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerUdpPort"));
        }
        if(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerTcpPort") != null) {
            balancerRunner.balancerContext.internalLoadBalancerTcpPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerTcpPort"));
        }
        if(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerTlsPort") != null) {
            balancerRunner.balancerContext.internalLoadBalancerTlsPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerTlsPort"));
        }
        if(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerWsPort") != null) {
            balancerRunner.balancerContext.internalLoadBalancerWsPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerWsPort"));
        }
        if(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerWssPort") != null) {
            balancerRunner.balancerContext.internalLoadBalancerWssPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("internalIpLoadBalancerWssPort"));
        }

        if(balancerRunner.balancerContext.isTwoEntrypoints()) {
            if(balancerRunner.balancerContext.externalLoadBalancerUdpPort > 0) {
                if(balancerRunner.balancerContext.internalLoadBalancerUdpPort <=0) {
                    throw new RuntimeException("External IP load balancer specified UDP port, but not internal load balancer UDP port");
                }                
            }
            if(balancerRunner.balancerContext.externalLoadBalancerTcpPort > 0) {
                if(balancerRunner.balancerContext.internalLoadBalancerTcpPort <=0) {
                    throw new RuntimeException("External IP load balancer specified TCP port, but not internal load balancer TCP port");
                }                
            }
            if(balancerRunner.balancerContext.externalLoadBalancerTlsPort > 0) {
                if(balancerRunner.balancerContext.internalLoadBalancerTlsPort <=0) {
                    throw new RuntimeException("External IP load balancer specified TLS port, but not internal load balancer TLS port");
                }                
            }
            if(balancerRunner.balancerContext.externalLoadBalancerWsPort > 0) {
                if(balancerRunner.balancerContext.internalLoadBalancerWsPort <=0) {
                    throw new RuntimeException("External IP load balancer specified WS port, but not internal load balancer WS port");
                }                
            }
            if(balancerRunner.balancerContext.externalLoadBalancerWssPort > 0) {
                if(balancerRunner.balancerContext.internalLoadBalancerWssPort <=0) {
                    throw new RuntimeException("External IP load balancer specified WSS port, but not internal load balancer WSS port");
                }                
            }
        }
        
        if(balancerRunner.balancerContext.externalIpLoadBalancerAddress != null) {
            if(balancerRunner.balancerContext.externalLoadBalancerUdpPort<=0&&
            		balancerRunner.balancerContext.externalLoadBalancerTcpPort<=0&&
            		balancerRunner.balancerContext.externalLoadBalancerTlsPort<=0&&
            		balancerRunner.balancerContext.externalLoadBalancerWsPort<=0&&
            		balancerRunner.balancerContext.externalLoadBalancerWssPort<=0
            		) {
                throw new RuntimeException("External load balancer address is specified, but none externalIpLoadBalancerPort ");
            }
        }

        if(balancerRunner.balancerContext.internalIpLoadBalancerAddress != null) {
            if(balancerRunner.balancerContext.internalLoadBalancerUdpPort<=0&&
            		balancerRunner.balancerContext.internalLoadBalancerTcpPort<=0&&
            		balancerRunner.balancerContext.internalLoadBalancerTlsPort<=0&&
            		balancerRunner.balancerContext.internalLoadBalancerWsPort<=0&&
            		balancerRunner.balancerContext.internalLoadBalancerWssPort<=0) {
                throw new RuntimeException("Internal load balancer address is specified, but none internalIpLoadBalancerPort");
            }
        }

        String extraServerNodesString = balancerRunner.balancerContext.properties.getProperty("extraServerNodes");
        String performanceTestingMode = balancerRunner.balancerContext.properties.getProperty("performanceTestingMode");
        if(extraServerNodesString != null) {
            ArrayList<SIPNode> extraServerNodes = new ArrayList<SIPNode>();
            extraServerAddresses = extraServerNodesString.split(",");
            extraServerPorts = new int[extraServerAddresses.length];
            for(int q=0; q<extraServerAddresses.length; q++) {
                int indexOfPort = extraServerAddresses[q].indexOf(':');
                if(indexOfPort > 0) {
                    extraServerPorts[q] = Integer.parseInt(extraServerAddresses[q].substring(indexOfPort + 1, extraServerAddresses[q].length()));
                    extraServerAddresses[q] = extraServerAddresses[q].substring(0, indexOfPort);
                } else {
                    extraServerPorts[q] = 5060;
                }
                ExtraServerNode extraServerNode = new ExtraServerNode("ExtraServerNode"+q+"-"+extraServerAddresses[q]+":"+extraServerPorts[q], extraServerAddresses[q]);
                HashMap<String, Serializable> properties = new HashMap<String, Serializable>();
                properties.put("udpPort", extraServerPorts[q]);
                properties.put("tcpPort", extraServerPorts[q]);
                properties.put("version","0");
                extraServerNode.setProperties(properties);
                extraServerNodes.add(extraServerNode);
                logger.info("Extra Server: " + extraServerAddresses[q] + ":" + extraServerPorts[q]);
            }
            if(performanceTestingMode.equalsIgnoreCase("true")){
                register.handlePingInRegister(extraServerNodes);
                logger.info("Extra Servers registered as active nodes!");
            }
        }

        try {
            // Create SipStack object
            sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");
            
            balancerRunner.balancerContext.properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", LoadBalancerNioMessageProcessorFactory.class.getName());    		
            balancerRunner.balancerContext.properties.setProperty("gov.nist.javax.sip.SIP_MESSAGE_VALVE", SIPBalancerValveProcessor.class.getName());
            if(balancerRunner.balancerContext.properties.getProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE") == null) {
                balancerRunner.balancerContext.properties.setProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", "100");
            }

            balancerRunner.balancerContext.sipStack = (SipStackImpl) sipFactory.createSipStack(balancerRunner.balancerContext.properties);

        } catch (PeerUnavailableException pue) {
            // could not find
            // gov.nist.jain.protocol.ip.sip.SipStackImpl
            // in the classpath
            throw new IllegalStateException("Cant create stack due to["+pue.getMessage()+"]", pue);
        }
    
        try {
            balancerRunner.balancerContext.headerFactory = sipFactory.createHeaderFactory();
            boolean usePrettyEncoding = Boolean.valueOf(balancerRunner.balancerContext.properties.getProperty("usePrettyEncoding", "false"));
            if(usePrettyEncoding) {
                ((HeaderFactoryImpl)balancerRunner.balancerContext.headerFactory).setPrettyEncoding(true);
            }
            balancerRunner.balancerContext.addressFactory = sipFactory.createAddressFactory();
            balancerRunner.balancerContext.messageFactory = sipFactory.createMessageFactory();

            ListeningPoint externalLpUdp=null,externalLpTcp=null,externalLpTls=null,externalLpWs=null,externalLpWss=null;
            ArrayList <ListeningPoint> lpsExt = new ArrayList<ListeningPoint>();
            
            if(balancerRunner.balancerContext.externalUdpPort != 0)
            {
            	externalLpUdp = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.externalHost, balancerRunner.balancerContext.externalUdpPort, ListeningPoint.UDP);
            	lpsExt.add(externalLpUdp);
            }
            if(balancerRunner.balancerContext.externalTcpPort != 0)
            {
            	externalLpTcp = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.externalHost, balancerRunner.balancerContext.externalTcpPort, ListeningPoint.TCP);
            	lpsExt.add(externalLpTcp);
            }
            if(balancerRunner.balancerContext.externalTlsPort != 0)
            {
            	externalLpTls = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.externalHost, balancerRunner.balancerContext.externalTlsPort, ListeningPoint.TLS);
            	lpsExt.add(externalLpTls);
            }
            if(balancerRunner.balancerContext.externalWsPort != 0)
            {
            	externalLpWs = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.externalHost, balancerRunner.balancerContext.externalWsPort, ListeningPointExt.WS);
            	lpsExt.add(externalLpWs);
            }
            if(balancerRunner.balancerContext.externalWssPort != 0)
            {
            	externalLpWss = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.externalHost, balancerRunner.balancerContext.externalWssPort, ListeningPointExt.WSS);
            	lpsExt.add(externalLpWss);
            }
           
            balancerRunner.balancerContext.externalSipProvider = balancerRunner.balancerContext.sipStack.createSipProvider(lpsExt.remove(0));
            for (ListeningPoint temp : lpsExt)
            	balancerRunner.balancerContext.externalSipProvider.addListeningPoint(temp);
            
            balancerRunner.balancerContext.externalSipProvider.addSipListener(this);
            
  
            ListeningPoint internalLpUdp = null,internalLpTcp=null,internalLpTls=null,internalLpWs=null,internalLpWss=null;
            if(balancerRunner.balancerContext.isTwoEntrypoints()) {
            	
            	ArrayList <ListeningPoint> lpsInt = new ArrayList<ListeningPoint>();
            	if(balancerRunner.balancerContext.internalUdpPort != 0)
            	{
            		internalLpUdp = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.internalHost, balancerRunner.balancerContext.internalUdpPort, ListeningPoint.UDP);
            		lpsInt.add(internalLpUdp);
            	}
            	if(balancerRunner.balancerContext.internalTcpPort != 0)
            	{
            		internalLpTcp = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.internalHost, balancerRunner.balancerContext.internalTcpPort, ListeningPoint.TCP);
            		lpsInt.add(internalLpTcp);
            	}
            	if(balancerRunner.balancerContext.internalTlsPort != 0)
            	{
            		internalLpTls = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.internalHost, balancerRunner.balancerContext.internalTlsPort, ListeningPoint.TLS);
            		lpsInt.add(internalLpTls);
            	}
            	if(balancerRunner.balancerContext.internalWsPort != 0)
            	{
            		internalLpWs = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.internalHost, balancerRunner.balancerContext.internalWsPort, ListeningPointExt.WS);
            		lpsInt.add(internalLpWs);
            	}
            	if(balancerRunner.balancerContext.internalWssPort != 0)
            	{
            		internalLpWss = balancerRunner.balancerContext.sipStack.createListeningPoint(balancerRunner.balancerContext.internalHost, balancerRunner.balancerContext.internalWssPort, ListeningPointExt.WSS);
            		lpsInt.add(internalLpWss);
            	}

                balancerRunner.balancerContext.internalSipProvider = balancerRunner.balancerContext.sipStack.createSipProvider(lpsInt.remove(0));
                for (ListeningPoint temp : lpsInt)
                	balancerRunner.balancerContext.internalSipProvider.addListeningPoint(temp);

                balancerRunner.balancerContext.internalSipProvider.addSipListener(this);
            }
                
 
            //Creating the Record Route headers on startup since they can't be changed at runtime and this will avoid the overhead of creating them
            //for each request

            //We need to use double record (better option than record route rewriting) routing otherwise it is impossible :
            //a) to forward BYE from the callee side to the caller
            //b) to support different transports	
            if(externalLpUdp!=null)
            {
                SipURI externalLocalUri = balancerRunner.balancerContext.addressFactory
                        .createSipURI(null, externalLpUdp.getIPAddress());
                externalLocalUri.setPort(externalLpUdp.getPort());
                externalLocalUri.setTransportParam(ListeningPoint.UDP);
                //See RFC 3261 19.1.1 for lr parameter
                externalLocalUri.setLrParam();
                Address externalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(externalLocalUri);
                externalLocalAddress.setURI(externalLocalUri);

                if(logger.isDebugEnabled()) {
                    logger.debug("adding Record Router Header :"+externalLocalAddress);
                }                    
                balancerRunner.balancerContext.externalRecordRouteHeader[UDP] = balancerRunner.balancerContext.headerFactory
                        .createRecordRouteHeader(externalLocalAddress);    
            }
            if(externalLpTcp!=null)
            {
                SipURI externalLocalUri = balancerRunner.balancerContext.addressFactory
                        .createSipURI(null, externalLpTcp.getIPAddress());
                externalLocalUri.setPort(externalLpTcp.getPort());
                externalLocalUri.setTransportParam(ListeningPoint.TCP);
                //See RFC 3261 19.1.1 for lr parameter
                externalLocalUri.setLrParam();
                Address externalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(externalLocalUri);
                externalLocalAddress.setURI(externalLocalUri);

                if(logger.isDebugEnabled()) {
                    logger.debug("adding Record Router Header :"+externalLocalAddress);
                }                    
                balancerRunner.balancerContext.externalRecordRouteHeader[TCP] = balancerRunner.balancerContext.headerFactory
                        .createRecordRouteHeader(externalLocalAddress);    
            }
            if(externalLpWs!=null)
            {
                SipURI externalLocalUri = balancerRunner.balancerContext.addressFactory
                        .createSipURI(null, externalLpWs.getIPAddress());
                externalLocalUri.setPort(externalLpWs.getPort());
                externalLocalUri.setTransportParam(ListeningPointExt.WS);
                //See RFC 3261 19.1.1 for lr parameter
                externalLocalUri.setLrParam();
                Address externalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(externalLocalUri);
                externalLocalAddress.setURI(externalLocalUri);

                if(logger.isDebugEnabled()) {
                    logger.debug("adding Record Router Header :"+externalLocalAddress);
                }                    
                balancerRunner.balancerContext.externalRecordRouteHeader[WS] = balancerRunner.balancerContext.headerFactory
                        .createRecordRouteHeader(externalLocalAddress);    
            }
            if(externalLpTls!=null)
            {
                SipURI externalLocalUri = balancerRunner.balancerContext.addressFactory
                        .createSipURI(null, externalLpTls.getIPAddress());
                externalLocalUri.setPort(externalLpTls.getPort());
                externalLocalUri.setTransportParam(ListeningPoint.TLS);
                //See RFC 3261 19.1.1 for lr parameter
                externalLocalUri.setLrParam();
                Address externalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(externalLocalUri);
                externalLocalAddress.setURI(externalLocalUri);

                if(logger.isDebugEnabled()) {
                    logger.debug("adding Record Router Header :"+externalLocalAddress);
                }                    
                balancerRunner.balancerContext.externalRecordRouteHeader[TLS] = balancerRunner.balancerContext.headerFactory
                        .createRecordRouteHeader(externalLocalAddress);    
            }
            if(externalLpWss!=null)
            {
            	SipURI externalLocalUri = balancerRunner.balancerContext.addressFactory
                        .createSipURI(null, externalLpWss.getIPAddress());
                externalLocalUri.setPort(externalLpWss.getPort());
                externalLocalUri.setTransportParam(ListeningPointExt.WSS);
                //See RFC 3261 19.1.1 for lr parameter
                externalLocalUri.setLrParam();
                Address externalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(externalLocalUri);
                externalLocalAddress.setURI(externalLocalUri);

                if(logger.isDebugEnabled()) {
                    logger.debug("adding Record Router Header :"+externalLocalAddress);
                }                    
                balancerRunner.balancerContext.externalRecordRouteHeader[WSS] = balancerRunner.balancerContext.headerFactory
                        .createRecordRouteHeader(externalLocalAddress);
            }

            if(balancerRunner.balancerContext.isTwoEntrypoints()) {
            	if(internalLpUdp!=null)
                {
                    SipURI internalLocalUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, internalLpUdp.getIPAddress());
                    internalLocalUri.setPort(internalLpUdp.getPort());
                    internalLocalUri.setTransportParam(ListeningPoint.UDP);
                    //See RFC 3261 19.1.1 for lr parameter
                    internalLocalUri.setLrParam();
                    Address internalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(internalLocalUri);
                    internalLocalAddress.setURI(internalLocalUri);
                    if(logger.isDebugEnabled()) {
                        logger.debug("adding Record Router Header :"+internalLocalAddress);
                    }                    
                    balancerRunner.balancerContext.internalRecordRouteHeader[UDP] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(internalLocalAddress);  
                }
            	if(internalLpTcp!=null)
                {
                    SipURI internalLocalUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, internalLpTcp.getIPAddress());
                    internalLocalUri.setPort(internalLpTcp.getPort());
                    internalLocalUri.setTransportParam(ListeningPoint.TCP);
                    //See RFC 3261 19.1.1 for lr parameter
                    internalLocalUri.setLrParam();
                    Address internalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(internalLocalUri);
                    internalLocalAddress.setURI(internalLocalUri);
                    if(logger.isDebugEnabled()) {
                        logger.debug("adding Record Router Header :"+internalLocalAddress);
                    }                    
                    balancerRunner.balancerContext.internalRecordRouteHeader[TCP] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(internalLocalAddress);  
                }
            	if(internalLpWs!=null)
                {
                    SipURI internalLocalUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, internalLpWs.getIPAddress());
                    internalLocalUri.setPort(internalLpWs.getPort());
                    internalLocalUri.setTransportParam(ListeningPointExt.WS);
                    //See RFC 3261 19.1.1 for lr parameter
                    internalLocalUri.setLrParam();
                    Address internalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(internalLocalUri);
                    internalLocalAddress.setURI(internalLocalUri);
                    if(logger.isDebugEnabled()) {
                        logger.debug("adding Record Router Header :"+internalLocalAddress);
                    }                    
                    balancerRunner.balancerContext.internalRecordRouteHeader[WS] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(internalLocalAddress);  
                }
            	if(internalLpTls!=null)
                {
                    SipURI internalLocalUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, internalLpTls.getIPAddress());
                    internalLocalUri.setPort(internalLpTls.getPort());
                    internalLocalUri.setTransportParam(ListeningPoint.TLS);
                    //See RFC 3261 19.1.1 for lr parameter
                    internalLocalUri.setLrParam();
                    Address internalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(internalLocalUri);
                    internalLocalAddress.setURI(internalLocalUri);
                    if(logger.isDebugEnabled()) {
                        logger.debug("adding Record Router Header :"+internalLocalAddress);
                    }                    
                    balancerRunner.balancerContext.internalRecordRouteHeader[TLS] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(internalLocalAddress);  
                }
            	if(internalLpWss!=null)
                {
                	SipURI internalLocalUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, internalLpWss.getIPAddress());
                    internalLocalUri.setPort(internalLpWss.getPort());
                    internalLocalUri.setTransportParam(ListeningPointExt.WSS);
                    //See RFC 3261 19.1.1 for lr parameter
                    internalLocalUri.setLrParam();
                    Address internalLocalAddress = balancerRunner.balancerContext.addressFactory.createAddress(internalLocalUri);
                    internalLocalAddress.setURI(internalLocalUri);
                    if(logger.isDebugEnabled()) {
                        logger.debug("adding Record Router Header :"+internalLocalAddress);
                    }                    
                    balancerRunner.balancerContext.internalRecordRouteHeader[WSS] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(internalLocalAddress);  
                }
            }

            if(balancerRunner.balancerContext.externalIpLoadBalancerAddress != null) {
                //UDP RR
            	if(balancerRunner.balancerContext.externalLoadBalancerUdpPort!=0)
                {
                    SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, balancerRunner.balancerContext.externalIpLoadBalancerAddress);
                    ipLbSipUri.setPort(balancerRunner.balancerContext.externalLoadBalancerUdpPort);
                    ipLbSipUri.setTransportParam(ListeningPoint.UDP);
                    ipLbSipUri.setLrParam();
                    Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                    ipLbAdress.setURI(ipLbSipUri);
                    balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[UDP] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(ipLbAdress);
                }
                //TCP RR
            	if(balancerRunner.balancerContext.externalLoadBalancerTcpPort!=0)
                {
                    SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, balancerRunner.balancerContext.externalIpLoadBalancerAddress);
                    ipLbSipUri.setPort(balancerRunner.balancerContext.externalLoadBalancerTcpPort);
                    ipLbSipUri.setTransportParam(ListeningPoint.TCP);
                    ipLbSipUri.setLrParam();
                    Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                    ipLbAdress.setURI(ipLbSipUri);
                    balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[TCP] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(ipLbAdress);
                }
                //WS RR
            	if(balancerRunner.balancerContext.externalLoadBalancerWsPort!=0)
                {
                    SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, balancerRunner.balancerContext.externalIpLoadBalancerAddress);
                    ipLbSipUri.setPort(balancerRunner.balancerContext.externalLoadBalancerWsPort);
                    ipLbSipUri.setTransportParam(ListeningPointExt.WS);
                    ipLbSipUri.setLrParam();
                    Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                    ipLbAdress.setURI(ipLbSipUri);
                    balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[WS] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(ipLbAdress);
                }
                //TLS RR
            	if(balancerRunner.balancerContext.externalLoadBalancerTlsPort!=0)
                {
                    SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, balancerRunner.balancerContext.externalIpLoadBalancerAddress);
                    ipLbSipUri.setPort(balancerRunner.balancerContext.externalLoadBalancerTlsPort);
                    ipLbSipUri.setTransportParam(ListeningPoint.TLS);
                    ipLbSipUri.setLrParam();
                    Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                    ipLbAdress.setURI(ipLbSipUri);
                    balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[TLS] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(ipLbAdress);
                }
                //WSS RR
            	if(balancerRunner.balancerContext.externalLoadBalancerWssPort!=0)
                {
                	SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, balancerRunner.balancerContext.externalIpLoadBalancerAddress);
                    ipLbSipUri.setPort(balancerRunner.balancerContext.externalLoadBalancerWssPort);
                    ipLbSipUri.setTransportParam(ListeningPointExt.WSS);
                    ipLbSipUri.setLrParam();
                    Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                    ipLbAdress.setURI(ipLbSipUri);
                    balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[WSS] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(ipLbAdress);
                }
            }

            if(balancerRunner.balancerContext.internalIpLoadBalancerAddress != null) {
            	if(balancerRunner.balancerContext.internalLoadBalancerUdpPort!=0)
                {
                    SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, balancerRunner.balancerContext.internalIpLoadBalancerAddress);
                    ipLbSipUri.setPort(balancerRunner.balancerContext.internalLoadBalancerUdpPort);
                    ipLbSipUri.setTransportParam(ListeningPoint.UDP);
                    ipLbSipUri.setLrParam();
                    Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                    ipLbAdress.setURI(ipLbSipUri);
                    balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[UDP] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(ipLbAdress);
                }
            	if(balancerRunner.balancerContext.internalLoadBalancerTcpPort!=0)
                {
                    SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, balancerRunner.balancerContext.internalIpLoadBalancerAddress);
                    ipLbSipUri.setPort(balancerRunner.balancerContext.internalLoadBalancerTcpPort);
                    ipLbSipUri.setTransportParam(ListeningPoint.TCP);
                    ipLbSipUri.setLrParam();
                    Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                    ipLbAdress.setURI(ipLbSipUri);
                    balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[TCP] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(ipLbAdress);
                }
            	if(balancerRunner.balancerContext.internalLoadBalancerWsPort!=0)
                {
                    SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, balancerRunner.balancerContext.internalIpLoadBalancerAddress);
                    ipLbSipUri.setPort(balancerRunner.balancerContext.internalLoadBalancerWsPort);
                    ipLbSipUri.setTransportParam(ListeningPointExt.WS);
                    ipLbSipUri.setLrParam();
                    Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                    ipLbAdress.setURI(ipLbSipUri);
                    balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[WS] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(ipLbAdress);
                }
            	if(balancerRunner.balancerContext.internalLoadBalancerTlsPort!=0)
                {
                    SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, balancerRunner.balancerContext.internalIpLoadBalancerAddress);
                    ipLbSipUri.setPort(balancerRunner.balancerContext.internalLoadBalancerTlsPort);
                    ipLbSipUri.setTransportParam(ListeningPoint.TLS);
                    ipLbSipUri.setLrParam();
                    Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                    ipLbAdress.setURI(ipLbSipUri);
                    balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[TLS] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(ipLbAdress);
                }
            	if(balancerRunner.balancerContext.internalLoadBalancerWssPort!=0)
                {
                    SipURI ipLbSipUri = balancerRunner.balancerContext.addressFactory
                            .createSipURI(null, balancerRunner.balancerContext.internalIpLoadBalancerAddress);
                    ipLbSipUri.setPort(balancerRunner.balancerContext.internalLoadBalancerWssPort);
                    ipLbSipUri.setTransportParam(ListeningPointExt.WSS);
                    ipLbSipUri.setLrParam();
                    Address ipLbAdress = balancerRunner.balancerContext.addressFactory.createAddress(ipLbSipUri);
                    ipLbAdress.setURI(ipLbSipUri);
                    balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[WSS] = balancerRunner.balancerContext.headerFactory
                            .createRecordRouteHeader(ipLbAdress);
                }
            }
            
            balancerRunner.balancerContext.activeExternalHeader[UDP] = balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[UDP] != null ?
            		balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[UDP] : balancerRunner.balancerContext.externalRecordRouteHeader[UDP];
            balancerRunner.balancerContext.activeInternalHeader[UDP] = balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[UDP] != null ?
            		balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[UDP] : balancerRunner.balancerContext.internalRecordRouteHeader[UDP];

            balancerRunner.balancerContext.activeExternalHeader[TCP] = balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[TCP] != null ?
            		balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[TCP] : balancerRunner.balancerContext.externalRecordRouteHeader[TCP];
            balancerRunner.balancerContext.activeInternalHeader[TCP] = balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[TCP] != null ?
            		balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[TCP] : balancerRunner.balancerContext.internalRecordRouteHeader[TCP];

            balancerRunner.balancerContext.activeExternalHeader[WS] = balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[WS] != null ?
                    balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[WS] : balancerRunner.balancerContext.externalRecordRouteHeader[WS];
            balancerRunner.balancerContext.activeInternalHeader[WS] = balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[WS] != null ?
                    balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[WS] : balancerRunner.balancerContext.internalRecordRouteHeader[WS];
                    		
            balancerRunner.balancerContext.activeExternalHeader[TLS] = balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[TLS] != null ?
            		balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[TLS] : balancerRunner.balancerContext.externalRecordRouteHeader[TLS];
			balancerRunner.balancerContext.activeInternalHeader[TLS] = balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[TLS] != null ?
					balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[TLS] : balancerRunner.balancerContext.internalRecordRouteHeader[TLS];

			balancerRunner.balancerContext.activeExternalHeader[WSS] = balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[WSS] != null ?
            		balancerRunner.balancerContext.externalIpBalancerRecordRouteHeader[WSS] : balancerRunner.balancerContext.externalRecordRouteHeader[WSS];
			balancerRunner.balancerContext.activeInternalHeader[WSS] = balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[WSS] != null ?
					balancerRunner.balancerContext.internalIpBalancerRecordRouteHeader[WSS] : balancerRunner.balancerContext.internalRecordRouteHeader[WSS];

			balancerRunner.balancerContext.useIpLoadBalancerAddressInViaHeaders = Boolean
					.valueOf(balancerRunner.balancerContext.properties
							.getProperty(
									"useIpLoadBalancerAddressInViaHeaders",
									"false"));
			
			if (balancerRunner.balancerContext.useIpLoadBalancerAddressInViaHeaders) 
			{
				balancerRunner.balancerContext.externalViaHost = balancerRunner.balancerContext.externalIpLoadBalancerAddress;
				balancerRunner.balancerContext.internalViaHost = balancerRunner.balancerContext.internalIpLoadBalancerAddress;
				
				balancerRunner.balancerContext.externalViaUdpPort = balancerRunner.balancerContext.externalLoadBalancerUdpPort;
				balancerRunner.balancerContext.externalViaTcpPort = balancerRunner.balancerContext.externalLoadBalancerTcpPort;
				balancerRunner.balancerContext.externalViaTlsPort = balancerRunner.balancerContext.externalLoadBalancerTlsPort;
				balancerRunner.balancerContext.externalViaWsPort = balancerRunner.balancerContext.externalLoadBalancerWsPort;
				balancerRunner.balancerContext.externalViaWssPort = balancerRunner.balancerContext.externalLoadBalancerWssPort;
				
				balancerRunner.balancerContext.internalViaUdpPort = balancerRunner.balancerContext.internalLoadBalancerUdpPort;
				balancerRunner.balancerContext.internalViaTcpPort = balancerRunner.balancerContext.internalLoadBalancerTcpPort;
				balancerRunner.balancerContext.internalViaTlsPort = balancerRunner.balancerContext.internalLoadBalancerTlsPort;
				balancerRunner.balancerContext.internalViaWsPort = balancerRunner.balancerContext.internalLoadBalancerWsPort;
				balancerRunner.balancerContext.internalViaWssPort = balancerRunner.balancerContext.internalLoadBalancerWssPort;

			} 
			else 
			{
				balancerRunner.balancerContext.externalViaHost = balancerRunner.balancerContext.externalHost;
				balancerRunner.balancerContext.internalViaHost = balancerRunner.balancerContext.internalHost;
				
				balancerRunner.balancerContext.externalViaUdpPort = balancerRunner.balancerContext.externalUdpPort;
				balancerRunner.balancerContext.externalViaTcpPort = balancerRunner.balancerContext.externalTcpPort;
				balancerRunner.balancerContext.externalViaTlsPort = balancerRunner.balancerContext.externalTlsPort;
				balancerRunner.balancerContext.externalViaWsPort = balancerRunner.balancerContext.externalWsPort;
				balancerRunner.balancerContext.externalViaWssPort = balancerRunner.balancerContext.externalWssPort;

				balancerRunner.balancerContext.internalViaUdpPort = balancerRunner.balancerContext.internalUdpPort;
				balancerRunner.balancerContext.internalViaTcpPort = balancerRunner.balancerContext.internalTcpPort;
				balancerRunner.balancerContext.internalViaTlsPort = balancerRunner.balancerContext.internalTlsPort;
				balancerRunner.balancerContext.internalViaWsPort = balancerRunner.balancerContext.internalWsPort;
				balancerRunner.balancerContext.internalViaWssPort = balancerRunner.balancerContext.internalWssPort;
			}

//			balancerRunner.balancerContext.publicIP = balancerRunner.balancerContext.properties
//					.getProperty("public-ip",
//							balancerRunner.balancerContext.host);
			// https://github.com/RestComm/load-balancer/issues/43 don't use host by default if public-ip is not set or it will result in contact header responses being badly patched
			balancerRunner.balancerContext.publicIP = balancerRunner.balancerContext.properties
					.getProperty("public-ip");
			
			String blockedValues = balancerRunner.balancerContext.properties
					.getProperty("blocked-values",
							"sipvicious,sipcli,friendly-scanner");
			balancerRunner.balancerContext.blockedList = new ArrayList<String>(
					Arrays.asList(blockedValues.split(",")));

			balancerRunner.balancerContext.sipStack.start();
			SIPBalancerValveProcessor valve = (SIPBalancerValveProcessor) balancerRunner.balancerContext.sipStack.sipMessageValve;
			valve.balancerRunner = balancerRunner;
        } catch (Exception ex) {
            throw new IllegalStateException("Can't create sip objects and lps due to["+ex.getMessage()+"]", ex);
        }
        if(logger.isInfoEnabled()) {
            logger.info("Sip Balancer started on external address " + balancerRunner.balancerContext.externalHost + 
            		", external UDP port : " + balancerRunner.balancerContext.externalUdpPort +
            		", external TCP port : " + balancerRunner.balancerContext.externalTcpPort +
            		", external TLS port : " + balancerRunner.balancerContext.externalTlsPort +
            		", external WS port : " + balancerRunner.balancerContext.externalWsPort +
            		", external WSS port : " + balancerRunner.balancerContext.externalWssPort +
            		", internal UDP Port : " + balancerRunner.balancerContext.internalUdpPort +
            		", internal TCP Port : " + balancerRunner.balancerContext.internalTcpPort +
            		", internal TLS Port : " + balancerRunner.balancerContext.internalTlsPort +
            		", internal WS Port : " + balancerRunner.balancerContext.internalWsPort +
            		", internal WSS Port : " + balancerRunner.balancerContext.internalWssPort);
        }              
    }

    public void stop() {
        if(balancerRunner.balancerContext.sipStack == null) return;// already stopped
        @SuppressWarnings("rawtypes")
		Iterator sipProviderIterator = balancerRunner.balancerContext.sipStack.getSipProviders();
        try{
            while (sipProviderIterator.hasNext()) {
                SipProvider sipProvider = (SipProvider)sipProviderIterator.next();
                sipProvider.removeSipListener(this);
                ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
                balancerRunner.balancerContext.sipStack.deleteSipProvider(sipProvider);	
                
                for (ListeningPoint listeningPoint : listeningPoints) {
                    if(logger.isInfoEnabled()) {
                        logger.info("Removing the following Listening Point " + listeningPoint);
                    }
                    try {
                    	sipProvider.removeListeningPoint(listeningPoint);
                        balancerRunner.balancerContext.sipStack.deleteListeningPoint(listeningPoint);
                        
                    } catch (Exception e) {
                        logger.error("Cant remove the listening points or sip providers", e);
                    }
                }
                if(logger.isInfoEnabled()) {
                    logger.info("Removing the sip provider");
                }
                sipProviderIterator = balancerRunner.balancerContext.sipStack.getSipProviders();
            }
            balancerRunner.balancerContext.sipStack.stop();
            balancerRunner.balancerContext.sipStack = null;
            System.gc();
            if(logger.isInfoEnabled()) {
                logger.info("Sip forwarder SIP stack stopped");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cant remove the listening points or sip providers", e);
        }


    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        // We wont see those
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        // Hopefully we wont see those either
    }

    /*
     * (non-Javadoc)
     * @see javax.sip.SipListener#processRequest(javax.sip.RequestEvent)
     */
    public void processRequest(RequestEvent requestEvent) {
        // This will be invoked only by external endpoint
        final SipProvider sipProvider = (SipProvider) requestEvent.getSource();

        final Request request = requestEvent.getRequest();
        final String requestMethod = request.getMethod();

        if((requestMethod.equals(Request.OPTIONS) ||
                requestMethod.equals(Request.INFO)) &&
                request.getHeader("Mobicents-Heartbeat") != null &&
                sipProvider == balancerRunner.balancerContext.internalSipProvider) {
            byte[] bytes = (byte[]) request.getContent();
            Properties prop = new Properties();
            try {
                prop.load(new ByteArrayInputStream(bytes, 0, bytes.length));
                SIPNode node = new SIPNode(prop.getProperty("hostname"), prop.getProperty("ip"));
                for(String id : prop.stringPropertyNames()) {
                    node.getProperties().put(id, prop.getProperty(id));
                }
                ArrayList<SIPNode> list = new ArrayList<SIPNode>();
                list.add(node);
                this.register.handlePingInRegister(list);
                Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.OK, request);			
                sipProvider.sendResponse(response);	
                return;
            } catch (Exception e) {
                logger.error("Failure parsing heartbeat properties from this request " + request, e);
            }
        }
        //Issue 10: https://telestax.atlassian.net/browse/LB-10
        if (request.getContent() != null || (requestMethod.equals(Request.REGISTER) && sipProvider != balancerRunner.balancerContext.internalSipProvider)) {
            SIPMessage message = (SIPMessage)request;            

            String initialRemoteAddr = message.getPeerPacketSourceAddress().getHostAddress();
            String initialRemotePort = String.valueOf(message.getPeerPacketSourcePort());

            Header remoteAddrHeader = null;
            Header remotePortHeader = null;
            try {
                remoteAddrHeader = SipFactory.getInstance().createHeaderFactory().createHeader("X-Sip-Balancer-InitialRemoteAddr", initialRemoteAddr);
                remotePortHeader = SipFactory.getInstance().createHeaderFactory().createHeader("X-Sip-Balancer-InitialRemotePort", initialRemotePort);
            } catch (PeerUnavailableException e) {
                logger.error("Unexpected exception while creating custom headers for REGISTER message ", e);
            } catch (ParseException e) {
                logger.error("Unexpected exception while creating custom headers for REGISTER message ", e);
            }
            if (remoteAddrHeader != null)
                request.addHeader(remoteAddrHeader);
            if (remotePortHeader != null)
                request.addHeader(remotePortHeader);
        } 
        try {	
            updateStats(request);
            forwardRequest(sipProvider,request);          						
        } catch (Throwable throwable) {
            logger.error("Unexpected exception while forwarding the request " + request, throwable);
            if(!Request.ACK.equalsIgnoreCase(requestMethod)) {
                try {
                    Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, request);			
                    sipProvider.sendResponse(response);	
                } catch (Exception e) {
                    logger.error("Unexpected exception while trying to send the error response for this " + request, e);
                }
            }
        }
    }

    private void updateStats(Message message) {
        if(balancerRunner.balancerContext.gatherStatistics) {
            if(message instanceof Request) {
            	balancerRunner.balancerContext.bytesTransferred.addAndGet(((Request) message).getContentLength().getContentLength());
                balancerRunner.balancerContext.requestsProcessed.incrementAndGet();
                final String method = ((Request) message).getMethod();
                final AtomicLong requestsProcessed = balancerRunner.balancerContext.requestsProcessedByMethod.get(method);
                if(requestsProcessed == null) {
                    balancerRunner.balancerContext.requestsProcessedByMethod.put(method, new AtomicLong(0));
                } else {
                    requestsProcessed.incrementAndGet();
                }
            } else {
                balancerRunner.balancerContext.responsesProcessed.incrementAndGet();
                balancerRunner.balancerContext.bytesTransferred.addAndGet(((Response) message).getContentLength().getContentLength());
                final int statusCode = ((Response)message).getStatusCode();				
                int statusCodeDiv = statusCode / 100;
                switch (statusCodeDiv) {
                    case 1:
                        balancerRunner.balancerContext.responsesProcessedByStatusCode.get("1XX").incrementAndGet();
                        break;
                    case 2:
                        balancerRunner.balancerContext.responsesProcessedByStatusCode.get("2XX").incrementAndGet();
                        break;
                    case 3:
                        balancerRunner.balancerContext.responsesProcessedByStatusCode.get("3XX").incrementAndGet();
                        break;
                    case 4:
                        balancerRunner.balancerContext.responsesProcessedByStatusCode.get("4XX").incrementAndGet();
                        break;
                    case 5:
                        balancerRunner.balancerContext.responsesProcessedByStatusCode.get("5XX").incrementAndGet();
                        break;
                    case 6:
                        balancerRunner.balancerContext.responsesProcessedByStatusCode.get("6XX").incrementAndGet();
                        break;
                    case 7:
                        balancerRunner.balancerContext.responsesProcessedByStatusCode.get("7XX").incrementAndGet();
                        break;
                    case 8:
                        balancerRunner.balancerContext.responsesProcessedByStatusCode.get("8XX").incrementAndGet();
                        break;
                    case 9:
                        balancerRunner.balancerContext.responsesProcessedByStatusCode.get("9XX").incrementAndGet();
                        break;
                }		
            }		
        }
    }

    private SIPNode getAliveNode(String host, int port, String otherTransport, InvocationContext ctx) {
        //return getNodeFromCollection(host, port, otherTransport, ctx.nodes);
    	return ctx.sipNodeMap.get(new KeySip(host,port));
    }

    private SIPNode getAliveNodeAnyVersion(String host, int port, String otherTransport) {
        return getNodeFromCollection(host, port, otherTransport, balancerRunner.balancerContext.aliveNodes);
    }

    private SIPNode getNodeFromCollection(String host, int port, String otherTransport, Collection<SIPNode> ctx) {
    	
        otherTransport = otherTransport.toLowerCase();
        for(SIPNode node : ctx) {
            if(host.equals(node.getHostName()) || host.equals(node.getIp())) {
            	if((Integer)node.getProperties().get(otherTransport + "Port") == port) {
                    return node;
                }
            }
        }
        return null;
    }

    private SIPNode getNodeDeadOrAlive(String host, int port, String otherTransport) {
        return getNodeFromCollection(host, port, otherTransport, balancerRunner.balancerContext.allNodesEver);
    }

    private boolean isViaHeaderFromServer(Request request) {
        ViaHeader viaHeader = ((ViaHeader)request.getHeader(ViaHeader.NAME));
        String host = viaHeader.getHost();
        String transport = viaHeader.getTransport();
		if(balancerRunner.balancerContext.terminateTLSTraffic)
		{
			if(transport.equalsIgnoreCase(ListeningPoint.TLS))
				transport = ListeningPoint.TCP;
			if(transport.equalsIgnoreCase(ListeningPointExt.WSS))
				transport = ListeningPointExt.WS;
		}
        if(transport == null) transport = ListeningPoint.UDP;
        int port = viaHeader.getPort();
        if(extraServerAddresses != null) {
            for(int q=0; q<extraServerAddresses.length; q++) {
                if(extraServerAddresses[q].equals(host) && extraServerPorts[q] == port) {
                    return true;
                }
            }
        }
        if(getAliveNodeAnyVersion(host, port, transport) != null) {
            return true;
        }
        return false;
    }

    private SIPNode getTransactionSourceNode(Response response) {
        ViaHeader viaHeader = ((ViaHeader)response.getHeader(ViaHeader.NAME));
        String host = viaHeader.getHost();
        String transport = viaHeader.getTransport();
        if(transport == null) transport = ListeningPoint.UDP;
        transport = transport.toLowerCase();
        int port = viaHeader.getPort();
        if(extraServerAddresses != null) {
            for(int q=0; q<extraServerAddresses.length; q++) {
                if(extraServerAddresses[q].equals(host) && extraServerPorts[q] == port) {
                    return ExtraServerNode.extraServerNode;
                }
            }
        }
        SIPNode node = getNodeDeadOrAlive(host, port, transport);
        if(node != null) {
            return node;
        }
        return null;
    }

    private SIPNode getSenderNode(Response response) {
        SIPResponse resp = (SIPResponse) response;
        String host = resp.getRemoteAddress().getHostAddress();
        
        ViaHeader viaHeader = ((ViaHeader)response.getHeader(ViaHeader.NAME));
		String currentTransport = viaHeader.getTransport();
		String transport = null;
		if (!balancerRunner.balancerContext.terminateTLSTraffic) {
			transport = currentTransport;
		} else {
			if (currentTransport.equalsIgnoreCase(ListeningPoint.TLS))
				transport = ListeningPoint.TCP;
			else if (currentTransport.equalsIgnoreCase(ListeningPointExt.WSS))
				transport = ListeningPointExt.WS;
			else
				transport = currentTransport;
		}
        if(transport == null) transport = ListeningPoint.UDP;
        int port = resp.getRemotePort();
        if(extraServerAddresses != null) {
            for(int q=0; q<extraServerAddresses.length; q++) {
                if(extraServerAddresses[q].equals(host) && extraServerPorts[q] == port) {
                    return ExtraServerNode.extraServerNode;
                }
            }
        }
        SIPNode node = getNodeDeadOrAlive(host, port, transport);
        if(node != null) {
            return node;
        }
        return null;
    }

    public SipURI getLoopbackUri(Request request) {
        SipURI uri = null;

        RouteHeader route = (RouteHeader) request.getHeader(RouteHeader.NAME);
        if(route != null) {
            if(route.getAddress().getURI().isSipURI()) {
                uri = (SipURI) route.getAddress().getURI();
            }
        } else {
            if(request.getRequestURI().isSipURI()) {
                uri = (SipURI) request.getRequestURI();
            }
        }
        
        if(uri != null) 
        {
            if(uri.getHost().equals(balancerRunner.balancerContext.externalHost) || uri.getHost().equals(balancerRunner.balancerContext.publicIP))
            {
				int port = uri.getPort();
				if (port == balancerRunner.balancerContext.externalUdpPort
						|| port == balancerRunner.balancerContext.externalTcpPort
						|| port == balancerRunner.balancerContext.externalTlsPort
						|| port == balancerRunner.balancerContext.externalWsPort
						|| port == balancerRunner.balancerContext.externalWssPort)
					return uri;
            }
                
            if(uri.getHost().equals(balancerRunner.balancerContext.internalHost) || uri.getHost().equals(balancerRunner.balancerContext.publicIP))
            {
				int port = uri.getPort();
				if (port == balancerRunner.balancerContext.internalUdpPort
						|| port == balancerRunner.balancerContext.internalTcpPort
						|| port == balancerRunner.balancerContext.internalTlsPort
						|| port == balancerRunner.balancerContext.internalWsPort
						|| port == balancerRunner.balancerContext.internalWssPort)
					return uri;
            }
        }
        return null;
    }

    /**
     * @param requestEvent
     * @param sipProvider
     * @param originalRequest
     * @param serverTransaction
     * @param request
     * @throws ParseException
     * @throws InvalidArgumentException
     * @throws SipException
     * @throws TransactionUnavailableException
     */
    private void forwardRequest(SipProvider sipProvider, Request request)
                    throws ParseException, InvalidArgumentException, SipException, TransactionUnavailableException {
        if(logger.isDebugEnabled()) {
            logger.debug("got request:\n"+request);
        } 

        boolean isRequestFromServer = false;
        if(!balancerRunner.balancerContext.isTwoEntrypoints()) {
            isRequestFromServer = isViaHeaderFromServer(request);
        } else {
            isRequestFromServer = sipProvider.equals(balancerRunner.balancerContext.internalSipProvider);
        }

        final boolean isCancel = Request.CANCEL.equals(request.getMethod());

        if(!isCancel) {
            decreaseMaxForwardsHeader(sipProvider, request);
        }

		String outerTransport = ((ViaHeader) request.getHeader(ViaHeader.NAME))
				.getTransport().toLowerCase();
		if (isRequestFromServer) {
			Boolean hasTransport = false;
			if (request.getRequestURI().isSipURI()) {
				if (((SipUri) request.getRequestURI()).getTransportParam() != null) {
					outerTransport = ((SipUri) request.getRequestURI())
							.getTransportParam();
					hasTransport = true;
				}
			}

			if (!hasTransport) {
				outerTransport = getRouteHeadersMeantForLB(request);
				if (outerTransport == null)
					outerTransport = ((ViaHeader) request
							.getHeader(ViaHeader.NAME)).getTransport()
							.toLowerCase();
			}
		}

        RouteHeaderHints hints = removeRouteHeadersMeantForLB(request);

        String version = hints.version;

        if(version == null) {
            version = register.getLatestVersion();
            hints.version = version;
        }

        InvocationContext ctx = balancerRunner.getInvocationContext(version);

        final String callID = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();

		String transport = null;
		if (!balancerRunner.balancerContext.terminateTLSTraffic)
			transport = ((ViaHeader) request.getHeader(ViaHeader.NAME))
					.getTransport().toLowerCase();
		else {
			switch (((ViaHeader) request.getHeader(ViaHeader.NAME))
					.getTransport()) {
			case ListeningPoint.TLS:
				transport = ListeningPoint.TCP.toLowerCase();
				break;
			case ListeningPointExt.WSS:
				transport = ListeningPointExt.WS.toLowerCase();
				break;
			case ListeningPointExt.WS:
			case ListeningPointExt.TCP:
			case ListeningPointExt.UDP:
				transport = ((ViaHeader) request.getHeader(ViaHeader.NAME))
						.getTransport().toLowerCase();
			}
		}

        if(hints.serverAssignedNode !=null) {
        	String headerKey = null;
        	if(balancerRunner.balancerContext.sipHeaderAffinityKey=="To")
        	{
        		URI currURI=((HeaderAddress)request.getHeader(balancerRunner.balancerContext.sipHeaderAffinityKey)).getAddress().getURI();
        		if(currURI.isSipURI())
        			headerKey = ((SipURI)currURI).getUser();
        		else
        			headerKey = ((TelURL)currURI).getPhoneNumber();
        	}
        	else
        	{
        		headerKey = ((SIPHeader) request.getHeader(balancerRunner.balancerContext.sipHeaderAffinityKey)).getValue();
        	}
        	ctx.balancerAlgorithm.assignToNode(headerKey, hints.serverAssignedNode);
            if(logger.isDebugEnabled()) {
                logger.debug("Following node information has been found in one of the route Headers " + hints.serverAssignedNode);
            }

            SipURI loopbackUri = getLoopbackUri(request);
            if(loopbackUri != null) {
            	loopbackUri.setHost(hints.serverAssignedNode.getIp());
            	loopbackUri.setPort((Integer) hints.serverAssignedNode.getProperties().get(transport + "Port"));
            }
        }

        SIPNode nextNode = null;

        if(isRequestFromServer) {
        	if(logger.isDebugEnabled()) {
        		logger.debug("Request from server");
        	}
            Header initialAddrHeader = request.getHeader("X-Sip-Balancer-InitialRemoteAddr");
            Header initialPortHeader = request.getHeader("X-Sip-Balancer-InitialRemotePort");
            if(initialAddrHeader != null)
                request.removeHeader(initialAddrHeader.getName());
            if(initialPortHeader != null)
                request.removeHeader(initialPortHeader.getName());
            ctx.balancerAlgorithm.processInternalRequest(request);
            if (request.getMethod().equalsIgnoreCase(Request.INVITE) && ctx.balancerAlgorithm.blockInternalRequest(request)) {
                Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.FORBIDDEN, request);          
                response.setReasonPhrase("Destination not allowed");
                sipProvider.sendResponse(response);
                return;
            }
            
            nextNode = hints.serverAssignedNode;            
            
        } else {
        	if(logger.isDebugEnabled()) {
        		logger.debug("Request not from server");
        	}
            // Request is NOT from app server, first check if we have hints in Route headers
            SIPNode assignedNode = hints.serverAssignedNode;

            // If there are no hints see if there is route header pointing existing node
            if(assignedNode == null) {
                RouteHeader nextNodeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
                if(nextNodeHeader != null) {
                    URI uri = nextNodeHeader.getAddress().getURI();
                    if(uri instanceof SipURI) {
                        SipURI sipUri = (SipURI) uri;
                        assignedNode = getAliveNode(sipUri.getHost(), sipUri.getPort(), transport, ctx);
                        if(logger.isDebugEnabled()) {
                            logger.debug("Found SIP URI " + uri + " |Next node is " + assignedNode);
                        }
                    }
                }
            }
            SipURI assignedUri = null;
            //boolean nextNodeInRequestUri = false;
            SipURI originalRouteHeaderUri = null;
            if(assignedNode == null) {
                if(hints.subsequentRequest) {
                    RouteHeader header = (RouteHeader) request.getHeader(RouteHeader.NAME);
                    if(header != null) {
                        assignedUri = (SipURI) header.getAddress().getURI();
                        originalRouteHeaderUri = (SipURI) assignedUri.clone();
                        request.removeFirst(RouteHeader.NAME);
                    } else {
                        if(request.getRequestURI() instanceof SipURI) {
                            SipURI sipUri =(SipURI) request.getRequestURI();
                            //nextNodeInRequestUri = true;
                            assignedNode = getAliveNode(sipUri.getHost(), sipUri.getPort(), transport, ctx);
                        }
                    }
                    if(logger.isDebugEnabled()) {
                        logger.debug("Subsequent request -> Found Route Header " + header + " |Next node is " + assignedNode);
                    }
                } else if(request.getRequestURI() instanceof SipURI) {
                    SipURI sipUri =(SipURI) request.getRequestURI();
                    //nextNodeInRequestUri = true;
                    assignedNode = getAliveNode(sipUri.getHost(), sipUri.getPort(), transport, ctx);
                    if(logger.isDebugEnabled()) {
                        logger.debug("NOT Subsequent request -> using sipUri " + sipUri + " |Next node is " + assignedNode);
                    }
                }
            }

            if(assignedNode == null) {
                if(logger.isDebugEnabled()) {
                    logger.debug("assignedNode is null");
                }
                if (!securityCheck(request)){
                    logger.warn("Request failed at the security check:\n"+request);
                } else {
                    nextNode = ctx.balancerAlgorithm.processExternalRequest(request);
                }
                if(nextNode instanceof NullServerNode) {
                    if(logger.isDebugEnabled()) {
                        logger.debug("Algorithm returned a NullServerNode. We will not attempt to forward this request " + request);
                    }
                }
                if(nextNode != null) {
                    if(logger.isDebugEnabled()) {
                        String nodesString = "";
                        //Object[] nodes = ctx.nodes.toArray();
                        Object[] nodes = ctx.sipNodeMap.values().toArray();
                        
                        for(Object n : nodes) {
                            nodesString +=n + " , ";
                        }
                        logger.debug("Next node is not null. Assigned uri is " + assignedUri + "Available nodes: " + nodesString);
                    }
                    //Adding Route Header pointing to the node the sip balancer wants to forward to
                    SipURI routeSipUri;
                    try {

                        if(assignedUri == null) { // If a next node is NOT already assigned in the dialog from previous requests
                            routeSipUri = balancerRunner.balancerContext.addressFactory
                                    .createSipURI(null, nextNode.getIp());
                        }
                        else { // OTHERWISE, a node is already assigned and it's alive
                            routeSipUri = assignedUri;
                        }
                        routeSipUri.setHost(nextNode.getIp());
                        Integer port = (Integer)nextNode.getProperties().get(transport + "Port");
                        if(port == null) {
                            throw new RuntimeException("Port is null in the node properties for transport="
                                    + transport);
                        }
                        routeSipUri.setPort(port);
                        routeSipUri.setTransportParam(transport);
                        routeSipUri.setLrParam();

                        SipURI uri = (SipURI) request.getRequestURI();
                        RouteHeader header = (RouteHeader) request.getHeader(RouteHeader.NAME);
                        if (isRouteHeaderExternal(uri.getHost(), uri.getPort(), ((ViaHeader)request.getHeader("Via")).getTransport()) || header != null)
                        {
	                        final RouteHeader route = balancerRunner.balancerContext.headerFactory.createRouteHeader(
	                                balancerRunner.balancerContext.addressFactory.createAddress(routeSipUri));
	                        request.addFirst(route);
	
	                        // If the request is meant for the AS it must recognize itself in the ruri, so update it too
	                        // For http://code.google.com/p/mobicents/issues/detail?id=2132
	                        if(originalRouteHeaderUri != null && request.getRequestURI().isSipURI()) {
	                            // we will just compare by hostport id
	                            String rurihostid = uri.getHost() + uri.getPort();
	                            String originalhostid = originalRouteHeaderUri.getHost() + originalRouteHeaderUri.getPort();
	                            if(rurihostid.equals(originalhostid)) {
	                                uri.setPort(routeSipUri.getPort());
	                                uri.setHost(routeSipUri.getHost());
	                            }
	                        }
                        }
                        else
                        {
                            //should not add any routes , packet is destinated to lb
                            uri.setPort(routeSipUri.getPort());
                            uri.setHost(routeSipUri.getHost());
                        }

                    } catch (Exception e) {
                        throw new RuntimeException("Error adding route header", e);
                    }
                }
            } else {
                nextNode = ctx.balancerAlgorithm.processAssignedExternalRequest(request, assignedNode);
            }
            if(nextNode == null) {
                if(logger.isDebugEnabled()) {
                    logger.debug("No nodes available");
                }
                if(!Request.ACK.equalsIgnoreCase(request.getMethod())) {
                    try {
                        Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, request);			
                        response.setReasonPhrase("No nodes available");
                        sipProvider.sendResponse(response);	
                    } catch (Exception e) {
                        logger.error("Unexpected exception while trying to send the error response for this " + request, e);
                    }
                }
                return;
            } else {

            }
        }

        String requestMethod=request.getMethod();
        //100 Trying should be sent only if has indent really forwarding request, othewise should send 500 error
        // https://telestax.atlassian.net/browse/LB-25 improve performance by sending back 100 Trying right away to tame retransmissions.
        if(balancerRunner.balancerContext.isSendTrying)
        {
        	logger.debug("Load balancer sends 100 TRYING");
        	if(requestMethod.equals(Request.INVITE) || requestMethod.equals(Request.SUBSCRIBE) || 
        			requestMethod.equals(Request.NOTIFY) || requestMethod.equals(Request.MESSAGE) || 
        			requestMethod.equals(Request.REFER) || requestMethod.equals(Request.PUBLISH) || 
        			requestMethod.equals(Request.UPDATE)) {
        		try {
        			Response response = balancerRunner.balancerContext.messageFactory.createResponse(Response.TRYING, request);
        			RouteList routeList = ((SIPMessage)request).getRouteHeaders();
        			if (routeList != null) {
        				Route route = (Route)routeList.getFirst();
        				SipUri sipUri = (SipUri)route.getAddress().getURI();
        				if (sipUri.toString().contains("node_host") || sipUri.toString().contains("node_port")) {
        					String nodeHost = sipUri.getParameter("node_host");
        					int nodePort = Integer.parseInt(sipUri.getParameter("node_port"));
        					ViaHeader viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
        					viaHeader.setHost(nodeHost);
        					viaHeader.setPort(nodePort);
        				}
        			}
        			sipProvider.sendResponse(response);
        		} catch (SipException e) {
        			logger.error("Unexpected exception while sending TRYING", e);
        		} catch (ParseException e) {
        			logger.error("Unexpected exception while sending TRYING", e);
        		} catch (NumberFormatException e) {
        			logger.error("Unexpected exception while sending TRYING", e);
        		} catch (InvalidArgumentException e) {
        			logger.error("Unexpected exception while sending TRYING", e);
        		}
        	}
        	}
        else
        {
        	logger.debug("Load balancer do not send 100 TRYING, this option is disabled");
        }
        
        hints.serverAssignedNode = nextNode;
        if(!hints.subsequentRequest && dialogCreationMethods.contains(request.getMethod())) {
        	addLBRecordRoute(sipProvider, request, hints, version);
        }

        // Stateless proxies must not use internal state or ransom values when creating branch because they
        // must repeat exactly the same branches for retransmissions
        final ViaHeader via = (ViaHeader) request.getHeader(ViaHeader.NAME);
        String newBranch = via.getBranch() + callID.substring(0, Math.min(callID.length(), 5));
        // Add the via header to the top of the header list.
		ViaHeader viaHeaderExternal = null;
		ViaHeader viaHeaderInternal = null;

		if (!isRequestFromServer) {
			viaHeaderExternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
			balancerRunner.balancerContext.externalViaHost,balancerRunner.balancerContext.getExternalViaPortByTransport(outerTransport),outerTransport, newBranch + "_" + version);
			String innerTransport = transport;
			if (balancerRunner.balancerContext.terminateTLSTraffic) {
				if (innerTransport.equalsIgnoreCase(ListeningPoint.TLS))
					innerTransport = ListeningPoint.TCP;
				else if (innerTransport.equalsIgnoreCase(ListeningPointExt.WSS))
					innerTransport = ListeningPointExt.WS;
			}

			if (balancerRunner.balancerContext.isTwoEntrypoints())
				viaHeaderInternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
								balancerRunner.balancerContext.internalViaHost,balancerRunner.balancerContext.getInternalViaPortByTransport(innerTransport),innerTransport, newBranch + "zsd" + "_"	+ version);
			else
				viaHeaderInternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
								balancerRunner.balancerContext.externalViaHost,balancerRunner.balancerContext.getExternalViaPortByTransport(innerTransport),innerTransport, newBranch + "zsd" + "_" + version);
		} else {
			if (balancerRunner.balancerContext.isTwoEntrypoints())
				viaHeaderInternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
								balancerRunner.balancerContext.internalViaHost,balancerRunner.balancerContext.getInternalViaPortByTransport(transport),transport, newBranch + "zsd" + "_" + version);
			else
				viaHeaderInternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
								balancerRunner.balancerContext.externalViaHost,balancerRunner.balancerContext.getExternalViaPortByTransport(transport),transport, newBranch + "zsd" + "_" + version);

			viaHeaderExternal = balancerRunner.balancerContext.headerFactory.createViaHeader(
							balancerRunner.balancerContext.externalViaHost,balancerRunner.balancerContext.getExternalViaPortByTransport(outerTransport),outerTransport, newBranch + "zsd" + "_" + version);
		}

        if(logger.isDebugEnabled()) {
            logger.debug("ViaHeaders will be added " + viaHeaderExternal + " and " + viaHeaderInternal);
            logger.debug("Sending the request:\n" + request + "\n on the other side");
        }
        if(getLoopbackUri(request) != null) {
            logger.warn("Drop. Cannot forward to loopback the following request: " + request);
            return;
        }

		if (!isRequestFromServer) {
			request.addHeader(viaHeaderExternal);
			if (viaHeaderInternal != null)
				request.addHeader(viaHeaderInternal);

			if (balancerRunner.balancerContext.isTwoEntrypoints())
				balancerRunner.balancerContext.internalSipProvider.sendRequest(request);
			else
				balancerRunner.balancerContext.externalSipProvider.sendRequest(request);
        } else {
            // Check if the next hop is actually the load balancer again
            if(viaHeaderInternal != null) request.addHeader(viaHeaderInternal); 
            if(viaHeaderExternal != null) request.addHeader(viaHeaderExternal); 
            balancerRunner.balancerContext.externalSipProvider.sendRequest(request);
        }
   
    }

    /**
     * @param request
     * @return
     */
    private boolean securityCheck(Request request) {
        //        User-Agent: sipcli/v1.8
        //        User-Agent: friendly-scanner
        //        To: "sipvicious" <sip:100@1.1.1.1>
        //        From: "sipvicious" <sip:100@1.1.1.1>;tag=3336353363346565313363340133313330323436343236
        //        From: "1" <sip:1@87.202.36.237>;tag=3e7a78de
        Header userAgentHeader = request.getHeader("User-Agent");
        Header toHeader = request.getHeader("To");
        Header fromHeader = request.getHeader("From");

        for (String blockedValue: balancerRunner.balancerContext.blockedList){
            if(userAgentHeader != null && userAgentHeader.toString().toLowerCase().contains(blockedValue.toLowerCase())) {
                return false;
            } else if (toHeader != null && toHeader.toString().toLowerCase().contains(blockedValue.toLowerCase())) {
                return false;
            } else if (fromHeader != null && fromHeader.toString().toLowerCase().contains(blockedValue.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private RecordRouteHeader stampRecordRoute(RecordRouteHeader rrh, RouteHeaderHints hints, String transport) {
        SipURI uri = (SipURI) rrh.getAddress().getURI();
        try {        	
            logger.debug("About to stamp RecordRoute for hints:\n"+hints.serverAssignedNode.toString()+"\n");
            uri.setParameter(ROUTE_PARAM_NODE_HOST, hints.serverAssignedNode.getIp());
            uri.setParameter(ROUTE_PARAM_NODE_PORT, hints.serverAssignedNode.getProperties().get(transport.toLowerCase()+"Port").toString());
            uri.setParameter(ROUTE_PARAM_NODE_VERSION, hints.version);
        } catch (ParseException e) {
            logger.warn("Problem adding rrh" ,e);
        }
        return rrh;
    }

    private void addTwoRecordRoutes(Request request, RecordRouteHeader first,RecordRouteHeader second, RouteHeaderHints hints, String transport) {
        if(logger.isDebugEnabled()) {
            logger.debug("adding Record Router Header :" + first);
        }

        
        try
        {
        	request.addFirst(stampRecordRoute((RecordRouteHeader) first.clone(), hints, transport));        	
        }
        catch(Exception ex)
        {
        	//should not occure
        }
        
        if(logger.isDebugEnabled()) {
            logger.debug("adding Record Router Header :" + second);
        }

        try
        {
        	request.addFirst(stampRecordRoute((RecordRouteHeader) second.clone(), hints, transport));        	
        }
        catch(Exception ex)
        {
        	//should not occure
        }
    }

    /**
     * @param sipProvider
     * @param request
     * @param hints 
     * @throws ParseException
     */
    private void addLBRecordRoute(SipProvider sipProvider, Request request, RouteHeaderHints hints, String version)
            throws ParseException {	
    	if(logger.isDebugEnabled()) {
            logger.debug("adding Record Router Header :" + balancerRunner.balancerContext.activeExternalHeader);
        }
                
		String transport = ((ViaHeader) request.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();

		if (sipProvider.equals(balancerRunner.balancerContext.externalSipProvider)) {
			int transportIndex = TLS;
			int internalTransportIndex = TLS;
			if (balancerRunner.balancerContext.terminateTLSTraffic) {
				internalTransportIndex = TCP;
			}

			if (transport.equalsIgnoreCase(ListeningPoint.UDP)) {
				transportIndex = UDP;
				internalTransportIndex = UDP;
			} else if (transport.equalsIgnoreCase(ListeningPoint.TCP)) {
				transportIndex = TCP;
				internalTransportIndex = TCP;
			} else if (transport.equalsIgnoreCase(ListeningPointExt.WS)) {
				transportIndex = WS;
				internalTransportIndex = WS;
			} else if (transport.equalsIgnoreCase(ListeningPointExt.WSS)) {
				transportIndex = WSS;
				if (balancerRunner.balancerContext.terminateTLSTraffic) {
					internalTransportIndex = WS;
				} else {
					internalTransportIndex = WSS;
				}
			}

			// comes from client
			addTwoRecordRoutes(request,
			balancerRunner.balancerContext.activeExternalHeader[transportIndex],
			balancerRunner.balancerContext.activeInternalHeader[internalTransportIndex],
			hints, transport);
		} else {
			int transportIndex = TLS;
			int externalTransportIndex = TLS;
			String externalTransport = transport;
			URI requestURI = ((Request) request).getRequestURI();
			if (requestURI.isSipURI()) {
				if (((SipUri) requestURI).getTransportParam() != null)
					externalTransport = ((SipUri) requestURI).getTransportParam();
			}

			if (transport.equalsIgnoreCase(ListeningPoint.UDP))
				transportIndex = UDP;
			else if (transport.equalsIgnoreCase(ListeningPoint.TCP))
				transportIndex = TCP;
			else if (transport.equalsIgnoreCase(ListeningPointExt.WS))
				transportIndex = WS;
			else if (transport.equalsIgnoreCase(ListeningPointExt.WSS))
				transportIndex = WSS;

			if (externalTransport.equalsIgnoreCase(ListeningPoint.UDP))
				externalTransportIndex = UDP;
			else if (externalTransport.equalsIgnoreCase(ListeningPoint.TCP))
				externalTransportIndex = TCP;
			else if (externalTransport.equalsIgnoreCase(ListeningPointExt.WS))
				externalTransportIndex = WS;
			else if (externalTransport.equalsIgnoreCase(ListeningPointExt.WSS))
				externalTransportIndex = WSS;

			// comes from app server
			addTwoRecordRoutes(	request,
					balancerRunner.balancerContext.activeInternalHeader[transportIndex],
					balancerRunner.balancerContext.activeExternalHeader[externalTransportIndex],
					hints, transport);
			if(logger.isInfoEnabled()) {
				logger.info("Will patch Request : \"" + request.getRequestURI()	+ "\" to provide public IP address for the RecordRoute header");
			}
			patchSipMessageForNAT(request);
        }
    }

    /**
     * This will check if in the route header there is information on which node from the cluster send the request.
     * If the request is not received from the cluster, this information will not be present. 
     * @param routeHeader the route header to check
     * @return the corresponding Sip Node
     */
    private SIPNode checkRouteHeaderForSipNode(SipURI routeSipUri) {
        SIPNode node = null;
        String hostNode = routeSipUri.getParameter(ROUTE_PARAM_NODE_HOST);
        String hostPort = routeSipUri.getParameter(ROUTE_PARAM_NODE_PORT);
        String hostVersion = routeSipUri.getParameter(ROUTE_PARAM_NODE_VERSION);
        if(hostNode != null && hostPort != null) {
            int port = Integer.parseInt(hostPort);
            String transport = routeSipUri.getTransportParam();
            if(transport == null) transport = ListeningPoint.UDP;
            node = register.getNode(hostNode, port, transport, hostVersion);                       
        }
        return node;
    }

	private String getRouteHeadersMeantForLB(Request request) {
		@SuppressWarnings("unchecked")
		ListIterator<RouteHeader> headers = request
				.getHeaders(RouteHeader.NAME);
		RouteHeader routeHeader = null;
		if (headers.hasNext())
			routeHeader = headers.next();

		if (routeHeader != null) {
			SipURI routeUri = (SipURI) routeHeader.getAddress().getURI();
			String transport = ((ViaHeader) request.getHeader("Via"))
					.getTransport();
			if (routeUri.getTransportParam() != null)
				transport = routeUri.getTransportParam();

			if (!isRouteHeaderExternal(routeUri.getHost(), routeUri.getPort(),
					transport)) {
				routeHeader = null;
				if (headers.hasNext())
					routeHeader = headers.next();

				if (routeHeader != null) {
					routeUri = (SipURI) routeHeader.getAddress().getURI();
					transport = ((ViaHeader) request.getHeader("Via"))
							.getTransport();
					if (routeUri.getTransportParam() != null)
						transport = routeUri.getTransportParam();

					if (!isRouteHeaderExternal(routeUri.getHost(),
							routeUri.getPort(), transport)) {
						return transport;
					}
				}
				return transport;
			}
		}

		return null;
	}

    /**
     * Remove the different route headers that are meant for the Load balancer. 
     * There is two cases here :
     * <ul>
     * <li>* Requests coming from external and going to the cluster : dialog creating requests can have route header so that they go through the LB and subsequent requests 
     * will have route headers since the LB record routed</li>
     * <li>* Requests coming from the cluster and going to external : dialog creating requests can have route header so that they go through the LB - those requests will define in the route header
     * the originating node of the request so that that subsequent requests are routed to the originating node if still alive</li>
     * </ul>
     * 
     * @param request
     */
    private RouteHeaderHints removeRouteHeadersMeantForLB(Request request) {
        if(logger.isDebugEnabled()) {
            logger.debug("Checking if there is any route headers meant for the LB to remove...");
        }
        SIPNode node = null;
        String callVersion = null;
        int numberOfRemovedRouteHeaders = 0;

        //Removing first routeHeader if it is for the sip balancer
        RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
        if(routeHeader != null) {
            SipURI routeUri = (SipURI)routeHeader.getAddress().getURI();
            callVersion = routeUri.getParameter(ROUTE_PARAM_NODE_VERSION);

			String transport = ((ViaHeader) request.getHeader("Via")).getTransport();
			if (routeUri.getTransportParam() != null)
				transport = routeUri.getTransportParam();

			if (!isRouteHeaderExternal(routeUri.getHost(), routeUri.getPort(),	transport)) {
				if (logger.isDebugEnabled()) {
					logger.debug("this route header is for the LB removing it "	+ routeUri);
				}

                numberOfRemovedRouteHeaders ++;

                request.removeFirst(RouteHeader.NAME);
                routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
                //since we used double record routing we may have 2 routes corresponding to us here
                // for ACK and BYE from caller for example
                node = checkRouteHeaderForSipNode(routeUri);
                if(routeHeader != null) {

                    routeUri = (SipURI)routeHeader.getAddress().getURI();
					transport = ((ViaHeader) request.getHeader("Via")).getTransport();
					if (routeUri.getTransportParam() != null)
						transport = routeUri.getTransportParam();
						
					if (!isRouteHeaderExternal(routeUri.getHost(),routeUri.getPort(), transport)) {
						if (logger.isDebugEnabled()) {
							logger.debug("this route header is for the LB removing it "	+ routeUri);
						}

                        numberOfRemovedRouteHeaders ++;

                        request.removeFirst(RouteHeader.NAME);
                        if(node == null) {
                        	node = checkRouteHeaderForSipNode(routeUri);                            
                        }

                        // SIPP sometimes appends more headers and lets remove them here. There is no legitimate reason
                        // more than two SIP LB headers to be place next to each-other, so this cleanup is SAFE!
                        boolean moreHeaders = true;
                        while(moreHeaders) {
                            RouteHeader extraHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
                            if(extraHeader != null) {
                                SipURI u = (SipURI)extraHeader.getAddress().getURI();

								transport = ((ViaHeader) request.getHeader("Via")).getTransport();
								if (u.getTransportParam() != null)
									transport = u.getTransportParam();

								if (!isRouteHeaderExternal(u.getHost(),
										u.getPort(), transport)) {
                                    numberOfRemovedRouteHeaders ++;
                                    request.removeFirst(RouteHeader.NAME);
                                } else {
                                    moreHeaders = false;
                                }
                            } else {
                                moreHeaders = false;
                            }
                        }
                    }
                }
            }	                
        }

        if(node == null) {
            if(request.getRequestURI().isSipURI()) {
            	node = checkRouteHeaderForSipNode((SipURI) request.getRequestURI());                
            }
        }

        //logger.info(request.ge + " has this hint " + node);
        ToHeader to = (ToHeader)(request.getHeader(ToHeader.NAME));

        /*
         * We determine if this is subsequent based on To tag instead of checking route header metadata.
         */
        boolean subsequent = to.getTag() != null;

        if(logger.isDebugEnabled()) {
            logger.debug("Number of removed Route headers is " + numberOfRemovedRouteHeaders);
        }
        if(numberOfRemovedRouteHeaders != 2 && subsequent) {
            logger.warn("A subsequent request should have two Route headers. Number of removed Route headers is " + numberOfRemovedRouteHeaders
                    + ". This indicates a client is removing important headers.");
        }

        return new RouteHeaderHints(node, subsequent, callVersion);
    }

    /**
     * Check if the sip uri is meant for the LB same host and same port
     * @param sipUri sip Uri to check 
     * @return
     */
    private boolean isRouteHeaderExternal(String host, int port,String transport) 
    {  

		if (host.equalsIgnoreCase(balancerRunner.balancerContext.externalHost))
		{
			if(balancerRunner.balancerContext.getExternalPortByTransport(transport)==port)
				return false;
			
			if(balancerRunner.balancerContext.getExternalLoadBalancerPortByTransport(transport)==port)
				return false;
		}

		if(host.equalsIgnoreCase(balancerRunner.balancerContext.internalHost))
		{
			if(balancerRunner.balancerContext.getInternalPortByTransport(transport)==port)
				return false;
			
			if(balancerRunner.balancerContext.getInternalLoadBalancerPortByTransport(transport)==port)
				return false;
		}
		
		if(host.equalsIgnoreCase(balancerRunner.balancerContext.externalIpLoadBalancerAddress))
		{
			if(balancerRunner.balancerContext.getExternalPortByTransport(transport)==port)
				return false;
			
			if(balancerRunner.balancerContext.getExternalLoadBalancerPortByTransport(transport)==port)
				return false;
		}
		
		if(host.equalsIgnoreCase(balancerRunner.balancerContext.internalIpLoadBalancerAddress))
		{
			if(balancerRunner.balancerContext.getInternalPortByTransport(transport)==port)
				return false;
			
			if(balancerRunner.balancerContext.getInternalLoadBalancerPortByTransport(transport)==port)
				return false;
		}
		
		if(host.equalsIgnoreCase(balancerRunner.balancerContext.publicIP))
		{
			if(balancerRunner.balancerContext.getExternalPortByTransport(transport)==port)
				return false;
			
			if(balancerRunner.balancerContext.getExternalLoadBalancerPortByTransport(transport)==port)
				return false;
			
			if(balancerRunner.balancerContext.getInternalPortByTransport(transport)==port)
				return false;
			
			if(balancerRunner.balancerContext.getInternalLoadBalancerPortByTransport(transport)==port)
				return false;
		}

		return true;		
    }

    /**
     * @param sipProvider
     * @param request
     * @throws InvalidArgumentException
     * @throws ParseException
     * @throws SipException
     */
    private void decreaseMaxForwardsHeader(SipProvider sipProvider,
            Request request) throws InvalidArgumentException, ParseException,
            SipException {
        // Decreasing the Max Forward Header
        if(logger.isDebugEnabled()) {
            logger.debug("Decreasing  the Max Forward Header ");
        }
        MaxForwardsHeader maxForwardsHeader = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);
        if (maxForwardsHeader == null) {
            maxForwardsHeader = balancerRunner.balancerContext.headerFactory.createMaxForwardsHeader(70);
            request.addHeader(maxForwardsHeader);
        } else {
            if(maxForwardsHeader.getMaxForwards() - 1 > 0) {
                maxForwardsHeader.setMaxForwards(maxForwardsHeader.getMaxForwards() - 1);
            } else {
                //Max forward header equals to 0, thus sending too many hops response
                Response response = balancerRunner.balancerContext.messageFactory.createResponse
                        (Response.TOO_MANY_HOPS,request);			
                sipProvider.sendResponse(response);
            }
        }
    }

    /**
     * @param originalRequest
     * @param serverTransaction
     * @throws ParseException
     * @throws SipException
     * @throws InvalidArgumentException
     * @throws TransactionUnavailableException
     */

    /*
     * (non-Javadoc)
     * @see javax.sip.SipListener#processResponse(javax.sip.ResponseEvent)
     */
    public void processResponse(ResponseEvent responseEvent) {
    	SipProvider sipProvider = (SipProvider) responseEvent.getSource();
        Response originalResponse = responseEvent.getResponse();
        if(logger.isDebugEnabled()) {
            logger.debug("got response :\n" + originalResponse);
        }

        updateStats(originalResponse);

        final Response response = (Response) originalResponse; 
        SIPNode senderNode = getSenderNode(response);
        if(senderNode != null) {
        	if(logger.isDebugEnabled()) {
    			logger.debug("Updating Timestamp of sendernode: " + senderNode);
    		}
            senderNode.updateTimerStamp();
        }

        // Topmost via headers is me. As it is response to external request
        ViaHeader viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);

        String branch = viaHeader.getBranch();
        int versionDelimiter = branch.lastIndexOf('_');
        String version = branch.substring(versionDelimiter + 1);

        InvocationContext ctx = balancerRunner.getInvocationContext(version);

        if(viaHeader!=null && !isRouteHeaderExternal(viaHeader.getHost(), viaHeader.getPort(), viaHeader.getTransport())) {
            response.removeFirst(ViaHeader.NAME);
        }
        
        viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
        String transport=viaHeader.getTransport();
        
        if(viaHeader!=null && !isRouteHeaderExternal(viaHeader.getHost(), viaHeader.getPort(), viaHeader.getTransport())) {
            response.removeFirst(ViaHeader.NAME);
        }
        
        boolean fromServer = false;
        if(balancerRunner.balancerContext.isTwoEntrypoints()) {
            fromServer = sipProvider.equals(balancerRunner.balancerContext.internalSipProvider);
            if(logger.isDebugEnabled()) {
    			logger.debug("fromServer : "+ fromServer + ", sipProvider " + sipProvider + ", internalSipProvider " + balancerRunner.balancerContext.internalSipProvider);
    		}
        } else {
            fromServer = senderNode == null;
            if(logger.isDebugEnabled()) {
    			logger.debug("fromServer : "+ fromServer + ", senderNode " + senderNode);
    		}
        }

        if(fromServer) {
            /*
			if("true".equals(balancerRunner.balancerContext.properties.getProperty("removeNodesOn500Response")) && response.getStatusCode() == 500) {
				// If the server is broken remove it from the list and try another one with the next retransmission
				if(!(sourceNode instanceof ExtraServerNode)) {
					if(balancerRunner.balancerContext.nodes.size()>1) {
						balancerRunner.balancerContext.nodes.remove(sourceNode);
						balancerRunner.balancerContext.balancerAlgorithm.nodeRemoved(sourceNode);
					}
				}
			}*/  
        	
        	if(balancerRunner.balancerContext.publicIP != null && balancerRunner.balancerContext.publicIP.trim().length() > 0) {
        		if(logger.isDebugEnabled()) {
        			logger.debug("Will add Record-Route header to response with public IP Address: "+balancerRunner.balancerContext.publicIP);
        		}
                patchSipMessageForNAT(response);
            }
            // https://github.com/RestComm/load-balancer/issues/45 Adding sender node for the algorithm to be available
        	((ResponseExt)response).setApplicationData(senderNode);
        	
            ctx.balancerAlgorithm.processInternalResponse(response);
            try {	
                if(logger.isDebugEnabled()) {
                    logger.debug("from server sending response externally " + response);
                }
                balancerRunner.balancerContext.externalSipProvider.sendResponse(response);
            } catch (Exception ex) {
                logger.error("Unexpected exception while forwarding the response \n" + response, ex);
            }
        } else {
        	try {
                SIPMessage message = (SIPMessage)response;

                String initialRemoteAddr = message.getPeerPacketSourceAddress().getHostAddress();
                String initialRemotePort = String.valueOf(message.getPeerPacketSourcePort());

                Header remoteAddrHeader = null;
                Header remotePortHeader = null;
                try {
                	HeaderFactory hf=SipFactory.getInstance().createHeaderFactory();
                    remoteAddrHeader = hf.createHeader("X-Sip-Balancer-InitialRemoteAddr", initialRemoteAddr);
                    remotePortHeader = hf.createHeader("X-Sip-Balancer-InitialRemotePort", initialRemotePort);
                } catch (PeerUnavailableException e) {
                    logger.error("Unexpected exception while creating custom headers for REGISTER message ", e);
                } catch (ParseException e) {
                    logger.error("Unexpected exception while creating custom headers for REGISTER message ", e);
                }
                if (remoteAddrHeader != null)
                    response.addHeader(remoteAddrHeader);
                if (remotePortHeader != null)
                    response.addHeader(remotePortHeader);
                                
                if(balancerRunner.balancerContext.isTwoEntrypoints()) {
            	    ctx.balancerAlgorithm.processExternalResponse(response);
                   
                    if(logger.isDebugEnabled()) {
                        logger.debug("two entry points: from external sending response " + response);
                    }
                    balancerRunner.balancerContext.internalSipProvider.sendResponse(response);
                } else {
                	if(!comesFromInternalNode(response,ctx,initialRemoteAddr,message.getPeerPacketSourcePort(),transport))
                		ctx.balancerAlgorithm.processExternalResponse(response);
                	else
                		ctx.balancerAlgorithm.processInternalResponse(response);
                	
                    if(logger.isDebugEnabled()) {
                        logger.debug("one entry point: from external sending response " + response);
                    }
                    balancerRunner.balancerContext.externalSipProvider.sendResponse(response);
                }
            } catch (Exception ex) {
                logger.error("Unexpected exception while forwarding the response \n" + response, ex);
            }
        }
    }

    //need to verify that comes from external in case of single leg
    protected Boolean comesFromInternalNode(Response externalResponse,InvocationContext ctx,String host,Integer port,String transport)
	{
		boolean found = false;
		if(host!=null && port!=null)
		{
			if(ctx.sipNodeMap.containsKey(new KeySip(host, port)))
				found = true;
//			for(SIPNode node : ctx.nodes) {
//				if(node.getIp().equals(host)) {
//					if(port.equals(node.getProperties().get(transport+"Port"))) {
//						found = true;
//						break;
//					}
//				}
//			}
		}
		return found;
	}
    
    /**
     * Patch Response for NAT Environment where the Load Balancer runs on a private IP but UAs need to know the LB public IP to 
     * send back subsequent requests to it.
     * @param sipMessage the response to patch
     * @throws PeerUnavailableException 
     * @throws ParseException 
     */
    public void patchSipMessageForNAT(javax.sip.message.Message sipMessage) {
        //Need to patch the response so the subsequent requests are directly correctly at the public IP Address of the LB
        // Useful for NAT environment such as Amazon EC2
        if (balancerRunner.balancerContext.publicIP != null && !balancerRunner.balancerContext.publicIP.isEmpty()) {
            int udpPort = balancerRunner.balancerContext.externalUdpPort;
            int tcpPort = balancerRunner.balancerContext.externalTcpPort;
            int tlsPort = balancerRunner.balancerContext.externalTlsPort;
            int wsPort = balancerRunner.balancerContext.externalWsPort;
            int wssPort = balancerRunner.balancerContext.externalWssPort;

			String transport = null;
			if (sipMessage instanceof Response)
				transport = ((ViaHeader) sipMessage.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
			else {
				URI requestURI = ((Request) sipMessage).getRequestURI();
				transport = ((ViaHeader) sipMessage.getHeader(ViaHeader.NAME)).getTransport().toLowerCase();
				if (requestURI.isSipURI()) {
					if (((SipUri) requestURI).getTransportParam() != null)
						transport = ((SipUri) requestURI).getTransportParam();
				}
			}

            String privateIp = balancerRunner.balancerContext.host;
            @SuppressWarnings("unchecked")
			ListIterator<RecordRouteHeader> recordRouteHeaderList = sipMessage.getHeaders(RecordRouteHeader.NAME);
            
            try {
                HeaderFactory headerFactory = SipFactory.getInstance().createHeaderFactory(); 
                Header contactHeader = null;
                
                if (!recordRouteHeaderList.hasNext()) {
                	if(logger.isDebugEnabled()) {
                		logger.debug("Record Route header list is empty");
                	}
                }

                while (recordRouteHeaderList.hasNext()) {
                    RecordRouteHeader recordRouteHeader = (RecordRouteHeader) recordRouteHeaderList.next();
                    
                    if(logger.isDebugEnabled()) {
                    	logger.debug("About to check Record-Route header: "+recordRouteHeader.toString());
                    }
                    if (((SipURI)recordRouteHeader.getAddress().getURI()).getHost().equals(privateIp)) { //If this RecordRoute header is from LB
                        if (((SipURI)recordRouteHeader.getAddress().getURI()).getPort()==udpPort 
                        		|| ((SipURI)recordRouteHeader.getAddress().getURI()).getPort()==tcpPort
                        		|| ((SipURI)recordRouteHeader.getAddress().getURI()).getPort()==tlsPort
                        		|| ((SipURI)recordRouteHeader.getAddress().getURI()).getPort()==wsPort
                        		|| ((SipURI)recordRouteHeader.getAddress().getURI()).getPort()==wssPort) { // And if the port is the external Port
                            SipURI sipURI = (SipURI) recordRouteHeader.getAddress().getURI();
                            sipURI.setHost(balancerRunner.balancerContext.publicIP);                            
                        }                        
                    } else {
                    	if(logger.isDebugEnabled()) {
                    		logger.debug("Didn't patched the Record-Route because ip address is not the private one: "+((SipURI)recordRouteHeader.getAddress().getURI()).getHost());
                    	}
                    }
                }

                if (sipMessage.getHeader(ContactHeader.NAME) != null) {                	
                    final String displayedName = ((ContactHeader)sipMessage.getHeader("Contact")).getAddress().getDisplayName();
                    
                    int currPort=balancerRunner.balancerContext.getExternalPortByTransport(transport);
                    
                    if (displayedName != null && !displayedName.isEmpty()) {
                        final String contactURI = "sip:"+displayedName+"@"+balancerRunner.balancerContext.publicIP+":"+currPort;
                        contactHeader = headerFactory.createHeader("Contact", contactURI);
                        ((ContactHeader)contactHeader).getAddress().setDisplayName(displayedName);
                    } else {
                        final String contactURI = "sip:"+balancerRunner.balancerContext.publicIP+":"+currPort;
                        contactHeader = headerFactory.createHeader("Contact", contactURI);
                    }
                    if (contactHeader != null) {
                        sipMessage.removeFirst("Contact");
                        sipMessage.addHeader(contactHeader);
                    }
                }

                if(logger.isDebugEnabled() && contactHeader != null) {
                		logger.debug("Patched the Contact header with : "+contactHeader.toString());
                }
            } catch (PeerUnavailableException peerUnavailableException) {
                logger.error("Unexpected exception while forwarding the response \n" + sipMessage, peerUnavailableException);
            } catch (ParseException parseException) {
                logger.error("Unexpected exception while forwarding the response \n" + sipMessage, parseException);
            } catch (NullPointerException e) {
                logger.error("Unexpected exception while forwarding the response \n" + sipMessage, e);
            }
        }
    }        

    /*
     * (non-Javadoc)
     * @see javax.sip.SipListener#processTimeout(javax.sip.TimeoutEvent)
     */
    public void processTimeout(TimeoutEvent timeoutEvent) {
        Transaction transaction = null;
        if(timeoutEvent.isServerTransaction()) {
            transaction = timeoutEvent.getServerTransaction();
            if(logger.isDebugEnabled()) {
                logger.debug("timeout => " + transaction.getRequest().toString());
            }
        } else {
            transaction = timeoutEvent.getClientTransaction();
            if(logger.isDebugEnabled()) {
                logger.debug("timeout => " + transaction.getRequest().toString());
            }
        }
        String callId = ((CallIdHeader)transaction.getRequest().getHeader(CallIdHeader.NAME)).getCallId();
        register.unStickSessionFromNode(callId);
    }

    /*
     * (non-Javadoc)
     * @see javax.sip.SipListener#processTransactionTerminated(javax.sip.TransactionTerminatedEvent)
     */
    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        Transaction transaction = null;
        if(transactionTerminatedEvent.isServerTransaction()) {
            transaction = transactionTerminatedEvent.getServerTransaction();
            if(logger.isDebugEnabled()) {
                logger.debug("timeout => " + transaction.getRequest().toString());
            }
        } else {
            transaction = transactionTerminatedEvent.getClientTransaction();
            if(logger.isDebugEnabled()) {
                logger.debug("timeout => " + transaction.getRequest().toString());
            }
        }
        if(Request.BYE.equals(transaction.getRequest().getMethod())) {
            String callId = ((CallIdHeader)transaction.getRequest().getHeader(CallIdHeader.NAME)).getCallId();
            register.unStickSessionFromNode(callId);
        }
    }

    /**
     * @return the requestsProcessed
     */
    public long getNumberOfRequestsProcessed() {
        return balancerRunner.balancerContext.requestsProcessed.get();
    }

    /**
     * @return the responsesProcessed
     */
    public long getNumberOfResponsesProcessed() {
        return balancerRunner.balancerContext.responsesProcessed.get();
    }
    
    /**
     * @return the bytesTransfered
     */
    public long getNumberOfBytesTransferred()
    {
    	return balancerRunner.balancerContext.bytesTransferred.get();
    }

    /**
     * @return the requestsProcessedByMethod
     */
    public long getRequestsProcessedByMethod(String method) {
        AtomicLong requestsProcessed = balancerRunner.balancerContext.requestsProcessedByMethod.get(method);
        if(requestsProcessed != null) {
            return requestsProcessed.get();
        }
        return 0;
    }

    /**
     * @return the responsesProcessedByStatusCode
     */
    public long getResponsesProcessedByStatusCode(String statusCode) {
        AtomicLong responsesProcessed = balancerRunner.balancerContext.responsesProcessedByStatusCode.get(statusCode);
        if(responsesProcessed != null) {
            return responsesProcessed.get();
        }
        return 0;
    }

    public Map<String, AtomicLong> getNumberOfRequestsProcessedByMethod() {
        return balancerRunner.balancerContext.requestsProcessedByMethod;
    }

    public Map<String, AtomicLong> getNumberOfResponsesProcessedByStatusCode() {
        return balancerRunner.balancerContext.responsesProcessedByStatusCode;
    }

    public BalancerContext getBalancerAlgorithmContext() {
        return balancerRunner.balancerContext;
    }

    public void setBalancerAlgorithmContext(
            BalancerContext balancerAlgorithmContext) {
        balancerRunner.balancerContext = balancerAlgorithmContext;
    }

    /**
     * @param skipStatistics the skipStatistics to set
     */
    public void setGatherStatistics(boolean skipStatistics) {
        balancerRunner.balancerContext.gatherStatistics = skipStatistics;
    }

    /**
     * @return the skipStatistics
     */
    public boolean isGatherStatistics() {
        return balancerRunner.balancerContext.gatherStatistics;
    }
}
