package org.mobicents.tools.http.balancer;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocket00FrameDecoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocket00FrameEncoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocket07FrameDecoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocket07FrameEncoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocket08FrameDecoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */

public class WebsocketModifyClientPipelineFactory {

	public void upgradeClientPipelineFactoryPhase1(ChannelPipeline p, String version){

		if (p.get(HttpChunkAggregator.class) != null) {
			p.remove(HttpChunkAggregator.class);
		}
		
		//Add the proper WebsocketFrameDecoder according to the version of the SEC_WEBSOCKET_VERSION header
		if (version != null) {
			if (version.equals(WebSocketVersion.V13.toHttpHeaderValue())) {
				// Version 13 of the wire protocol - RFC 6455 (version 17 of the draft hybi specification).
				p.replace(HttpRequestEncoder.class, "ws-encoder", new WebSocket13FrameEncoder(true));
			} else if (version.equals(WebSocketVersion.V08.toHttpHeaderValue())) {
				// Version 8 of the wire protocol - version 10 of the draft hybi specification.
				p.replace(HttpRequestEncoder.class, "ws-encoder", new WebSocket08FrameEncoder(true));
			} else if (version.equals(WebSocketVersion.V07.toHttpHeaderValue())) {
				// Version 8 of the wire protocol - version 07 of the draft hybi specification.
				p.replace(HttpRequestEncoder.class, "ws-encoder", new WebSocket07FrameEncoder(true));
			} else {
				return;
			}
		} else {
			// Assume version 00 where version header was not specified
			p.replace(HttpRequestEncoder.class, "ws-encoder", new WebSocket00FrameEncoder());
		}
	}

	public void upgradeClientPipelineFactoryPhase2(ChannelPipeline p, String version){
		if (p.get(HttpResponseDecoder.class)!=null){
			p.remove(HttpResponseDecoder.class);	
		}
		if (p.get(HttpChunkAggregator.class) != null) {
			p.remove(HttpChunkAggregator.class);
		}

		//Add the proper WebsocketFrameDecoder according to the version of the SEC_WEBSOCKET_VERSION header
		if (version != null) {
			if (version.equals(WebSocketVersion.V13.toHttpHeaderValue())) {
				// Version 13 of the wire protocol - RFC 6455 (version 17 of the draft hybi specification).
				if (p.get(WebSocket13FrameDecoder.class)==null){
					p.addAfter("ws-encoder", "ws-decoder", new WebSocket13FrameDecoder(false, true));
				}
			} else if (version.equals(WebSocketVersion.V08.toHttpHeaderValue())) {
				// Version 8 of the wire protocol - version 10 of the draft hybi specification.
				if (p.get(WebSocket08FrameDecoder.class)==null){
					p.addAfter("ws-encoder", "ws-decoder", new WebSocket08FrameDecoder(false, true));
				}
			} else if (version.equals(WebSocketVersion.V07.toHttpHeaderValue())) {
				// Version 8 of the wire protocol - version 07 of the draft hybi specification.
				if (p.get(WebSocket07FrameDecoder.class)==null){
					p.addAfter("ws-encoder", "ws-decoder", new WebSocket07FrameDecoder(false, true, Long.MAX_VALUE));
				}
			} else {
				return;
			}
		} else {
			// Assume version 00 where version header was not specified
			if (p.get(WebSocket00FrameDecoder.class)==null){
				p.addAfter("ws-encoder", "ws-decoder", new WebSocket00FrameDecoder());
			}
		}
	}

}
