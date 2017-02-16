/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package org.mobicents.tools.heartbeat.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.mobicents.tools.heartbeat.client.ClientPipelineFactory;
import org.mobicents.tools.heartbeat.interfaces.IListener;
import org.mobicents.tools.heartbeat.interfaces.IServer;
import org.mobicents.tools.heartbeat.interfaces.Protocol;
import org.mobicents.tools.heartbeat.packets.Packet;
import org.mobicents.tools.heartbeat.packets.LBShutdownRequestPacket;
import org.mobicents.tools.heartbeat.server.ServerPipelineFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class Server implements IServer{

	private static final Logger logger = Logger.getLogger(Server.class.getCanonicalName());
	
	private ExecutorService executor;
	private NioServerSocketChannelFactory nioServerSocketChannelFactory;
	private ServerBootstrap serverBootstrap;
	private Channel serverChannel;
	//client part
	private ChannelFuture future;
	private NioClientSocketChannelFactory nioClientSocketChannelFactory;
	private ClientBootstrap clientBootstrap;
	
	private String lbAddress;
	private int lbPort;
	private Packet packet;
	
	IListener serverListener;
	
	public Server(IListener serverListener, String lbAddress, int httpPort)
	{
		this.serverListener = serverListener;
		this.lbAddress = lbAddress;
		this.lbPort = httpPort;
	}
	
	@Override
	public void start() 
	{
		executor = Executors.newCachedThreadPool();
		nioServerSocketChannelFactory = new NioServerSocketChannelFactory(executor,	executor);		
		serverBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		serverBootstrap.setPipelineFactory(new ServerPipelineFactory(serverListener));
		serverChannel = serverBootstrap.bind(new InetSocketAddress(lbAddress,lbPort));
		logger.info("Heartbeat service started on " +lbAddress+":"+ lbPort+" (Load balancer's side)");
	}

	@Override
	public void sendPacket(String command, String host,int port) {
		this.nioClientSocketChannelFactory = new NioClientSocketChannelFactory(executor, executor);
		this.clientBootstrap = new ClientBootstrap(nioClientSocketChannelFactory);
		this.clientBootstrap.setPipelineFactory(new ClientPipelineFactory(serverListener));
		future = clientBootstrap.connect(new InetSocketAddress(host, port));
		
		switch(command)
		{
			case Protocol.STOP:
				packet = new LBShutdownRequestPacket(lbAddress, lbPort);
				break;
		}
		future.awaitUninterruptibly();
		future.getChannel().write(createRequest(command));
	}

	@Override
	public void stop() 
	{
		if(serverChannel!=null)
		{
			serverChannel.unbind();
			serverChannel.close();
			serverChannel.getCloseFuture().awaitUninterruptibly();
		}
		if(executor!=null)
			executor.shutdownNow();
	}
	
	private String getStringFromJson(String command)
	{
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.setPrettyPrinting().create();
		JsonElement je = gson.toJsonTree(packet);
		JsonObject jo = new JsonObject();
		jo.add(command, je);
		String output=jo.toString();
		logger.debug("Server is sending request : "+ output);
		return output;
	}
	private HttpRequest createRequest(String command)
	{
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
    	ChannelBuffer buf = ChannelBuffers.copiedBuffer(getStringFromJson(command), Charset.forName("UTF-8"));
    	request.setHeader(HttpHeaders.Names.CONTENT_TYPE, APPLICATION_JSON);
    	request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buf.readableBytes());
    	request.setContent(buf);
    	return request;
	}

}
