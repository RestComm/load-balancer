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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.heartbeat.api.Packet;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.mobicents.tools.heartbeat.interfaces.IClientListener;
import org.mobicents.tools.heartbeat.packets.StopResponsePacket;
import org.mobicents.tools.heartbeat.server.ServerPipelineFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class HeartbeatService implements IClientListener{
	private static final Logger logger = Logger.getLogger(Client.class.getCanonicalName());
	
	private Set<ClientController> clientControllers = null;
	private String heartBeatIp = null;
	private int heartBeatPort = 2222;
	private int heartBeatInterval = 5000;
	private int startInterval = 5000;
	private int maxHeartbeatErrors = 3;
	private ExecutorService executor = Executors.newCachedThreadPool();
	
	private ServerBootstrap serverBootstrap;
	private Channel serverChannel;
	private Gson gson = new Gson();
	private AtomicBoolean started=new AtomicBoolean(false);
	
	public HeartbeatService(String heartBeatIp, int heartBeatPort, int heartBeatInterval, int startInterval, int maxHeartbeatErrors)
	{
		clientControllers = new CopyOnWriteArraySet<ClientController>();
        this.heartBeatIp = heartBeatIp;
        this.heartBeatPort = heartBeatPort;
        this.heartBeatInterval = heartBeatInterval;
        this.startInterval = startInterval;
        this.maxHeartbeatErrors = maxHeartbeatErrors;
	}
	public void start ()
	{
		serverBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(executor, executor));
		serverBootstrap.setPipelineFactory(new ServerPipelineFactory(this));
		serverChannel = serverBootstrap.bind(new InetSocketAddress(heartBeatIp, heartBeatPort));
		this.started.set(true);
		logger.info("Heartbeat service listen on " + heartBeatIp + ":"+ heartBeatPort+" (Node's side)");
	}
	
	public void stop(boolean isGracefullShutdown) 
	{
		for(ClientController clientController : clientControllers)
     		clientController.stopClient(isGracefullShutdown);

    	serverChannel.unbind();
		serverChannel.close();
		serverChannel.getCloseFuture().awaitUninterruptibly();
 	}
	
	public Boolean started()
	{
		return started.get();
	}
	
	public void restartClientController(String lbAddress, int lbHeartbeatPort, Node node)
	{		
		for(ClientController cc:clientControllers)
		{
			if(cc.getLbAddress().equalsIgnoreCase(lbAddress) && cc.getLbPort()==lbHeartbeatPort)
			{
				cc.updateNode(node);
				break;
			}
		}	
	}
	
	public void startClientController(String lbAddress, int lbHeartbeatPort, Node node)
	{
		ClientController currController = new ClientController(this, lbAddress, lbHeartbeatPort, node, startInterval , heartBeatInterval, maxHeartbeatErrors, executor);
		currController.startClient();
		clientControllers.add(currController);
	}
	
	public void stopClientController(String lbAddress, int lbHeartbeatPort)
	{
		for(ClientController cc:clientControllers)
		{
			if(cc.getLbAddress().equalsIgnoreCase(lbAddress) && cc.getLbPort()==lbHeartbeatPort)
			{
				cc.stopClient(false);
				break;
			}
		}
	}
	
	public void switchover(String lbAddress, int lbPort , String fromJvmRoute, String toJvmRoute)
	{
		for(ClientController cc : clientControllers)
		{
			if(cc.getLbAddress().equals(lbAddress)&&cc.getLbPort()==lbPort)
			{
				cc.switchover(fromJvmRoute, toJvmRoute);
				logger.info("client controller connected to LB  " +cc.getLbAddress() + ":"+ cc.getLbPort() + " send switching over from " + fromJvmRoute + " to " + toJvmRoute);
			}
		}
	}
	
	@Override
	public void responseReceived(JsonObject json) {

		
	}
	@Override
	public void stopRequestReceived(MessageEvent e, JsonObject json) 
	{
		logger.info("stop request received from LB : " + json);
		for(ClientController cc : clientControllers)
		{
			if(cc.getLbAddress().equals(json.get("ipAddress").toString().replace("\"",""))&&cc.getLbPort()==Integer.parseInt(json.get("port").toString()))
			{
				cc.restartClient();
				logger.info("client controller connected to LB  " +cc.getLbAddress() + ":"+ cc.getLbPort() + " have changed state to initial");
			}
		}
			
		writeResponse(e, HttpResponseStatus.OK, Protocol.STOP, Protocol.OK);
	}
	
	private synchronized void writeResponse(MessageEvent e, HttpResponseStatus status, String command, String responceString) 
    {
		Packet packet = null;
		switch(command)
		{
			case Protocol.STOP:
				packet = new StopResponsePacket(responceString);
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
	
	public boolean isStarted(String lbAddress, int lbPort) {
		boolean isStarted = false;
		for(ClientController cc : clientControllers)
		{
			if(cc.getLbAddress().equals(lbAddress)&&cc.getLbPort()==lbPort)
			{
				isStarted = true;
			}
		}
		return isStarted;
	}
}
