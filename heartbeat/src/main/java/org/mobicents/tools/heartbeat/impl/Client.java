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

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.mobicents.tools.heartbeat.api.HeartbeatRequestPacket;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.heartbeat.api.NodeShutdownRequestPacket;
import org.mobicents.tools.heartbeat.api.NodeStopRequestPacket;
import org.mobicents.tools.heartbeat.api.Packet;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.mobicents.tools.heartbeat.api.StartRequestPacket;
import org.mobicents.tools.heartbeat.client.ClientPipelineFactory;
import org.mobicents.tools.heartbeat.interfaces.IClient;
import org.mobicents.tools.heartbeat.interfaces.IClientListener;
import org.mobicents.tools.heartbeat.packets.SwitchoverRequestPacket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class Client implements IClient{

	private static final Logger logger = Logger.getLogger(Client.class.getCanonicalName());
	
	private ExecutorService executor;
	private ClientBootstrap clientBootstrap;
	private NioClientSocketChannelFactory nioClientSocketChannelFactory;
	private ChannelFuture future;

	private String lbAddress;
	private int lbPort;
	private Node node;
	
	private Packet packet;
	private IClientListener clientListener;
	private InetSocketAddress isa;
	
	public Client(IClientListener clientListener, String lbAddress, int lbPort, Node node, ExecutorService executor)
	{
		this.clientListener = clientListener;
		this.lbAddress = lbAddress;
		this.lbPort = lbPort;
		this.node = node;
		this.executor = executor;
	}
	
	public void updateNode(Node node)
	{
		this.node=node;
	}
	
	@Override
	public void start()
	{
		this.nioClientSocketChannelFactory = new NioClientSocketChannelFactory(executor, executor);
		this.clientBootstrap = new ClientBootstrap(nioClientSocketChannelFactory);
		this.clientBootstrap.setPipelineFactory(new ClientPipelineFactory(clientListener));
		this.isa = new InetSocketAddress(lbAddress, lbPort);
	}
	
	@Override
	public void switchover(String fromJvmRoute, String toJvmRoute)
	{
		Packet packet = new SwitchoverRequestPacket(fromJvmRoute, toJvmRoute);
		ClientBootstrap clientBootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(executor, executor));
		clientBootstrap.setPipelineFactory(new ClientPipelineFactory(clientListener));
		ChannelFuture future = clientBootstrap.connect(new InetSocketAddress(lbAddress, lbPort));
		future.awaitUninterruptibly();
		future.getChannel().write(createRequest(Protocol.SWITCHOVER,packet));
	}

	@Override
	public synchronized void sendPacket(String command)
	{
		if(clientBootstrap!=null&&!executor.isShutdown())
		{
			
			future = clientBootstrap.connect(isa);
			switch(command)
			{
				case Protocol.START:
					if(packet==null||!(packet instanceof StartRequestPacket))
					{
						node.getProperties().put(Protocol.SESSION_ID, ""+ System.currentTimeMillis());
						packet = new StartRequestPacket(node);
					}
					break;
				case Protocol.HEARTBEAT:
					if(packet!=null&&!(packet instanceof HeartbeatRequestPacket))
						packet = new HeartbeatRequestPacket(node);
					break;
				case Protocol.SHUTDOWN:
					packet = new NodeShutdownRequestPacket(node);
					break;
				case Protocol.STOP:
					packet = new NodeStopRequestPacket(node);
					break;
			}
			future.awaitUninterruptibly();
			future.getChannel().write(createRequest(command, null));
		}
	}

	@Override
	public void stop() 
	{
		
		if(executor==null) return;
			executor.shutdownNow();
		if(clientBootstrap!=null)
			clientBootstrap.shutdown();
		nioClientSocketChannelFactory.shutdown();
	}
	
	private String getStringFromJson(String packetType, Packet packet)
	{
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.setPrettyPrinting().create();
		JsonElement je = null;
		if(packet==null)
			je = gson.toJsonTree(this.packet);
		else
			je = gson.toJsonTree(packet);
		JsonObject jo = new JsonObject();
		jo.add(packetType, je);
		String output=jo.toString();
		logger.debug("Client is sending request : "+ output);
		return output;
	}
	private HttpRequest createRequest(String packetType, Packet packet)
	{
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
    	ChannelBuffer buf = ChannelBuffers.copiedBuffer(getStringFromJson(packetType, packet), Charset.forName("UTF-8"));
    	request.setHeader(HttpHeaders.Names.CONTENT_TYPE, APPLICATION_JSON);
    	request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buf.readableBytes());
    	request.setContent(buf);
    	return request;
	}

}
