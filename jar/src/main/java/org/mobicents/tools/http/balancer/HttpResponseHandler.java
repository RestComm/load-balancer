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

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpChunkedInput;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.infinispan.commons.equivalence.EquivalentHashMap.EntrySet;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.InvocationContext;
import org.mobicents.tools.sip.balancer.KeyHttp;
import org.mobicents.tools.sip.balancer.KeySip;

import org.mobicents.tools.heartbeat.api.Node;

/**
 * @author Vladimir Ralev (vladimir.ralev@jboss.org)
 *
 */
//https://issues.jboss.org/browse/NETTY-283 - ChannelPipelineCoverage deprecated
//@ChannelPipelineCoverage("one")
public class HttpResponseHandler extends SimpleChannelUpstreamHandler {
	private static final Logger logger = Logger.getLogger(HttpResponseHandler.class.getCanonicalName());
	private volatile boolean readingChunks;
	private volatile HttpResponse response;
	private volatile String wsVersion;
	private volatile WebsocketModifyServerPipelineFactory websocketModifyServerPipelineFactory;
	private BalancerRunner balancerRunner;
	
	public HttpResponseHandler (BalancerRunner balancerRunner)
	{
		this.balancerRunner = balancerRunner;
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {		
		Object msg = e.getMessage();
		if ((msg instanceof HttpResponse) || (msg instanceof DefaultHttpChunk) || (msg instanceof HttpChunkTrailer)) {
			handleHttpResponse(ctx, e);
		} else if (msg instanceof WebSocketFrame) {
			handleWebSocketFrame(ctx, e);
		}
	}
	
	private void handleWebSocketFrame(ChannelHandlerContext ctx, MessageEvent e) {
		Object msg = e.getMessage();
		final String response = ((TextWebSocketFrame) msg).getText();
		if(logger.isDebugEnabled()) {
			logger.debug(String.format("Channel %s received WebSocket response %s", ctx.getChannel().getId(), response));
		}
				
		Channel channel = HttpChannelAssociations.channels.get(new AdvancedChannel(e.getChannel())).getChannel();
//		channel.getPipeline().remove(HttpResponseDecoder.class);
		
		if(channel != null) {
			channel.write(new TextWebSocketFrame(response));
		} 
	}

	private void handleHttpResponse(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
		if(e.getMessage() instanceof HttpChunkTrailer)
		{
			HttpChunkTrailer chunk = (HttpChunkTrailer) e.getMessage();			
			balancerRunner.balancerContext.httpBytesToClient.addAndGet(chunk.getContent().capacity());
			if (chunk.isLast()) 
				readingChunks = false;
			
			AdvancedChannel ac = HttpChannelAssociations.channels.get(new AdvancedChannel(e.getChannel()));
			Channel channel = null;
			if(ac!=null)
				channel = ac.getChannel();
			if(channel != null) 
			{
				if(logger.isDebugEnabled())
					logger.debug("Send chunked response from : " + e.getChannel().getRemoteAddress() + " to : " + channel.getRemoteAddress() + " capacity : " + chunk.getContent().capacity());
				channel.write(chunk);				
			}
		}
		else if(!readingChunks || !(e.getMessage() instanceof DefaultHttpChunk))
		{
			response = (HttpResponse) e.getMessage();
			int stsusCode = response.getStatus().getCode(); 
			if(stsusCode > 399 && stsusCode < 600)
			{
				AdvancedChannel ac = HttpChannelAssociations.channels.get(new AdvancedChannel(e.getChannel()));
				if(ac!=null && ac.isCheckNeed())
				{
					
					InetSocketAddress address = (InetSocketAddress)e.getChannel().getRemoteAddress();
					InvocationContext invocationContext = balancerRunner.getLatestInvocationContext();
					KeySip keySip = new KeySip(address.getHostString(), address.getPort(), false);
					Node currNode = invocationContext.sipNodeMap(false).get(keySip);
					if( currNode!= null) 
					{
						currNode.setBad(true);
						logger.error("Error code [" + stsusCode + "] detected in HTTP response. From  node : " + currNode +". This node will marked as bad.");
						String currInstanceId = (String) currNode.getProperties().get("Restcomm-Instance-Id");
						if(currInstanceId!=null)
							logger.warn("Node : " + invocationContext.httpNodeMap.remove(new KeyHttp(currInstanceId)) + " from httpNodeMap");
						//invocationContext.badSipNodeMap(false).put(keySip, currNode);
						invocationContext.balancerAlgorithm.nodeRemoved(currNode);
					}
					//TODO CHECK REQUEST AND REMOVE NODE
					
				}


			}
			updateStatistic(response);
			balancerRunner.balancerContext.httpBytesToClient.addAndGet(response.getContent().capacity());

			if(response.isChunked()){
				readingChunks = true;
			}

			AdvancedChannel ac = HttpChannelAssociations.channels.get(new AdvancedChannel(e.getChannel()));
			Channel channel = null;
			if(ac!=null)
				channel = ac.getChannel();
			if(channel != null) {
				if(logger.isDebugEnabled())
					logger.debug("Send response from : " + e.getChannel().getRemoteAddress() + " to : " + channel.getRemoteAddress() + " capacity : " + response.getContent().capacity());
				channel.write(response);
			}

			Set<String> headers = response.getHeaderNames();
			if(headers.contains("Sec-WebSocket-Protocol")) {
				if(response.getHeader("Sec-WebSocket-Protocol").equalsIgnoreCase("sip")){
					if(logger.isDebugEnabled()) {
						logger.debug("WebSocket response");
					}
					wsVersion = response.getHeader(Names.SEC_WEBSOCKET_VERSION);

					//Modify the Server pipeline
					ChannelPipeline p = channel.getPipeline();
					websocketModifyServerPipelineFactory = new WebsocketModifyServerPipelineFactory();
					websocketModifyServerPipelineFactory.upgradeServerPipelineFactory(p, wsVersion);
					
//					ChannelPipeline p = channel.getPipeline();
//					if (p.get(HttpChunkAggregator.class) != null) {
//						p.remove(HttpChunkAggregator.class);
//					}
//
//					p.get(HttpRequestDecoder.class).replace("wsdecoder",
//							new WebSocket13FrameDecoder(true, true, Long.MAX_VALUE));
//					p.replace(HttpResponseEncoder.class, "wsencoder", new WebSocket13FrameEncoder(false));

				}
			}
		} 
		else
		{			
			
			HttpChunk chunk = (HttpChunk) e.getMessage();			
			balancerRunner.balancerContext.httpBytesToClient.addAndGet(chunk.getContent().capacity());
			if (chunk.isLast()) 
				readingChunks = false;
			
			AdvancedChannel ac = HttpChannelAssociations.channels.get(new AdvancedChannel(e.getChannel()));
			Channel channel = null;
			if(ac!=null)
				channel = ac.getChannel();
			if(channel != null) 
			{
				if(logger.isDebugEnabled())
					logger.debug("Send chunked response from : " + e.getChannel().getRemoteAddress() + " to : " + channel.getRemoteAddress() + " capacity : " + chunk.getContent().capacity());
				channel.write(chunk);				
			}
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		logger.error("Error", e.getCause());
	}
	
	private void updateStatistic(HttpResponse response)
	{
		int statusCode = response.getStatus().getCode();
		 int statusCodeDiv = statusCode / 100;
        switch (statusCodeDiv) {
            case 1:
                balancerRunner.balancerContext.httpResponseProcessedByCode.get("1XX").incrementAndGet();
                break;
            case 2:
                balancerRunner.balancerContext.httpResponseProcessedByCode.get("2XX").incrementAndGet();
                break;
            case 3:
                balancerRunner.balancerContext.httpResponseProcessedByCode.get("3XX").incrementAndGet();
                break;
            case 4:
                balancerRunner.balancerContext.httpResponseProcessedByCode.get("4XX").incrementAndGet();
                break;
            case 5:
                balancerRunner.balancerContext.httpResponseProcessedByCode.get("5XX").incrementAndGet();
                break;
        }
	}

}
