/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
 * by the @authors tag.
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
package org.mobicents.tools.telestaxproxy.sip.balancer;

import static org.jboss.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import gov.nist.javax.sip.header.SIPHeader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.jboss.netty.handler.ssl.JdkSslClientContext;
import org.jboss.netty.handler.ssl.SslContext;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.logging.InternalLogLevel;
import org.mobicents.tools.sip.balancer.CallIDAffinityBalancerAlgorithm;
import org.mobicents.tools.sip.balancer.SIPNode;
import org.mobicents.tools.telestaxproxy.dao.DaoManager;
import org.mobicents.tools.telestaxproxy.dao.PhoneNumberDaoManager;
import org.mobicents.tools.telestaxproxy.dao.RestcommInstanceDaoManager;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth.BandwidthMessageProcessor;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth.BandwidthOrderRequest;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth.BandwidthOrderResponse;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth.BandwidthReleaseRequest;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth.BandwidthReleaseResponse;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth.BandwidthResponse;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth.BandwidthSearchResponse;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth.BandwidthStorage;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.common.ProvisionProvider;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.common.ProxyRequest;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.voipinnovation.VoipInnovationMessageProcessor;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.voipinnovation.VoipInnovationProvisionRequest;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.voipinnovation.VoipInnovationResponse;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.voipinnovation.VoipInnovationStorage;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.RestcommInstance;

