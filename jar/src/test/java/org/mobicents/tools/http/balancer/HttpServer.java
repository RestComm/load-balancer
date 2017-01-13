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
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.mobicents.tools.sip.balancer.NodeRegisterRMIStub;
import org.mobicents.tools.sip.balancer.SIPNode;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class HttpServer 
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
	private boolean sendHeartbeat = true;
	private String balancers;
	private Timer timer;
	private SIPNode appServerNode;
	private AtomicBoolean stopFlag = new AtomicBoolean(false);
	private String lbAddress = "127.0.0.1";
	private int lbRMIport = 2000;
	private String instanceId;
	public static int delta = 0;
	
	public HttpServer(int httpPort, int sslPort, String instanceId)
	{
		this.httpPort = httpPort;
		this.sslPort = sslPort;
		this.instanceId = instanceId;
		this.udpPort = 4060 + delta++;
	}
	public HttpServer(int httpPort, int sslPort)
	{
		this(httpPort, sslPort, null);
	}
	
	public void start() 
	{
		executor = Executors.newCachedThreadPool();
		nioServerSocketChannelFactory = new NioServerSocketChannelFactory(executor,	executor);	
		
		serverBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		serverBootstrap.setPipelineFactory(new TestHttpServerPipelineFactory(true, requestCount));
		serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", httpPort));
		
		serverSecureBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		serverSecureBootstrap.setPipelineFactory(new TestHttpServerPipelineFactory(false, requestCount));
		serverSecureChannel = serverSecureBootstrap.bind(new InetSocketAddress("127.0.0.1", sslPort));
		
		//ping
		 timer = new Timer();
		    appServerNode = new SIPNode("HttpServer", "127.0.0.1");		
			appServerNode.getProperties().put("version", "0");
			appServerNode.getProperties().put("httpPort", httpPort);
			appServerNode.getProperties().put("udpPort", udpPort);
			appServerNode.getProperties().put("sslPort", sslPort);
			if(instanceId!=null)
				appServerNode.getProperties().put("Restcomm-Instance-Id", instanceId);
			
			
			timer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					try {
						ArrayList<SIPNode> nodes = new ArrayList<SIPNode>();
						nodes.add(appServerNode);
						appServerNode.getProperties().put("version", "0");
						if(!stopFlag.get())
						sendKeepAliveToBalancers(nodes);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}, 1000, 1000);
		
	}

	public void stop() {
		
		stopFlag.getAndSet(true);
		timer.cancel();
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
		logger.info("HTTP server stoped : " + appServerNode);
	}
	private void sendKeepAliveToBalancers(ArrayList<SIPNode> info) {
		if(sendHeartbeat) {
			Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
			if(balancers != null) {
				for(String balancer:balancers.replaceAll(" ","").split(",")) {
					if(balancer.length()<2) continue;
					String host;
					String port;
					int semi = balancer.indexOf(':');
					if(semi>0) {
						host = balancer.substring(0, semi);
						port = balancer.substring(semi+1);
					} else {
						host = balancer;
						port = "2000";
					}
					try {
						Registry registry = LocateRegistry.getRegistry(host, Integer.parseInt(port));
						NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
						reg.handlePing(info);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
			} else {
				try {
					Registry registry = LocateRegistry.getRegistry(lbAddress, lbRMIport);
					NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
					reg.handlePing(info);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

	}	
	public void sendCleanShutdownToBalancers() {
		ArrayList<SIPNode> nodes = new ArrayList<SIPNode>();
		nodes.add(appServerNode);
		sendCleanShutdownToBalancers(nodes);
	}
	
	public void sendCleanShutdownToBalancers(ArrayList<SIPNode> info) {
		Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
		try {
			Registry registry = LocateRegistry.getRegistry(lbAddress, lbRMIport);
			NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
			reg.forceRemoval(info);
			stop();
			Thread.sleep(2000); // delay the OK for a while
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public AtomicInteger getRequstCount()
	{
		return requestCount;
	}
}
