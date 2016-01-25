package org.mobicents.tools.http.balancer;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import org.jboss.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */

public class WebsocketModifyServerPipelineFactory {

	/**
	 * Upgrade the Server ChannelPipelineFactory. This method should be called from the HttpResponseHandler.messageReceived(ChannelHandlerContext, MessageEvent)
	 * when the handler detects that the response contains WebSocket header "Sec-WebSocket-Protocol"
	 * 
	 * @param ChannelPipeline p
	 * @param String WebSocket version
	 * 
	 */
	public void upgradeServerPipelineFactory(ChannelPipeline p, String wsVersion) {
		if (p.get(HttpChunkAggregator.class) != null) {
			p.remove(HttpChunkAggregator.class);
		}

		p.get(HttpRequestDecoder.class).replace("wsdecoder",
				new WebSocket13FrameDecoder(true, true, Long.MAX_VALUE));
		p.replace(HttpResponseEncoder.class, "wsencoder", new WebSocket13FrameEncoder(false));		
	}

}
