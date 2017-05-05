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

import javax.servlet.ServletException;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.mobicents.tools.http.urlrewriting.BalancerUrlRewriteFilter;
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
	
	public void start() {
		executor = Executors.newCachedThreadPool();
		nioServerSocketChannelFactory = new NioServerSocketChannelFactory(executor,	executor);		
		nioClientSocketChannelFactory = new NioClientSocketChannelFactory(executor, executor);
		HttpChannelAssociations.serverBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		HttpChannelAssociations.serverSecureBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		HttpChannelAssociations.serverApiBootstrap = new ServerBootstrap(nioServerSocketChannelFactory);
		HttpChannelAssociations.inboundBootstrap = new ClientBootstrap(nioClientSocketChannelFactory);
		HttpChannelAssociations.channels = new ConcurrentHashMap<Channel, Channel>();
		if(balancerRunner.getConfiguration().getHttpConfiguration().getUrlrewriteRule()!=null)
		{
			HttpChannelAssociations.urlRewriteFilter = new BalancerUrlRewriteFilter();
			try {
				HttpChannelAssociations.urlRewriteFilter.init(balancerRunner);
			} catch (ServletException e) {
				throw new IllegalStateException("Can't init url filter due to [ " + e.getMessage()+ " ] ", e);
			}
		}
		// https://telestax.atlassian.net/browse/LB-7 making the default port the same
		Integer httpPort = 2080;
		if(balancerRunner.balancerContext.lbConfig != null) 
			httpPort = balancerRunner.balancerContext.lbConfig.getHttpConfiguration().getHttpPort();
		
		logger.info("HTTP LB listening on port " + httpPort);
		HttpChannelAssociations.serverBootstrap.setPipelineFactory(new HttpServerPipelineFactory(balancerRunner,false));
		HttpChannelAssociations.serverBootstrap.setOption("child.tcpNoDelay", true);
		HttpChannelAssociations.serverBootstrap.setOption("child.keepAlive", true);
		serverChannel = HttpChannelAssociations.serverBootstrap.bind(new InetSocketAddress(httpPort));
		Integer httpsPort = balancerRunner.balancerContext.lbConfig.getHttpConfiguration().getHttpsPort();
		if(httpsPort != null)
		{
			logger.info("HTTPS LB listening on port " + httpsPort);
			HttpChannelAssociations.serverSecureBootstrap.setPipelineFactory(new HttpServerPipelineFactory(balancerRunner, true));
			serverSecureChannel = HttpChannelAssociations.serverSecureBootstrap.bind(new InetSocketAddress(httpsPort));
			if(!balancerRunner.balancerContext.terminateTLSTraffic)
			{
				HttpChannelAssociations.inboundSecureBootstrap = new ClientBootstrap(nioClientSocketChannelFactory);
				HttpChannelAssociations.inboundSecureBootstrap.setPipelineFactory(new HttpClientPipelineFactory(balancerRunner, true));
				HttpChannelAssociations.inboundSecureBootstrap.setOption("child.tcpNoDelay", true);
				HttpChannelAssociations.inboundSecureBootstrap.setOption("child.keepAlive", true);
			}
		}
		
		Integer apiPort = balancerRunner.balancerContext.lbConfig.getCommonConfiguration().getStatisticPort();
		if(apiPort != null)
		{
			logger.info("Load balancer API port : " + apiPort);
			HttpChannelAssociations.serverApiBootstrap.setPipelineFactory(new HttpServerPipelineFactory(balancerRunner, false));
			HttpChannelAssociations.serverApiChannel = HttpChannelAssociations.serverApiBootstrap.bind(new InetSocketAddress(apiPort));
		}
		
		HttpChannelAssociations.inboundBootstrap.setPipelineFactory(new HttpClientPipelineFactory(balancerRunner, false));		
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
		if(HttpChannelAssociations.serverApiChannel!=null)
		{
			HttpChannelAssociations.serverApiChannel.unbind();
			HttpChannelAssociations.serverApiChannel.close();
			HttpChannelAssociations.serverApiChannel.getCloseFuture().awaitUninterruptibly();
		}
		
		executor.shutdownNow();
		executor = null;
		
		//cleaning everything
		HttpChannelAssociations.serverBootstrap.shutdown();
		
		if(HttpChannelAssociations.serverSecureBootstrap!=null)
			HttpChannelAssociations.serverSecureBootstrap.shutdown();
		
		if(HttpChannelAssociations.serverApiBootstrap!=null)
			HttpChannelAssociations.serverApiBootstrap.shutdown();
		
		HttpChannelAssociations.inboundBootstrap.shutdown();
		if(HttpChannelAssociations.inboundSecureBootstrap!=null)
			HttpChannelAssociations.inboundSecureBootstrap.shutdown();
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
