/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.mobicents.tools.sip.balancer.BalancerRunner;

/**
 * @author Vladimir Ralev (vladimir.ralev@jboss.org)
 * @author Jean Deruelle (jean.deruelle@telestax.com)
 */
public class HttpBalancerForwarder {
	private static final Logger logger = Logger.getLogger(HttpBalancerForwarder.class.getCanonicalName());
	ExecutorService executor;
	public BalancerRunner balancerRunner;
	NioServerSocketChannelFactory nioServerSocketChannelFactory = null;
	NioClientSocketChannelFactory nioClientSocketChannelFactory = null;
	Channel serverChannel;
	Channel serverSecureChannel;
	Channel serverStatisticChannel;
	
	public void start() {
		executor = Executors.newCachedThreadPool();
		nioServerSocketChannelFactory = new NioServerSocketChannelFactory(executor,	executor);		
		nioClientSocketChannelFactory = new NioClientSocketChannelFactory(executor, executor);
		HttpChannelAssociations.serverBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		HttpChannelAssociations.serverSecureBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		HttpChannelAssociations.serverStatisticBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		HttpChannelAssociations.inboundBootstrap = new ClientBootstrap(nioClientSocketChannelFactory);
		HttpChannelAssociations.channels = new ConcurrentHashMap<Channel, Channel>();

		// https://telestax.atlassian.net/browse/LB-7 making the default port the same
		Integer httpPort = 2080;
		if(balancerRunner.balancerContext.properties != null) {
			String httpPortString = balancerRunner.balancerContext.properties.getProperty("httpPort", "2080");
			httpPort = Integer.parseInt(httpPortString);
		}
		
		// Defaulting to 1 MB
		int maxContentLength = 1048576;
		if(balancerRunner.balancerContext.properties != null) {
			String maxContentLengthString = balancerRunner.balancerContext.properties.getProperty("maxContentLength", "1048576");
			maxContentLength = Integer.parseInt(maxContentLengthString);
		}
		logger.info("HTTP LB listening on port " + httpPort);
		logger.debug("HTTP maxContentLength Chunking set to " + maxContentLength);
		HttpChannelAssociations.serverBootstrap.setPipelineFactory(new HttpServerPipelineFactory(balancerRunner, maxContentLength, false));
		serverChannel = HttpChannelAssociations.serverBootstrap.bind(new InetSocketAddress(httpPort));
		if(balancerRunner.balancerContext.properties.getProperty("httpsPort")!=null)
		{
			Integer httpsPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("httpsPort", "2085"));
			logger.info("HTTPS LB listening on port " + httpsPort);
			HttpChannelAssociations.serverSecureBootstrap.setPipelineFactory(new HttpServerPipelineFactory(balancerRunner, maxContentLength, true));
			serverSecureChannel = HttpChannelAssociations.serverSecureBootstrap.bind(new InetSocketAddress(httpsPort));
		}
		if(balancerRunner.balancerContext.properties.getProperty("statisticPort")!=null)
		{
			Integer statisticPort = Integer.parseInt(balancerRunner.balancerContext.properties.getProperty("statisticPort"));
			logger.info("Load balancer statistic on port : " + statisticPort);
			HttpChannelAssociations.serverStatisticBootstrap.setPipelineFactory(new HttpServerPipelineFactory(balancerRunner, maxContentLength, false));
			serverStatisticChannel = HttpChannelAssociations.serverStatisticBootstrap.bind(new InetSocketAddress(statisticPort));
		}
		
		HttpChannelAssociations.inboundBootstrap.setPipelineFactory(new HttpClientPipelineFactory(balancerRunner, maxContentLength));		
	}

	public void stop() {
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
		serverChannel.close();
		serverChannel.getCloseFuture().awaitUninterruptibly();

		if(serverSecureChannel!=null)
		{
			serverSecureChannel.unbind();
			serverSecureChannel.close();
			serverSecureChannel.getCloseFuture().awaitUninterruptibly();
		}
		if(serverStatisticChannel!=null)
		{
			serverStatisticChannel.unbind();
			serverStatisticChannel.close();
			serverStatisticChannel.getCloseFuture().awaitUninterruptibly();
		}
		
		executor.shutdownNow();
		executor = null;
		
		//cleaning everything
		balancerRunner.stop();
		HttpChannelAssociations.serverBootstrap.shutdown();
		
		if(HttpChannelAssociations.serverSecureBootstrap!=null)
			HttpChannelAssociations.serverSecureBootstrap.shutdown();
		
		if(HttpChannelAssociations.serverStatisticBootstrap!=null)
			HttpChannelAssociations.serverStatisticBootstrap.shutdown();
		
		HttpChannelAssociations.inboundBootstrap.shutdown();
		nioServerSocketChannelFactory.shutdown();
		nioClientSocketChannelFactory.shutdown();
		
		//		System.gc();
	}
	
	//Statistic
	/**
     * @return the httpRequestCount
     */
	public long getNumberOfHttpRequests() 
	{
		return balancerRunner.balancerContext.httpRequests.get();
	}
	
	/**
     * @return the httpBytesToServer
     */
	public long getNumberOfHttpBytesToServer() 
	{
		return balancerRunner.balancerContext.httpBytesToServer.get();
	}
	
	/**
     * @return the httpBytesToClient
     */
	public long getNumberOfHttpBytesToClient() 
	{
		return balancerRunner.balancerContext.httpBytesToClient.get();
	}
	
	/**
     * @return the httpRequestsProcessedByMethod
     */
	public long getHttpRequestsProcessedByMethod(String method) 
	{
	        AtomicLong httpRequestsProcessed = balancerRunner.balancerContext.httpRequestsProcessedByMethod.get(method);
	        if(httpRequestsProcessed != null) {
	            return httpRequestsProcessed.get();
	        }
	        return 0;
	}
	
	/**
     * @return the httpResponseProcessedByCode
     */
	public long getHttpResponseProcessedByCode(String code) 
	{
	        AtomicLong httpRequestsProcessed = balancerRunner.balancerContext.httpResponseProcessedByCode.get(code);
	        if(httpRequestsProcessed != null) {
	            return httpRequestsProcessed.get();
	        }
	        return 0;
	}
	/**
     * @return the NumberOfActiveHttpConnections
     */
	public int getNumberOfActiveHttpConnections()
	{
		return HttpChannelAssociations.channels.size();
	}
}
