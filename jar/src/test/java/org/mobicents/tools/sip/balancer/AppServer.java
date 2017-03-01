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

package org.mobicents.tools.sip.balancer;



import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sip.SipProvider;
import javax.sip.message.Response;

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
import org.mobicents.tools.heartbeat.impl.ClientController;
import org.mobicents.tools.heartbeat.interfaces.IClientListener;
import org.mobicents.tools.heartbeat.packets.StopResponsePacket;
import org.mobicents.tools.heartbeat.server.ServerPipelineFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class AppServer implements IClientListener{
	
	private static final Logger logger = Logger.getLogger(AppServer.class.getCanonicalName());
	
	public ProtocolObjects protocolObjects;
	public TestSipListener sipListener;
	int port;
	String name;
	Node node;
	public boolean sendHeartbeat = true;
	private Gson gson = new Gson();
	NioServerSocketChannelFactory nioServerSocketChannelFactory;
	String lbAddress;
	int lbRMIport;
	int lbSIPext;
	int lbSIPint;
	String transport;
	protected String balancers;
	public SipProvider sipProvider;
	public String version;
	boolean isDummy;
	boolean isMediaFailure;
	boolean isFirstStart = true;
	boolean isIpv6 = false;
	
	ClientController clientController;
	ClientController [] clientControllers;
	int lbPort = 2610;
	int heartbeatPort = 2222; 
	int heartbeatPeriod = 1000;
	
	private ServerBootstrap serverBootstrap;
	private Channel serverChannel;
	//private ExecutorService executor = Executors.newCachedThreadPool();
	private String heartbeatAddress;
	
	public AppServer(String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint, String version , String transport, int heartbeatPort) 
	{
		this.port = port;
		this.name = appServer;
		this.lbAddress = lbAddress;
		this.lbRMIport = lbRMI;
		this.lbSIPext = lbSIPext;
		this.lbSIPint = lbSIPint;
		this.version = version;
		this.transport=transport;
		this.heartbeatPort = heartbeatPort;
	}
	public AppServer(boolean isIpv6,String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint, String version , String transport) 
	{
		this(appServer, port, lbAddress, lbRMI, lbSIPext, lbSIPint, version , transport, 2222);
		this.isIpv6 = true;
	}
	
	public AppServer(String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint, String version , String transport, boolean isDummy)
	{
		this(appServer, port, lbAddress, lbRMI, lbSIPext, lbSIPint, version , transport, 2222);
		this.isDummy = isDummy; 
	}
	
	public AppServer(String appServer, int port, String lbAddress, int lbRMI, int lbSIPext, int lbSIPint, String version , String transport, boolean isDummy, boolean isMediaFailure)
	{
		this(appServer, port, lbAddress, lbRMI, lbSIPext, lbSIPint, version , transport, 2222);
		this.isDummy = isDummy;
		this.isMediaFailure = isMediaFailure;
	}
	
	public AppServer() {
		// TODO Auto-generated constructor stub
	}
	public void setBalancers(String balancers) {
		this.balancers = balancers;
	}
	
		
	public void setEventListener(EventListener listener) {
		sipListener.eventListener = listener;
	}

	public void start() {
	
		ExecutorService executor = Executors.newCachedThreadPool();
		protocolObjects = new ProtocolObjects(name,	"gov.nist", transport, false, false, true);

			if(!isDummy)
			{
				if(!isMediaFailure||!isFirstStart)
				{
					sipListener = new TestSipListener(isIpv6,port, lbSIPint, protocolObjects, false);
				}
				else
				{
					sipListener = new TestSipListener(isIpv6,port, lbSIPint, protocolObjects, false);
					sipListener.setRespondWithError(Response.SERVICE_UNAVAILABLE);
				}
			}
			else
			{
				sipListener = new TestSipListener(isIpv6,port+1, lbSIPint, protocolObjects, false);
			}

		sipListener.appServer = this;
		try 
		{
			sipProvider = sipListener.createProvider();
			sipProvider.addSipListener(sipListener);
			protocolObjects.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
		//generate node
		if(!isIpv6)
			node = new Node(name, "127.0.0.1");
		else
			node = new Node(name, "::1");
		
		node.getProperties().put(transport.toLowerCase() + "Port",""+ port);		
		node.getProperties().put(Protocol.VERSION, version);
		node.getProperties().put(Protocol.SESSION_ID, ""+System.currentTimeMillis());
		node.getProperties().put(Protocol.HEARTBEAT_PORT, ""+heartbeatPort);
		nioServerSocketChannelFactory = new NioServerSocketChannelFactory(executor, executor);
		serverBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		serverBootstrap.setPipelineFactory(new ServerPipelineFactory(this));
		serverChannel = serverBootstrap.bind(new InetSocketAddress(node.getIp(), heartbeatPort));
		
		logger.info("Heartbeat service listen on " +heartbeatAddress+":"+ heartbeatPort+" (Node's side)");
		
		//start client
		if(balancers==null)
			clientController = new ClientController(this, lbAddress, lbPort, node, 5000 ,heartbeatPeriod, executor);
		else
		{
			String [] lbs = balancers.split(",");
			clientControllers = new ClientController [lbs.length];
			for(int i=0; i < lbs.length; i++)
			{
				if(!isIpv6)
					node = new Node(name, "127.0.0.1");
				else
					node = new Node(name, "::1");
				
				node.getProperties().put(transport.toLowerCase() + "Port",""+ port);		
				node.getProperties().put(Protocol.VERSION, version);
				node.getProperties().put(Protocol.HEARTBEAT_PORT, ""+heartbeatPort);
				
				clientControllers[i] = new ClientController(this, lbs[i].split(":")[0], Integer.parseInt(lbs[i].split(":")[1]), node, 5000 , heartbeatPeriod, executor);
				clientControllers[i].startClient();
			}
		}
		if(sendHeartbeat)
		{
			if(balancers==null)
				clientController.startClient();
		}
	}
	
	public void stop() {
		if(balancers==null)
			clientController.stopClient(false);
		else
			for(ClientController cc : clientControllers)
				cc.stopClient(false);
		
		serverChannel.unbind();
		serverChannel.close();
		serverChannel.getCloseFuture().awaitUninterruptibly();
		nioServerSocketChannelFactory.shutdown();
		isFirstStart = false;

		if(protocolObjects != null)
			protocolObjects.sipStack.stop();
		
		protocolObjects=null;
	}

	public TestSipListener getTestSipListener() {
		return this.sipListener;
	}
	
	public Node getNode() {
		return node;
	}

	public void gracefulShutdown()
	{
		clientController.stopClient(true);
	}
	
	@Override
	public void responseReceived(JsonObject json) 
	{

	}
	@Override
	public void stopRequestReceived(MessageEvent e, JsonObject json) 
	{
		logger.info("stop request received from LB : " + json);
		if(balancers==null)
			clientController.restartClient();
		else
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

}