import com.thoughtworks.xstream.XStream;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class TelestaxProxyAlgorithm extends CallIDAffinityBalancerAlgorithm {

    private static Logger logger = Logger.getLogger(TelestaxProxyAlgorithm.class.getCanonicalName());
    private Properties properties;
    private String viLogin, viPassword, viEndpoint, viUri;
    private VoipInnovationMessageProcessor viDispatcher;
    private String bwLogin, bwPassword, bwAccountId, bwSiteId, bwUri;
    private BandwidthMessageProcessor bwDispatcher;
    private volatile Channel outboundChannel;
    private XStream xstream;
    private DaoManager daoManager;
    private RestcommInstanceDaoManager restcommInstanceManager;
    private PhoneNumberDaoManager phoneNumberManager;
    private List<String> blockedList;
//    private String externalIP;

    @Override
    public void init() {        
        properties = getBalancerContext().properties;

        viLogin = properties.getProperty("vi-login");
        viPassword = properties.getProperty("vi-password");
        viEndpoint = properties.getProperty("vi-endpoint");
        viUri = properties.getProperty("vi-uri");

        bwLogin = properties.getProperty("bw-login");
        bwPassword = properties.getProperty("bw-password");
        bwAccountId = properties.getProperty("bw-accountId");
        bwSiteId = properties.getProperty("bw-siteId");
        bwUri = properties.getProperty("bw-uri");

        String myBatisConf = properties.getProperty("mybatis-config");
        File mybatisConfFile = null;
        if(myBatisConf != null && !myBatisConf.equalsIgnoreCase("")) 
            mybatisConfFile = new File(myBatisConf);

        String blackListLocation = properties.getProperty("blacklist");
        File blackListFile = null;
        if (blackListLocation != null && !blackListLocation.equalsIgnoreCase(""))
            blackListFile = new File(blackListLocation);
        
        if(blackListFile != null)
            processBlackList(blackListFile);
        
        //Setup myabtis and dao managers
        SqlSessionFactory sessionFactory = null;
        try {
            if(mybatisConfFile != null) {
                daoManager = new DaoManager(mybatisConfFile);
            } else {
                daoManager = new DaoManager();
            }
            sessionFactory = daoManager.getSessionFactory();
        } catch (IOException e) {
            logger.error("mybatis.xml configuration file not found: "+e);
        } catch (Exception e) {
            logger.error("Exception while trying to get SqlSessionFactory: "+e);
        }
        restcommInstanceManager = new RestcommInstanceDaoManager(sessionFactory);
        phoneNumberManager = new PhoneNumberDaoManager(sessionFactory);

        viDispatcher = new VoipInnovationMessageProcessor(viLogin,viPassword,viEndpoint,viUri,restcommInstanceManager, phoneNumberManager);
        bwDispatcher = new BandwidthMessageProcessor(bwLogin, bwPassword, bwAccountId, bwSiteId, bwUri, restcommInstanceManager, phoneNumberManager);

        //        externalIP = properties.getProperty("public-ip", super.getBalancerContext().host);

        //        String blockedValues = properties.getProperty("blocked-values", "sipvicious,sipcli,friendly-scanner");
        //        populateBlockedList(blockedValues);

        super.init();
    }

//    /**
//     * @param blockedValues The values should be seperated by comma ","
//     */
//    private void populateBlockedList(String blockedValues) {
//        blockedList = new ArrayList<String>(Arrays.asList(blockedValues.split(",")));
//    }
    

    private void processBlackList(final File blackListFile) {
        blockedList = Collections.synchronizedList(new ArrayList<String>());
        try {
            FileReader fr = new FileReader(blackListFile);
            BufferedReader br = new BufferedReader(fr);
            String stringRead = br.readLine();
            while (stringRead != null) {
                blockedList.addAll(Arrays.asList(stringRead.split(",")));

                stringRead = br.readLine();
            }
            br.close();
        } catch (IOException e) {
            logger.error("Exception while processing the black list");
        }
    }
    
    @Override
    public boolean blockInternalRequest(Request request) {
        boolean blockDestination = false;
        if (blockedList != null && !blockedList.isEmpty()) {
            String did = ((SipURI)request.getRequestURI()).getUser();
            if (did == null || did.equalsIgnoreCase("")){
                ToHeader toHeader = (ToHeader) request.getHeader("To");
                did = ((SipURI)toHeader.getAddress().getURI()).getUser();
            }
            if (did != null) {
                did = did.replaceFirst("^\\+", "");
                did = did.replaceFirst("^00", "");
                did = did.replaceFirst("^011", "");
            }
            for (String blocked: blockedList) {
                if (did.startsWith(blocked)) {
                    blockDestination = true;
                    break;
                }
            }
        }
        return blockDestination;
    }

    @Override
    public SIPNode processExternalRequest(Request request) {
//        if (!securityCheck(request)){
//            logger.warn("Request failed at the security check:\n"+request);
//            return null;
//        }

        String callId = ((SIPHeader) request.getHeader(headerName)).getValue();
        String transport = ((SipURI)request.getRequestURI()).getTransportParam() == null ? "udp" : ((SipURI)request.getRequestURI()).getTransportParam() ;
        String did = ((SipURI)request.getRequestURI()).getUser();
        if (did == null || did.equalsIgnoreCase("")){
            logger.info("Did is null or empty, most probably this is a re-invite without hints in the Route header. Will try to match request to a node using To header");
            ToHeader toHeader = (ToHeader) request.getHeader("To");
            did = ((SipURI)toHeader.getAddress().getURI()).getUser();
            logger.info("Did found: "+did);
        }

        if (!super.callIdMap.containsKey(callId)) {
            logger.info("Telestax-Proxy: Got new Request: "+request.getMethod()+" "+request.getRequestURI().toString()+" will check which node to dispatch");
            SIPNode node = null;
            if (did != null) {
                did = did.replaceFirst("\\+1", "");
                did = did.replaceFirst("^001", "");
                did = did.replaceFirst("^1", "");

                if (phoneNumberManager.didExists(did)) {
                    RestcommInstance restcommInstance = phoneNumberManager.getInstanceByDid(did);
                    for (SIPNode tempNode: invocationContext.nodes) {
                        try {

                            String ipAddressToCheck = tempNode.getIp()+":"+tempNode.getProperties().get(transport+"Port");

                            String restcommAddress = restcommInstance.getAddressForTransport(transport);

                            logger.debug("Going to check if node ip :"+ipAddressToCheck+" equals to :"+restcommAddress);
                            if (ipAddressToCheck.equalsIgnoreCase(restcommAddress)){
                                node = tempNode;
                            }
                        } catch (Exception e) {
                            logger.info("Exception, did was: "+did, e);
                        }
                    }
                } 
                else {
                    logger.info("Did :"+did+" couldn't matched to a restcomm instance. Going to super for node selection");
                } 
            }
            if (node != null) {
                logger.info("Node found for incoming request: "+request.getRequestURI()+" Will route to node: "+node);
                super.callIdMap.put(callId, node);
                return node;
            } else {
                logger.info("Telestax-Proxy: Node is null. Will check with IP Address");
                ToHeader toHeader = (ToHeader) request.getHeader("To");
                String host = ((SipURI)toHeader.getAddress().getURI()).getHost();
                String toHeaderDid = ((SipURI)toHeader.getAddress().getURI()).getUser();
                logger.info("Will try to get the Restcomm instance by Public IP Address: "+host+" or with Did: "+toHeaderDid);
                RestcommInstance restcommInstance = null;
                restcommInstance = restcommInstanceManager.getInstanceByPublicIpAddress(host);
                if (restcommInstance == null) {
                    restcommInstance = phoneNumberManager.getInstanceByDid(did);
                }
                if (restcommInstance != null) {
                    for (SIPNode tempNode: invocationContext.nodes) {
                        try {
                            String ipAddressToCheck = tempNode.getIp()+":"+tempNode.getProperties().get(transport+"Port");

                            String restcommAddress = restcommInstance.getAddressForTransport(transport);

                            logger.debug("Going to check if node ip :"+ipAddressToCheck+" equals to :"+restcommAddress);
                            if (ipAddressToCheck.equalsIgnoreCase(restcommAddress)){
                                node = tempNode;
                            }
                        } catch (Exception e) {
                            logger.info("Exception, did was: "+did, e);
                        }
                    }
                }
                if(node != null) {
                    logger.info("Node found for incoming request: "+request.getRequestURI()+" using the ip address. Will route to node: "+node);
                    super.callIdMap.put(callId, node);
                    return node;
                } else {
                    logger.info("Node is null. Going to super for node selection");
                    return super.processExternalRequest(request);
                }
            }
        } else {
            logger.info("Telestax-Proxy: Calld-id is already known, going to super for node selection");
            return super.processExternalRequest(request);
        }
    }


    @Override
    public void processInternalResponse(Response response) {
    	logger.debug("internal response");
//        //Need to patch the response so it 
//        int port = super.getBalancerContext().externalPort;
//        String contactURI = "sip:"+externalIP+":"+port;
//        String recordRouteURI = "sip:"+externalIP+":"+port+";lr";
//        RecordRouteHeader existingRecordRouteHeader = (RecordRouteHeader) response.getHeader("Record-Route");
//
//        if(existingRecordRouteHeader != null) {
//            SipURI sipURI = (SipURI) existingRecordRouteHeader.getAddress().getURI();
//            String nodeHost = sipURI.getParameter("node_host");
//            String nodePort = sipURI.getParameter("node_port");
//            String nodeVersion = sipURI.getParameter("version");
//            String transport = sipURI.getParameter("transport");
//            if ( nodeHost != null && nodePort != null) {
//                recordRouteURI = recordRouteURI+";transport="+transport+";node_host="+nodeHost+";node_port="+nodePort+";version="+nodeVersion;
//            }
//        }
//
//        Header contactHeader = null;
//        RecordRouteHeader recordRouteHeader = null;
//
//        try {
//            HeaderFactory headerFactory = SipFactory.getInstance().createHeaderFactory(); 
//            AddressFactory addressFactory = SipFactory.getInstance().createAddressFactory();
//            contactHeader = headerFactory.createHeader("Contact", contactURI);
//            Address externalAddress = addressFactory.createAddress(recordRouteURI);
//            recordRouteHeader = headerFactory.createRecordRouteHeader(externalAddress);
//            if (contactHeader != null) {
//                response.removeFirst("Contact");
//                response.addHeader(contactHeader);
//            }
//            if (recordRouteHeader != null) {
//                response.removeHeader("Record-Route");
//                response.addFirst(recordRouteHeader);
//            }
//        } catch (PeerUnavailableException e) {
//            e.printStackTrace();
//        } catch (ParseException e) {
//            e.printStackTrace();
//        } catch (NullPointerException e) {
//            e.printStackTrace();
//        } catch (SipException e) {
//            e.printStackTrace();
//        }
//        if (contactHeader != null)
//            logger.info("Patched the Contact header with : "+contactHeader.toString());
//        if (recordRouteHeader != null)
//            logger.info("Added on top : "+recordRouteHeader.toString());
    }

//    /**
//     * @param request
//     * @return
//     */
//    private boolean securityCheck(Request request) {
//        //        User-Agent: sipcli/v1.8
//        //        User-Agent: friendly-scanner
//        //        To: "sipvicious" <sip:100@1.1.1.1>
//        //        From: "sipvicious" <sip:100@1.1.1.1>;tag=3336353363346565313363340133313330323436343236
//        //        From: "1" <sip:1@87.202.36.237>;tag=3e7a78de
//        Header userAgentHeader = request.getHeader("User-Agent");
//        Header toHeader = request.getHeader("To");
//        Header fromHeader = request.getHeader("From");
//
//        for (String blockedValue: blockedList){
//            if(userAgentHeader != null && userAgentHeader.toString().toLowerCase().contains(blockedValue.toLowerCase())) {
//                return false;
//            } else if (toHeader != null && toHeader.toString().toLowerCase().contains(blockedValue.toLowerCase())) {
//                return false;
//            } else if (fromHeader != null && fromHeader.toString().toLowerCase().contains(blockedValue.toLowerCase())) {
//                return false;
//            }
//        }
//        return true;
//    }

    @Override
    public void proxyMessage(ChannelHandlerContext ctx, MessageEvent e){
        final Channel inboundChannel = e.getChannel();
        HttpRequest originalRequest = (HttpRequest)e.getMessage();

        xstream = new XStream();
        xstream.ignoreUnknownElements();
        xstream.alias("request", VoipInnovationProvisionRequest.class);
        xstream.processAnnotations(VoipInnovationProvisionRequest.class);

        ProvisionProvider.PROVIDER provider = null;
        final String providerClass = originalRequest.headers().get("Provider");
        if (providerClass != null) {
            if (providerClass.equalsIgnoreCase(ProvisionProvider.voipinnovationsClass)) {
                provider = ProvisionProvider.PROVIDER.VOIPINNOVATIONS;
            } else if (providerClass.equalsIgnoreCase(ProvisionProvider.bandiwidthClass)) {
                provider = ProvisionProvider.PROVIDER.BANDWIDTH;
            }
        } else {
            provider = ProvisionProvider.PROVIDER.VOIPINNOVATIONS;
        }

        ProvisionProvider.REQUEST_TYPE requestType = ProvisionProvider.REQUEST_TYPE.valueOf(originalRequest.headers().get("RequestType").toUpperCase());

        if(requestType.equals(ProvisionProvider.REQUEST_TYPE.PING)){
            String body = getContent(originalRequest).replaceFirst("apidata=", "");
            VoipInnovationProvisionRequest provisionRequest = (VoipInnovationProvisionRequest) xstream.fromXML(body);
            pong(ctx, e, originalRequest);
            RestcommInstance restcomm = new RestcommInstance(provisionRequest.getEndpointGroup(), provider, originalRequest.headers().getAll("OutboundIntf"), originalRequest.headers().get("PublicIpAddress"));
            restcommInstanceManager.addRestcommInstance(restcomm);
            logger.info("Received PING request from Restcomm Instance: "+restcomm);
            return;
        }
        //        if (provider != null)
        //            logger.info("New request received. ProvisionProvider: "+provider.name()+" originalRequest.getUri(): "+originalRequest.getUri());
        if(provider != null && provider.equals(ProvisionProvider.PROVIDER.VOIPINNOVATIONS)) {
            String body = getContent(originalRequest).replaceFirst("apidata=", "");
            logger.info("Received VoipInnovation ProvisionRequest: "+body);
            VoipInnovationProvisionRequest viProvisionRequest = (VoipInnovationProvisionRequest) xstream.fromXML(body);
            ProxyRequest proxyRequest = new ProxyRequest(ctx, e, originalRequest, viProvisionRequest);

            //Process a number provision request for VoipInnovation
            VoipInnovationStorage.getStorage().addRequestToMap(viProvisionRequest.getId(), proxyRequest);
            final HttpRequest request = viDispatcher.patchHttpRequest(originalRequest);

            // Start the connection attempt.
            Executor executor = Executors.newCachedThreadPool();
            ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(executor, executor);
            ClientBootstrap cb = new ClientBootstrap(cf);

            SslContext sslCtx = null;
            try {
                sslCtx = JdkSslClientContext.newClientContext();
            } catch (SSLException e1) {
                logger.error("There was a problem to create the SSL Context", e1);
            }

            final SslHandler sslHandler = sslCtx.newHandler();
            if(viUri.startsWith("https")) {
                //First handler in the pipeline should be the SSL handler to decode/encode data
                cb.getPipeline().addLast("ssl", sslHandler);
            }
            //Next handler is the HTTP Codec. This combines both RequestEncoder and ResponseDecoder
            cb.getPipeline().addLast("codec", new HttpClientCodec());

            //        cb.getPipeline().addLast("HttpRequestEncoder", new HttpRequestEncoder());
            //        cb.getPipeline().addLast("HttpRequestDecoder", new HttpRequestDecoder());

            //Decompresses an HttpMessage and an HttpChunk compressed in gzip or deflate encoding
            cb.getPipeline().addLast("inflater", new HttpContentDecompressor());

            //Handles HttpChunks.
            cb.getPipeline().addLast("aggregator", new HttpChunkAggregator(1048576));

            cb.getPipeline().addLast("logger", new LoggingHandler(InternalLogLevel.DEBUG));

            //Telestax Proxy handler
            //        cb.getPipeline().addLast("handler", new VoipInnovationsOutboundHandler(e.getChannel()));
            cb.getPipeline().addLast("handler", new VoipInnovationsOutboundHandler());

            //Connect to VoipInnovation. Important is that VI will accept connections and request from API Users with valid username/viPassword and IP Address
            logger.debug("Will now connect to : "+viUri);
            String host = HostUtil.getInstance().getHost(viUri);
            int port = HostUtil.getInstance().getPort(viUri);
            ChannelFuture f = null;
            f = cb.connect(new InetSocketAddress(host, port));

            f.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        outboundChannel = future.getChannel();
                        outboundChannel.setReadable(true);
                        outboundChannel.setAttachment(inboundChannel);
                        inboundChannel.setReadable(true);

                        if (logger.isDebugEnabled()) {
                            logger.debug("Writing out HttpRequest: "+request);
                            byte[] bodyBytes = new byte[request.getContent().capacity()];
                            request.getContent().getBytes(0, bodyBytes);
                            String body = new String(bodyBytes);
                            try {
                                logger.debug("Request's body: "+URLDecoder.decode(body, "UTF-8"));
                            } catch (UnsupportedEncodingException e) {
                                logger.debug("Exception: "+e);
                            }
                        }
                        //Write the request to the outbound channel (to VI)
                        outboundChannel.write(request);
                    } else {
                        // Close the connection if the connection attempt has failed.
                        inboundChannel.close();
                    }
                }
            });
        } else if (provider != null && provider.equals(ProvisionProvider.PROVIDER.BANDWIDTH)){
            //Process a number provision request for Bandwidth
            String body = getContent(originalRequest);
            if (body != null) {
                try {
                    body = URLDecoder.decode(body, "UTF-8");
                    logger.info("Received Bandwidth ProvisionRequest: "+body);
                } catch (UnsupportedEncodingException exception) {
                    logger.error("There was a problem to decode the Request's content ",exception);
                }
            } else {
                logger.info("Received Bandwidth ProvisionRequest: "+originalRequest.getUri());
            }

            if (requestType.equals(ProvisionProvider.REQUEST_TYPE.GETDIDS)) {
                // REQUEST_TYPE.GETDIDS doesn't have XML body and is handled 
                // at org.mobicents.tools.telestaxproxy.http.balancer.provision.bandwidth.BandwidthMessageProcessor.patchHttpRequest(HttpRequest) 
            } else if (requestType.equals(ProvisionProvider.REQUEST_TYPE.ASSIGNDID)) {
                xstream = new XStream();
                xstream.ignoreUnknownElements();
                BandwidthOrderRequest bwRequest = null;
                xstream.alias("Order", BandwidthOrderRequest.class);
                xstream.processAnnotations(BandwidthOrderRequest.class);
                bwRequest = (BandwidthOrderRequest) xstream.fromXML(body);

                String siteId = originalRequest.headers().get("SiteId");
                if (siteId != null)
                    bwRequest.setSiteId(siteId);

                ProxyRequest proxyRequest = new ProxyRequest(ctx, e, originalRequest, bwRequest);                           
                BandwidthStorage.getStorage().addRequestToMap(((BandwidthOrderRequest)bwRequest).getSiteId(), proxyRequest);
            } else if (requestType.equals(ProvisionProvider.REQUEST_TYPE.RELEASEDID)) {
                xstream = new XStream();
                xstream.ignoreUnknownElements();
                BandwidthReleaseRequest bwRequest = null;
                xstream.alias("DisconnectTelephoneNumberOrder", BandwidthReleaseRequest.class);
                xstream.processAnnotations(BandwidthReleaseRequest.class);
                bwRequest = (BandwidthReleaseRequest) xstream.fromXML(body);

                String siteId = originalRequest.headers().get("SiteId");
                if (siteId != null)
                    bwRequest.setSiteId(siteId);

                ProxyRequest proxyRequest = new ProxyRequest(ctx, e, originalRequest, bwRequest);
                if (((BandwidthReleaseRequest)bwRequest).getSiteId() != null && proxyRequest != null)
                    BandwidthStorage.getStorage().addRequestToMap(((BandwidthReleaseRequest)bwRequest).getSiteId(), proxyRequest);
            }

            try {
                final HttpRequest request =  bwDispatcher.patchHttpRequest(originalRequest);

                // Start the connection attempt.
                Executor executor = Executors.newCachedThreadPool();
                ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(executor, executor);
                ClientBootstrap cb = new ClientBootstrap(cf);

                SslContext sslCtx = null;
                try {
                    sslCtx = JdkSslClientContext.newClientContext();
                } catch (SSLException e1) {
                    logger.error("There was a problem to create the SSL Context", e1);
                }

                final SslHandler sslHandler = sslCtx.newHandler();
                if(viUri.startsWith("https")) {
                    //First handler in the pipeline should be the SSL handler to decode/encode data
                    cb.getPipeline().addLast("ssl", sslHandler);
                }
                //Next handler is the HTTP Codec. This combines both RequestEncoder and ResponseDecoder
                cb.getPipeline().addLast("codec", new HttpClientCodec());

                //        cb.getPipeline().addLast("HttpRequestEncoder", new HttpRequestEncoder());
                //        cb.getPipeline().addLast("HttpRequestDecoder", new HttpRequestDecoder());

                //Decompresses an HttpMessage and an HttpChunk compressed in gzip or deflate encoding
                cb.getPipeline().addLast("inflater", new HttpContentDecompressor());

                //Handles HttpChunks.
                cb.getPipeline().addLast("aggregator", new HttpChunkAggregator(1048576));

                cb.getPipeline().addLast("logger", new LoggingHandler(InternalLogLevel.DEBUG));

                //Telestax Proxy handler
                //        cb.getPipeline().addLast("handler", new VoipInnovationsOutboundHandler(e.getChannel()));
                cb.getPipeline().addLast("handler", new BandwidthOutboundHandler());

                //Connect to VoipInnovation. Important is that VI will accept connections and request from API Users with valid username/viPassword and IP Address
                logger.debug("Will now connect to : "+bwUri);
                String host = HostUtil.getInstance().getHost(bwUri);
                int port = HostUtil.getInstance().getPort(bwUri);
                ChannelFuture f = null;
                f = cb.connect(new InetSocketAddress(host, port));

                f.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture future) {
                        if (future.isSuccess()) {
                            outboundChannel = future.getChannel();
                            outboundChannel.setReadable(true);
                            outboundChannel.setAttachment(inboundChannel);
                            inboundChannel.setReadable(true);

                            if (logger.isDebugEnabled()) {
                                logger.debug("Writing out HttpRequest: "+request);
                                byte[] bodyBytes = new byte[request.getContent().capacity()];
                                request.getContent().getBytes(0, bodyBytes);
                                String body = new String(bodyBytes);
                                try {
                                    logger.debug("Request's body: "+URLDecoder.decode(body, "UTF-8"));
                                } catch (UnsupportedEncodingException e) {
                                    logger.debug("Exception: "+e);
                                }
                            }
                            //Write the request to the outbound channel (to VI)
                            outboundChannel.write(request);
                        } else {
                            // Close the connection if the connection attempt has failed.
                            inboundChannel.close();
                        }
                    }
                });
            } catch (Exception exception) {
                logger.error("Exception occured while trying to proxy message: "+originalRequest);
            }
        }
    }

    private void pong(ChannelHandlerContext ctx, MessageEvent e, HttpRequest request) {
        Channel ch = e.getChannel();
        if (is100ContinueExpected(request)) {
            Channels.write(ctx, Channels.future(ch), new DefaultHttpResponse(HTTP_1_1, CONTINUE));
        }

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        ChannelFuture f = Channels.future(ch);
        f.addListener(ChannelFutureListener.CLOSE);
        Channels.write(ctx, f, response);
    }

    private String getContent(HttpMessage message){
        String body = null;
        byte[] bodyBytes = new byte[message.getContent().capacity()];
        message.getContent().getBytes(0, bodyBytes);

        if (bodyBytes != null && bodyBytes.length > 0) {
            try {
                body = URLDecoder.decode(new String(bodyBytes), "UTF-8");
            } catch (UnsupportedEncodingException exception) {
                logger.error("There was a problem to decode the Request's content ",exception);
            }
        }

        return body;
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isConnected()) {
            ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private class VoipInnovationsOutboundHandler extends SimpleChannelUpstreamHandler {

        /*
         * The VoipInnovationsOutboundHandler class will process the VoipInnovation response.
         * Currently supports only one inbound channel but this is not correct. 
         * There might be several request from several Restcomm instances, so the inbound channel should be
         * the appropriate according the the Restcomm instance
         */

        VoipInnovationsOutboundHandler() {}

        @Override
        public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) {
            DefaultHttpResponse response = (DefaultHttpResponse) e.getMessage();
            String body = getContent(response);
            Channel inboundChannel = (Channel) e.getChannel().getAttachment();

            HttpResponseStatus status = response.getStatus();
            if (status.getCode() != 200){
                inboundChannel.close();
                logger.error("Error response: "+status.getCode()+" reason: "+status.getReasonPhrase());
                return;
            } else if (body.contains("<type>Error</type>")){
                inboundChannel.close();
                logger.error("Received Error response, body: "+body);
                return;
            }

            xstream = new XStream();
            xstream.ignoreUnknownElements();
            xstream.alias("response", VoipInnovationResponse.class);
            xstream.processAnnotations(VoipInnovationResponse.class);
            VoipInnovationResponse viResponse = (VoipInnovationResponse) xstream.fromXML(body);

            ProxyRequest proxyRequest = VoipInnovationStorage.getStorage().getProxyRequest(viResponse.getId()); 

            if (logger.isDebugEnabled()) {
                logger.debug("******************** VI Response ******************************");
                logger.debug("Received response: "+response);
                logger.debug("Response body: "+body);
                logger.debug("******************** VI Response ******************************");
            }            

            viDispatcher.processHttpResponse(response, proxyRequest);
            inboundChannel.write(response);

            if (!inboundChannel.isWritable()) {
                e.getChannel().setReadable(false);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            e.getCause().printStackTrace();
            closeOnFlush(e.getChannel());
        }
    }

    private class BandwidthOutboundHandler extends SimpleChannelUpstreamHandler {

        /*
         * The Bandwidth class will process the Bandwidth response.
         * Currently supports only one inbound channel but this is not correct. 
         * There might be several request from several Restcomm instances, so the inbound channel should be
         * the appropriate according the the Restcomm instance
         */

        BandwidthOutboundHandler() {}

        @Override
        public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) {
            DefaultHttpResponse response = (DefaultHttpResponse) e.getMessage();
            String body = getContent(response);

            Channel inboundChannel = (Channel) e.getChannel().getAttachment();

            HttpResponseStatus status = response.getStatus();
            if ( (status.getCode() >= 299) ){
                inboundChannel.close();
                logger.error("Error response: "+status.getCode()+" reason: "+status.getReasonPhrase());
                return;
            } else if (body.contains("<type>Error</type>")){
                inboundChannel.close();
                logger.error("Received Error response, body: "+body);
                return;
            }

            xstream = new XStream();
            xstream.ignoreUnknownElements();
            BandwidthResponse bwResponse = null;
            if (body.contains("<SearchResult>")) {
                xstream.alias("SearchResult", BandwidthSearchResponse.class);
                xstream.processAnnotations(BandwidthSearchResponse.class);
                bwResponse = (BandwidthSearchResponse) xstream.fromXML(body);
            } else if (body.contains("<OrderResponse>")) {
                xstream.alias("OrderResponse", BandwidthOrderResponse.class);
                xstream.processAnnotations(BandwidthOrderResponse.class);
                bwResponse = (BandwidthOrderResponse) xstream.fromXML(body);
                //If Order Response is 201 and OrderStatus RECEIVED, then store the DID for this instance
                if (status.getCode()==201 && ((BandwidthOrderResponse)bwResponse).getOrderStatus().equalsIgnoreCase("received")) {
                    ProxyRequest proxyRequest = BandwidthStorage.getStorage().getProxyRequest(((BandwidthOrderResponse)bwResponse).getCustomerOrderId());
                    bwDispatcher.processHttpResponse(bwResponse, proxyRequest, ProvisionProvider.REQUEST_TYPE.ASSIGNDID);
                }
            } else if (body.contains("<DisconnectTelephoneNumberOrderResponse>")) {
                xstream.alias("DisconnectTelephoneNumberOrderResponse", BandwidthReleaseResponse.class);
                xstream.processAnnotations(BandwidthReleaseResponse.class);
                bwResponse = (BandwidthReleaseResponse) xstream.fromXML(body);
                //If Order Response is 201 and OrderStatus RECEIVED, then remove the DID from the db for this instance
                if (status.getCode()==201 && ((BandwidthReleaseResponse)bwResponse).getOrderStatus().equalsIgnoreCase("received")) {
                    bwDispatcher.processHttpResponse(bwResponse, null, ProvisionProvider.REQUEST_TYPE.RELEASEDID);
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug("******************** BW Response ******************************");
                logger.debug("Received response: "+response);
                logger.debug("Response body: "+body);
                logger.debug("******************** BW Response ******************************");
            }            

            inboundChannel.write(response);

            e.getChannel().close();

            if (!inboundChannel.isWritable()) {
                e.getChannel().setReadable(false);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            logger.error("Exception caught: "+e.getCause());
            logger.error("Exception :"+e);
            closeOnFlush(e.getChannel());
        }
    }
}
