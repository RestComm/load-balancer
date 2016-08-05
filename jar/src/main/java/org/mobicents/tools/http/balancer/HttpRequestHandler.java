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

package org.mobicents.tools.http.balancer;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.InvocationContext;
import org.mobicents.tools.sip.balancer.SIPNode;
import org.mobicents.tools.sip.balancer.StatisticObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * @author Vladimir Ralev (vladimir.ralev@jboss.org)
 * 
 */
//https://issues.jboss.org/browse/NETTY-283 - ChannelPipelineCoverage deprecated
//@ChannelPipelineCoverage("one")
public class HttpRequestHandler extends SimpleChannelUpstreamHandler {

    private static final Logger logger = Logger.getLogger(HttpRequestHandler.class.getCanonicalName());

    private volatile HttpRequest request;
    private volatile boolean readingChunks;
    private volatile boolean wsrequest;
    private String wsVersion;
    private WebsocketModifyClientPipelineFactory websocketServerPipelineFactory;
    private volatile SIPNode node;
    private boolean isSecured;

    private BalancerRunner balancerRunner;

    public HttpRequestHandler(BalancerRunner balancerRunner, boolean isSecured) {
        this.balancerRunner = balancerRunner;
        this.isSecured = isSecured;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof HttpRequest) {
            request = (HttpRequest)e.getMessage();
            if(balancerRunner.getProperty("statisticPort")!=null)
            {
				try 
				{
					URI uri = new URI(request.getUri());
					if (((InetSocketAddress) ctx.getChannel().getLocalAddress()).getPort() == Integer.parseInt(balancerRunner.getProperty("statisticPort"))) {
						if (uri.getPath().equalsIgnoreCase("/lbstat")) {
							writeStatisticResponse(e);
							return;
						} 
						else 
						{
							writeResponse(e,
									HttpResponseStatus.INTERNAL_SERVER_ERROR,
									"Server error");
							return;
						}
					}
				} catch (URISyntaxException e1) 
				{
					//ignore exception
				}
			}

            balancerRunner.balancerContext.httpRequests.incrementAndGet();
            balancerRunner.balancerContext.httpRequestsProcessedByMethod.get(request.getMethod().getName()).incrementAndGet();
            balancerRunner.balancerContext.httpBytesToServer.addAndGet(request.getContent().capacity());
            String telestaxHeader = request.headers().get("TelestaxProxy"); 
            if (telestaxHeader != null && telestaxHeader.equalsIgnoreCase("true")) {
                balancerRunner.getLatestInvocationContext().balancerAlgorithm.proxyMessage(ctx, e);
            } else {
                handleHttpRequest(ctx, e);
            }
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, e);
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, final MessageEvent e) {
        Object msg = e.getMessage();
        final String request = ((TextWebSocketFrame) msg).getText();
        if(logger.isDebugEnabled()) {
            logger.debug(String.format("Channel %s received WebSocket request %s", ctx.getChannel().getId(), request));
        }
        //Modify the Client Pipeline - Phase 2
        Channel channel = HttpChannelAssociations.channels.get(e.getChannel());
        ChannelPipeline p = channel.getPipeline();
        websocketServerPipelineFactory.upgradeClientPipelineFactoryPhase2(p, wsVersion);

        if(channel != null) {
            channel.write(new TextWebSocketFrame(request));
        } 


    }

    private void handleHttpRequest(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        if (!readingChunks) {
            request = (HttpRequest) e.getMessage();

            if(logger.isDebugEnabled()) {
                logger.debug("Request URI accessed: " + request.getUri() + " channel " + e.getChannel());
            }

            Channel associatedChannel = HttpChannelAssociations.channels.get(e.getChannel());

            InvocationContext invocationContext = balancerRunner.getLatestInvocationContext();

            //			SIPNode node = null;
            try {
                //TODO: If WebSocket request, choose a NODE that is able to handle WebSocket requests (has a websocket connector)
                node = invocationContext.balancerAlgorithm.processHttpRequest(request);
            } catch (Exception ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                logger.warn("Problem in balancer algorithm", ex);

                writeResponse(e, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Load Balancer Error: Exception in the balancer algorithm:\n" + 
                        sw.toString()
                        );
                return;
            }


            if(node == null) {
                if(logger.isInfoEnabled()) {
                    logger.info("Service unavailable. No server is available.");
                }
                writeResponse(e, HttpResponseStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable");
                return;
            }

            if(associatedChannel != null && associatedChannel.isConnected()) {
                associatedChannel.write(request);
            } else {

                e.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture arg0) throws Exception {
                        closeChannelPair(arg0.getChannel());
                    }
                });

                // Start the connection attempt.
                ChannelFuture future = null;
                Set<String> headers = request.getHeaderNames();
                if(headers.contains("Sec-WebSocket-Protocol")) {
                    if(request.getHeader("Sec-WebSocket-Protocol").equalsIgnoreCase("sip")){
                        if(logger.isDebugEnabled()) {
                            logger.debug("New SIP over WebSocket request. WebSocket uri: "+request.getUri());
                            logger.debug("Dispatching WebSocket request to node: "+ node.getIp()+" port: "+ node.getProperties().get("wsPort"));
                        }
                        wsrequest = true;
                        wsVersion = request.getHeader(Names.SEC_WEBSOCKET_VERSION);
                        websocketServerPipelineFactory = new WebsocketModifyClientPipelineFactory();
                        future = HttpChannelAssociations.inboundBootstrap.connect(new InetSocketAddress(node.getIp(), (Integer)node.getProperties().get("wsPort")));

                    }
                } else {
                	if(!isSecured)
                	{
                    if(logger.isDebugEnabled()) {
                        logger.debug("Dispatching HTTP request to node: "+ node.getIp()+" port: "+ node.getProperties().get("httpPort"));
                    }
                    future = HttpChannelAssociations.inboundBootstrap.connect(new InetSocketAddress(node.getIp(), (Integer) node.getProperties().get("httpPort")));
                	}
                	else
                	{
                		if(logger.isDebugEnabled()) {
                            logger.debug("Dispatching HTTPS request to node: "+ node.getIp()+" port: "+ node.getProperties().get("sslPort"));
                        }
                        future = HttpChannelAssociations.inboundSecureBootstrap.connect(new InetSocketAddress(node.getIp(), (Integer) node.getProperties().get("sslPort")));
                	}
                }

                future.addListener(new ChannelFutureListener() {

                    public void operationComplete(ChannelFuture arg0) throws Exception {
                        Channel channel = arg0.getChannel();
                        HttpChannelAssociations.channels.put(e.getChannel(), channel);
                        HttpChannelAssociations.channels.put(channel, e.getChannel());

                        if (request.isChunked()) {
                            readingChunks = true;
                        }
                        channel.write(request);

                        if(wsrequest){
                            if(logger.isDebugEnabled()) {
                                logger.debug("This is a websocket request, changing the pipeline");
                            }
                            //Modify the Client Pipeline - Phase 1
                            ChannelPipeline p = channel.getPipeline();
                            websocketServerPipelineFactory.upgradeClientPipelineFactoryPhase1(p, wsVersion);
                        }

                        channel.getCloseFuture().addListener(new ChannelFutureListener() {
                            public void operationComplete(ChannelFuture arg0) throws Exception {
                                closeChannelPair(arg0.getChannel());
                            }
                        });
                    }
                });
            }
        } else {
            HttpChunk chunk = (HttpChunk) e.getMessage();
            if (chunk.isLast()) {
                readingChunks = false;
            }
            HttpChannelAssociations.channels.get(e.getChannel()).write(chunk);
        }
    }

    private void closeChannelPair(Channel channel) {
        Channel associatedChannel = HttpChannelAssociations.channels.get(channel);
        if(associatedChannel != null) {
            try {
                HttpChannelAssociations.channels.remove(associatedChannel);
                if(!associatedChannel.isConnected()) {
                    associatedChannel.disconnect();
                    associatedChannel.close();
                    associatedChannel.getCloseFuture().awaitUninterruptibly();
                }
                associatedChannel = null;
            } catch (Exception e) {

            }
        }
        HttpChannelAssociations.channels.remove(channel);
        //logger.info("Channel closed. Channels remaining: " + HttpChannelAssocialtions.channels.size());
        if(logger.isDebugEnabled()) {
            try {
                logger.debug("Channel closed " + HttpChannelAssociations.channels.size() + " " + channel);
                Enumeration<Channel> c = HttpChannelAssociations.channels.keys();
                while(c.hasMoreElements()) {
                    logger.debug(c.nextElement().toString());
                }
            } catch (Exception e) {
                logger.debug("error", e);
            }
        }
    }

    private void writeResponse(MessageEvent e, HttpResponseStatus status, String responseString) {
        // Convert the response content to a ChannelBuffer.
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(responseString, Charset.forName("UTF-8"));

        // Decide whether to close the connection or not.
        boolean close =
                HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.CONNECTION)) ||
                request.getProtocolVersion().equals(HttpVersion.HTTP_1_0) &&
                !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.CONNECTION));

        // Build the response object.
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        response.setContent(buf);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(buf.readableBytes()));
        }

        String cookieString = request.getHeader(HttpHeaders.Names.COOKIE);
        if (cookieString != null) {
            CookieDecoder cookieDecoder = new CookieDecoder();
            Set<Cookie> cookies = cookieDecoder.decode(cookieString);
            if(!cookies.isEmpty()) {
                // Reset the cookies if necessary.
                CookieEncoder cookieEncoder = new CookieEncoder(true);
                for (Cookie cookie : cookies) {
                    cookieEncoder.addCookie(cookie);
                }
                response.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder.encode());
            }
        }

        // Write the response.
        ChannelFuture future = e.getChannel().write(response);

        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    //	@Override
    //	public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent event) throws Exception {
    //		//		logger.info("new handleUpstream reveiced on channel: ["+ctx.getChannel().toString() +"] event message: ["+event.toString()+"]");
    //		super.handleUpstream(ctx, event);
    //	}

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        logger.error("Error", e.getCause());
        e.getChannel().close();
    }
    private void writeStatisticResponse(MessageEvent e)
    {
     	GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.setPrettyPrinting().create();
		JsonElement je = gson.toJsonTree(new StatisticObject(balancerRunner));
		JsonObject jo = new JsonObject();
		jo.add("Metrics", je);
		String output=jo.toString();
    	HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
    	response.setHeader(HttpHeaders.Names.CONTENT_TYPE, APPLICATION_JSON);
    	ChannelBuffer buf = ChannelBuffers.copiedBuffer(output, Charset.forName("UTF-8"));
    	response.setContent(buf);
    	ChannelFuture future = e.getChannel().write(response);
    	future.addListener(ChannelFutureListener.CLOSE);
    }        
}
