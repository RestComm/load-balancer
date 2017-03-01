package org.mobicents.tools.sip.balancer.operation;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.mobicents.tools.heartbeat.interfaces.IClientListener;

import com.google.gson.JsonObject;

public class TestHA implements IClientListener{

	@Override
	public void responseReceived(JsonObject json) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stopRequestReceived(MessageEvent e,JsonObject json) 
	{
		writeResponse(e, HttpResponseStatus.OK, Protocol.SHUTDOWN);
	}
	
	private void writeResponse(MessageEvent e, HttpResponseStatus status, String command) 
    {
		JsonObject jo = new JsonObject();
		jo.addProperty(command, Protocol.OK);
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(jo.toString(), Charset.forName("UTF-8"));
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, APPLICATION_JSON);
        response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buf.readableBytes());
        response.setContent(buf);
        ChannelFuture future = e.getChannel().write(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

}
