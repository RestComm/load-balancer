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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.VoipInnovationMessageProcessor;
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.VoipInnovationStorage;
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.entities.request.ProxyRequest;
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.entities.request.VoipInnovationRequest;
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.entities.responses.VoipInnovationResponse;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.RestcommInstance;

import com.thoughtworks.xstream.XStream;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class TelestaxProxyAlgorithm extends CallIDAffinityBalancerAlgorithm {

    private static Logger logger = Logger.getLogger(TelestaxProxyAlgorithm.class.getCanonicalName());
    private Properties properties;
    private String login, password, endpoint, uri;
    private VoipInnovationMessageProcessor dispatcher;
    private volatile Channel outboundChannel;
    private XStream xstream;
    private DaoManager daoManager;
    private RestcommInstanceDaoManager restcommInstanceManager;
    private PhoneNumberDaoManager phoneNumberManager;
    private ArrayList<String> blockedList;
    private String externalIP;

    @Override
    public void init() {        
        properties = getBalancerContext().properties;
        login = properties.getProperty("vi-login");
        password = properties.getProperty("vi-password");
        endpoint = properties.getProperty("vi-endpoint");
        uri = properties.getProperty("vi-uri");
        String myBatisConf = properties.getProperty("mybatis-config");
        File mybatisConfFile = null;
        if(myBatisConf != null && !myBatisConf.equalsIgnoreCase("")) 
            mybatisConfFile = new File(myBatisConf);

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

        dispatcher = new VoipInnovationMessageProcessor(login,password,endpoint,uri,restcommInstanceManager, phoneNumberManager);

        externalIP = properties.getProperty("external-ip", super.getBalancerContext().host);

        String blockedValues = properties.getProperty("blocked-values", "sipvicious,sipcli,friendly-scanner");
        populateBlockedList(blockedValues);

        super.init();
    }

    /**
     * @param blockedValues The values should be seperated by comma ","
     */
    private void populateBlockedList(String blockedValues) {
        blockedList = new ArrayList<String>(Arrays.asList(blockedValues.split(",")));
    }

    @Override
    public SIPNode processExternalRequest(Request request) {
        if (!securityCheck(request)){
            logger.warn("Request failed at the security check:\n"+request);
            return null;
        }

        String callId = ((SIPHeader) request.getHeader(headerName)).getValue();

        if (!super.callIdMap.containsKey(callId)) {
            logger.info("Telestax-Proxy: Got new Request: "+request.getMethod()+" "+request.getRequestURI().toString()+" will check which node to dispatch");
            SIPNode node = null;
            String did = ((SipURI)request.getRequestURI()).getUser();
            if (did != null) {
                did = did.replaceFirst("\\+1", "");
                did = did.replaceFirst("001", "");
                did = did.replaceFirst("^1", "");

                if (phoneNumberManager.didExists(did)) {
                    RestcommInstance restcommInstance = phoneNumberManager.getInstanceByDid(did);
                    for (SIPNode tempNode: invocationContext.nodes) {
                        try {

                            String transport = ((SipURI)request.getRequestURI()).getTransportParam() == null ? "udp" : ((SipURI)request.getRequestURI()).getTransportParam() ;
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
                logger.info("Telestax-Proxy: Node is null");
                return null;
            }
        } else {
            logger.info("Telestax-Proxy: Calld-id is already known, going to super for node selection");
            return super.processExternalRequest(request);
        }
    }


    @Override
    public void processInternalResponse(Response response) {
        //Need to patch the response so it 
        int port = super.getBalancerContext().externalPort;
        String contactURI = "sip:"+externalIP+":"+port;
        String recordRouteURI = "sip:"+externalIP+":"+port+";lr";
        RecordRouteHeader existingRecordRouteHeader = (RecordRouteHeader) response.getHeader("Record-Route");

        if(existingRecordRouteHeader != null) {
            SipURI sipURI = (SipURI) existingRecordRouteHeader.getAddress().getURI();
            String nodeHost = sipURI.getParameter("node_host");
            String nodePort = sipURI.getParameter("node_port");
            String nodeVersion = sipURI.getParameter("version");
            String transport = sipURI.getParameter("transport");
            if ( nodeHost != null && nodePort != null) {
                recordRouteURI = recordRouteURI+";transport="+transport+";node_host="+nodeHost+";node_port="+nodePort+";version="+nodeVersion;
            }
        }

        Header contactHeader = null;
        RecordRouteHeader recordRouteHeader = null;

        try {
            HeaderFactory headerFactory = SipFactory.getInstance().createHeaderFactory(); 
            AddressFactory addressFactory = SipFactory.getInstance().createAddressFactory();
            contactHeader = headerFactory.createHeader("Contact", contactURI);
            Address externalAddress = addressFactory.createAddress(recordRouteURI);
            recordRouteHeader = headerFactory.createRecordRouteHeader(externalAddress);
            if (contactHeader != null) {
                response.removeFirst("Contact");
                response.addHeader(contactHeader);
            }
            if (recordRouteHeader != null) {
                response.removeHeader("Record-Route");
                response.addFirst(recordRouteHeader);
            }
        } catch (PeerUnavailableException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (SipException e) {
            e.printStackTrace();
        }
        if (contactHeader != null)
            logger.info("Patched the Contact header with : "+contactHeader.toString());
        if (recordRouteHeader != null)
            logger.info("Added on top : "+recordRouteHeader.toString());
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

        for (String blockedValue: blockedList){
            if(userAgentHeader != null && userAgentHeader.toString().toLowerCase().contains(blockedValue)) {
                return false;
            } else if (toHeader != null && toHeader.toString().toLowerCase().contains(blockedValue)) {
                return false;
            } else if (fromHeader != null && fromHeader.toString().toLowerCase().contains(blockedValue)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void proxyMessage(ChannelHandlerContext ctx, MessageEvent e){
        final Channel inboundChannel = e.getChannel();
        HttpRequest originalRequest = (HttpRequest)e.getMessage();

        xstream = new XStream();
        xstream.ignoreUnknownElements();
        xstream.alias("request", VoipInnovationRequest.class);
        xstream.processAnnotations(VoipInnovationRequest.class);
        String body = getContent(originalRequest).replaceFirst("apidata=", "");
        VoipInnovationRequest viRequest = (VoipInnovationRequest) xstream.fromXML(body);

        if(viRequest.getRequestType().equalsIgnoreCase("ping")){
            pong(ctx, e, originalRequest);
            RestcommInstance restcomm = new RestcommInstance(viRequest.getEndpointGroup(), originalRequest.headers().getAll("OutboundIntf"));
            restcommInstanceManager.addRestcommInstance(restcomm);
            logger.info("Received PING request from Restcomm Instance: "+restcomm);
            return;
        }

        ProxyRequest proxyRequest = new ProxyRequest(ctx, e, originalRequest, viRequest);
        VoipInnovationStorage.getStorage().addRequestToMap(viRequest.getId(), proxyRequest);

        final HttpRequest request = dispatcher.patchHttpRequest(originalRequest);

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
        if(uri.startsWith("https")) {
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
        //        cb.getPipeline().addLast("handler", new OutboundHandler(e.getChannel()));
        cb.getPipeline().addLast("handler", new OutboundHandler());

        //Connect to VoipInnovation. Important is that VI will accept connections and request from API Users with valid username/password and IP Address
        logger.debug("Will now connect to : "+uri);
        String host = HostUtil.getInstance().getHost(uri);
        int port = HostUtil.getInstance().getPort(uri);
        ChannelFuture f = null;
        f = cb.connect(new InetSocketAddress(host, port));

        f.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    outboundChannel = future.getChannel();
                    outboundChannel.setReadable(true);
                    inboundChannel.setReadable(true);

                    if (logger.isDebugEnabled()) {
                        logger.debug("Writing out HttpRequest: "+request);
                        byte[] bodyBytes = new byte[request.getContent().capacity()];
                        request.getContent().getBytes(0, bodyBytes);
                        String body = new String(bodyBytes);
                        try {
                            logger.debug("Request's body: "+URLDecoder.decode(body, "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
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

        try {
            body = URLDecoder.decode(new String(bodyBytes), "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            logger.error("There was a problem to decode the Request's content ",exception);
        }

        return body;
    }

    private class OutboundHandler extends SimpleChannelUpstreamHandler {

        /*
         * The OutboundHandler class will process the VoipInnovation response.
         * Currently supports only one inbound channel but this is not correct. 
         * There might be several request from several Restcomm instances, so the inbound channel should be
         * the appropriate according the the Restcomm instance
         */

        OutboundHandler() {}

        @Override
        public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) {
            DefaultHttpResponse response = (DefaultHttpResponse) e.getMessage();
            String body = getContent(response);

            HttpResponseStatus status = response.getStatus();
            if (status.getCode() != 200){
                //TODO: Need to find a way to close inbound channel
                logger.info("Error response: "+status.getCode()+" reason: "+status.getReasonPhrase());
                return;
            } else if (body.contains("<type>Error</type>")){
                //TODO: Need to find a way to close inbound channel
                logger.info("Received Error response, body: "+body);
                return;
            }

            xstream = new XStream();
            xstream.ignoreUnknownElements();
            xstream.alias("response", VoipInnovationResponse.class);
            xstream.processAnnotations(VoipInnovationResponse.class);
            VoipInnovationResponse viResponse = (VoipInnovationResponse) xstream.fromXML(body);

            ProxyRequest proxyRequest = VoipInnovationStorage.getStorage().getProxyRequest(viResponse.getId()); 
            Channel inboundChannel = proxyRequest.getEvent().getChannel();

            if (logger.isDebugEnabled()) {
                logger.debug("******************** VI Response ******************************");
                logger.debug("Received response: "+response);
                logger.debug("Response body: "+body);
                logger.debug("******************** VI Response ******************************");
            }            

            dispatcher.processHttpResponse(response, proxyRequest);
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

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isConnected()) {
            ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
