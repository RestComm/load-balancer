/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
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
package org.mobicents.tools.http.balancer;

import java.net.InetSocketAddress;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.mobicents.tools.heartbeat.impl.ClientController;
import org.mobicents.tools.heartbeat.interfaces.IClientListener;

import com.google.gson.JsonObject;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class HttpServer implements IClientListener
{
	private static final Logger logger = Logger.getLogger(HttpServer.class.getCanonicalName());
	private ExecutorService executor;
	private NioServerSocketChannelFactory nioServerSocketChannelFactory = null;
	private Channel serverChannel;
	private Channel serverSecureChannel;
	private ServerBootstrap serverBootstrap;
	private ServerBootstrap serverSecureBootstrap;
	private int httpPort;
	private int sslPort;
	private int udpPort;
	private AtomicInteger requestCount = new AtomicInteger(0);
	private Node node;

	private String lbAddress = "127.0.0.1";
	private String instanceId;
	public static int delta = 0;
	private List <String> requests = new LinkedList<String>();
	
	ClientController clientController;
	int lbPort = 2610;
	int heartbeatPort = 2222; 
	int heartbeatPeriod = 1000;
	
	public HttpServer(int httpPort, int sslPort, String instanceId, int heartbeatPort)
	{
		this.httpPort = httpPort;
		this.sslPort = sslPort;
		this.instanceId = instanceId;
		this.udpPort = 4060 + delta++;
		this.heartbeatPort = heartbeatPort;
	}
	public HttpServer(int httpPort, int sslPort, int heartbeatPort)
	{
		this(httpPort, sslPort, null, heartbeatPort);
	}
	
	public void start() 
	{
		executor = Executors.newCachedThreadPool();
		nioServerSocketChannelFactory = new NioServerSocketChannelFactory(executor,	executor);	
		
		serverBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		serverBootstrap.setPipelineFactory(new TestHttpServerPipelineFactory(true, requestCount,requests));
		serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", httpPort));
		
		serverSecureBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		serverSecureBootstrap.setPipelineFactory(new TestHttpServerPipelineFactory(false, requestCount,requests));
		serverSecureChannel = serverSecureBootstrap.bind(new InetSocketAddress("127.0.0.1", sslPort));
		
		//ping
		node = new Node("HttpServer", "127.0.0.1");		
		node.getProperties().put("version", "0");
		node.getProperties().put("httpPort",""+ httpPort);
		node.getProperties().put("udpPort",""+ udpPort);
		node.getProperties().put("sslPort",""+ sslPort);
		node.getProperties().put(Protocol.SESSION_ID, ""+System.currentTimeMillis());
		node.getProperties().put(Protocol.HEARTBEAT_PORT, ""+heartbeatPort);
		if(instanceId!=null)
			node.getProperties().put("Restcomm-Instance-Id", instanceId);
		clientController = new ClientController(this, lbAddress, lbPort, node, 5000 , heartbeatPeriod, executor);
		clientController.startClient();
		
			
	}

	public void stop() {
		clientController.stopClient(false);
		if(executor == null) return; // already stopped
		for (Entry<Channel, Channel> entry : HttpChannelAssociations.channels.entrySet()) {
			entry.getKey().unbind();
			entry.getKey().close();
			entry.getKey().getCloseFuture().awaitUninterruptibly();
			entry.getValue().unbind();
			entry.getValue().close();
			entry.getValue().getCloseFuture().awaitUninterruptibly();
		}
		
		serverChannel.unbind();
		serverSecureChannel.unbind();
		serverChannel.close();
		serverSecureChannel.close();
		serverChannel.getCloseFuture().awaitUninterruptibly();
		serverSecureChannel.getCloseFuture().awaitUninterruptibly();

		
		
		executor.shutdownNow();
		executor = null;
		
		//cleaning everything
		serverBootstrap.shutdown();
		serverSecureBootstrap.shutdown();
		nioServerSocketChannelFactory.shutdown();
		logger.info("HTTP server stoped : " + node);
	}
	
	public AtomicInteger getRequstCount()
	{
		return requestCount;
	}

	@Override
	public void responseReceived(JsonObject json) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void stopRequestReceived(MessageEvent e, JsonObject json) {
		// TODO Auto-generated method stub
		
	}
	public List<String> getRequests() {
		return requests;
	}
	
}
