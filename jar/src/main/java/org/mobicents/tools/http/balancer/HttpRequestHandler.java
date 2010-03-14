/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.mobicents.tools.http.balancer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.Enumeration;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.mobicents.tools.sip.balancer.BalancerContext;
import org.mobicents.tools.sip.balancer.SIPNode;

/**
 * @author Vladimir Ralev (vladimir.ralev@jboss.org)
 * 
 */
@ChannelPipelineCoverage("one")
public class HttpRequestHandler extends SimpleChannelUpstreamHandler {
	
	private static final Logger logger = Logger.getLogger(HttpRequestHandler.class.getCanonicalName());
	
    private volatile HttpRequest request;
    private volatile boolean readingChunks;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        if (!readingChunks) {
        	request = (HttpRequest) e.getMessage();
        	
        	if(logger.isLoggable(Level.FINE)) {
        		logger.fine("Request URI accessed: " + request.getUri() + " channel " + e.getChannel());
        	}
        	
        	Channel associatedChannel = HttpChannelAssocialtions.channels.get(e.getChannel());
        	
        	SIPNode node = null;
        	try {
        		node = BalancerContext.balancerContext.balancerAlgorithm.processHttpRequest(request);
        	} catch (Exception ex) {
        		StringWriter sw = new StringWriter();
        		ex.printStackTrace(new PrintWriter(sw));
        		logger.log(Level.WARNING, "Problem in balancer algorithm", ex);
        		
        		writeResponse(e, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Load Balancer Error: Exception in the balancer algorithm:\n" + 
        				sw.toString()
        		);
        		return;
        	}
        	
        	
        	if(node == null) {
        		if(logger.isLoggable(Level.INFO)) {
            		logger.log(Level.INFO, "Service unavailable. No server is available.");
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
        		ChannelFuture future = HttpChannelAssocialtions.inboundBootstrap.connect(new InetSocketAddress(node.getIp(), (Integer)node.getProperties().get("httpPort")));

        		future.addListener(new ChannelFutureListener() {

        			public void operationComplete(ChannelFuture arg0) throws Exception {
        				Channel channel = arg0.getChannel();
        				HttpChannelAssocialtions.channels.put(e.getChannel(), channel);
        				HttpChannelAssocialtions.channels.put(channel, e.getChannel());

        				if (request.isChunked()) {
        					readingChunks = true;
        				}
        				channel.write(request);

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
            HttpChannelAssocialtions.channels.get(e.getChannel()).write(chunk);
        }
    }
    
    private void closeChannelPair(Channel channel) {
		Channel associatedChannel = HttpChannelAssocialtions.channels.get(channel);
		if(associatedChannel != null) {
			try {
				HttpChannelAssocialtions.channels.remove(associatedChannel);
				if(!associatedChannel.isConnected()) {
					associatedChannel.disconnect();
					associatedChannel.close();
				}
				associatedChannel = null;
			} catch (Exception e) {

			}
		}
		HttpChannelAssocialtions.channels.remove(channel);
		//logger.info("Channel closed. Channels remaining: " + HttpChannelAssocialtions.channels.size());
		if(logger.isLoggable(Level.FINE)) {
			try {
			logger.fine("Channel closed " + HttpChannelAssocialtions.channels.size() + " " + channel);
			Enumeration<Channel> c = HttpChannelAssocialtions.channels.keys();
			while(c.hasMoreElements()) {
				logger.fine(c.nextElement().toString());
			}
			} catch (Exception e) {
				logger.log(Level.FINE, "error", e);
			}
		}
    }

    private void writeResponse(MessageEvent e, HttpResponseStatus status, String responseString) {
    	// Convert the response content to a ChannelBuffer.
    	ChannelBuffer buf = ChannelBuffers.copiedBuffer(responseString, "UTF-8");

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

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        logger.log(Level.SEVERE, "Error", e.getCause());
        e.getChannel().close();
    }
}
