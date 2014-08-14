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

import gov.nist.javax.sip.header.SIPHeader;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;
import javax.sip.address.SipURI;
import javax.sip.message.Request;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
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
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.VoipInnovationDispatcher;
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
    private VoipInnovationDispatcher dispatcher;
    private volatile Channel outboundChannel;
    private XStream xstream;
    private DaoManager daoManager;
    private RestcommInstanceDaoManager restcommInstanceManager;
    private PhoneNumberDaoManager phoneNumberManager;
    
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
        
        dispatcher = new VoipInnovationDispatcher(login,password,endpoint,uri,restcommInstanceManager, phoneNumberManager);
        
        super.init();
    }

    @Override
    public SIPNode processExternalRequest(Request request) {
        String callId = ((SIPHeader) request.getHeader(headerName)).getValue();
        if (!callIdMap.contains(callId)) {
            logger.info("Telestax-Proxy: Got new Request: "+request.getRequestURI().toString()+" will check which node to dispatch");
            SIPNode node = null;
            String did = ((SipURI)request.getRequestURI()).getUser();
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
            } else {
                logger.info("Did :"+did+" couldn't matched to a restcomm instance. Going to super for node selection");
            }
            if (node != null) {
                logger.info("Node found for incoming request: "+request.getRequestURI()+" Will route to node: "+node);
                super.callIdMap.put(callId, node);
                return node;
            } else {
                logger.info("Telestax-Proxy: Node is null, going to super for node selection");
                return super.processExternalRequest(request);
            }
        } else {
            logger.info("Telestax-Proxy: Calld-id is already known, going to super for node selection");
            return super.processExternalRequest(request);
        }
    }


    //    @Override
    //    public void processInternalRequest(Request request) {
    //        super.processInternalRequest(request);
    //    }
    //
    //    @Override
    //    public void processExternalResponse(Response response) {
    //        super.processExternalResponse(response);
    //    }
    //
    //    @Override
    //    public void processInternalResponse(Response response) {
    //        super.processInternalResponse(response);
    //    }
    //
    //    @Override
    //    public SIPNode processHttpRequest(HttpRequest request) {
    //        return super.processHttpRequest(request);
    //    }

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

        ProxyRequest proxyRequest = new ProxyRequest(ctx, e, originalRequest, viRequest);
        VoipInnovationStorage.getStorage().addRequestToMap(viRequest.getId(), proxyRequest);

        final HttpRequest request = dispatcher.patchHttpRequest(originalRequest);

        // Start the connection attempt.
        Executor executor = Executors.newCachedThreadPool();
        ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(executor, executor);
        ClientBootstrap cb = new ClientBootstrap(cf);

        SslContext sslCtx = null;
        try {
            sslCtx = JdkSslClientContext.newClientContext();// SslContext.newClientContext();
        } catch (SSLException e1) {
            logger.error("There was a problem to create the SSL Context", e1);
        }

        final SslHandler sslHandler = sslCtx.newHandler();

        //First handler in the pipeline should be the SSL handler to decode/encode data
        cb.getPipeline().addLast("ssl", sslHandler);
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
        ChannelFuture f = null;
        f = cb.connect(new InetSocketAddress("backoffice.voipinnovations.com", 443));

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

    //    private void proceedToWriteMsg(ChannelFuture future, final Channel inboundChannel, final HttpRequest httpRequest){
    //        future.addListener(new ChannelFutureListener() {
    //            @Override
    //            public void operationComplete(ChannelFuture future) throws Exception {
    //                if(future.isSuccess()) {
    //                    SslHandler sslHandler = (SslHandler) future.getChannel().getPipeline().get("ssl");
    //                    logger.info("SslHanler Future is success.");
    //                    // Connection attempt succeeded:
    //                    // Begin to accept incoming traffic.
    //                    //                    inboundChannel.setReadable(true);
    //                    logger.info("Writing out HttpRequest: "+httpRequest);
    //                    byte[] bodyBytes = new byte[httpRequest.getContent().capacity()];
    //                    httpRequest.getContent().getBytes(0, bodyBytes);
    //                    String body = new String(bodyBytes);
    //                    try {
    //                        logger.info("Request's body: "+URLDecoder.decode(body, "UTF-8"));
    //                    } catch (UnsupportedEncodingException e) {
    //                        // TODO Auto-generated catch block
    //                        e.printStackTrace();
    //                    }
    //                    outboundChannel.write(httpRequest);
    //                } else if (future.isCancelled()){
    //                    logger.info("SslHandler future is canceled");
    //                } else if (future.isDone()) {
    //                    logger.info("SslHandler future is done");
    //                }
    //            }
    //        });
    //    }



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

        //        @Override
        //        public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) {
        //            // If outboundChannel is not saturated anymore, continue accepting
        //            // the incoming traffic from the inboundChannel.
        //            //            synchronized (trafficLock) {
        //            if (e.getChannel().isWritable()) {
        //                inboundChannel.setReadable(true);
        //                //                }
        //            }
        //        }
        //
        //        @Override
        //        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
        //            closeOnFlush(inboundChannel);
        //        }

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
