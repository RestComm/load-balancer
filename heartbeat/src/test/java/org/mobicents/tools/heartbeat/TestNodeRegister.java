package org.mobicents.tools.heartbeat;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.mobicents.tools.heartbeat.interfaces.IServerListener;
import org.mobicents.tools.heartbeat.interfaces.Protocol;
import org.mobicents.tools.heartbeat.packets.HeartbeatResponsePacket;
import org.mobicents.tools.heartbeat.packets.ShutdownResponsePacket;
import org.mobicents.tools.heartbeat.packets.Packet;
import org.mobicents.tools.heartbeat.packets.StartResponsePacket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class TestNodeRegister implements IServerListener {

	private Gson gson = new Gson();
	
	@Override
	public void responseReceived(JsonObject json) 
	{
		//TODO
		
	}

	@Override
	public void startRequestReceived(MessageEvent e, JsonObject json) 
	{
		writeResponse(e, HttpResponseStatus.OK, Protocol.START);
	}

	@Override
	public void heartbeatRequestReceived(MessageEvent e, JsonObject json) 
	{
		writeResponse(e, HttpResponseStatus.OK, Protocol.HEARTBEAT);
	}

	@Override
	public void shutdownRequestReceived(MessageEvent e, JsonObject json) 
	{
		writeResponse(e, HttpResponseStatus.OK, Protocol.SHUTDOWN);
	}
	
	private void writeResponse(MessageEvent e, HttpResponseStatus status, String command) 
    {
		Packet packet = null;
		switch(command)
		{
			case Protocol.HEARTBEAT:
				packet = new HeartbeatResponsePacket(Protocol.OK);
				break;
			case Protocol.START:
				packet = new StartResponsePacket(Protocol.OK);
				break;
			case Protocol.SHUTDOWN:
				packet = new ShutdownResponsePacket(Protocol.OK);
				break;
		}
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(gson.toJson(packet), Charset.forName("UTF-8"));
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, APPLICATION_JSON);
        response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buf.readableBytes());
        response.setContent(buf);
        ChannelFuture future = e.getChannel().write(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

	@Override
	public void stopRequestReceived(MessageEvent e, JsonObject json) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchoverRequestReceived(MessageEvent e, JsonObject asJsonObject) {
		// TODO Auto-generated method stub
		
	}
}
