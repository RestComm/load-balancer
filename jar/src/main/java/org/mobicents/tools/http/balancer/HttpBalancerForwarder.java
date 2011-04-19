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

package org.mobicents.tools.http.balancer;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.mobicents.tools.sip.balancer.BalancerContext;
import org.mobicents.tools.sip.balancer.BalancerRunner;

public class HttpBalancerForwarder {
	private static final Logger logger = Logger.getLogger(HttpBalancerForwarder.class.getCanonicalName());
	ExecutorService executor;
	public BalancerRunner balancerRunner;
	public void start() {
		executor = Executors.newFixedThreadPool(32);
		HttpChannelAssociations.serverBootstrap = new ServerBootstrap(
	            new NioServerSocketChannelFactory(
	                    executor,
	                    executor));
		HttpChannelAssociations.inboundBootstrap = new ClientBootstrap(
	            new NioClientSocketChannelFactory(
	                    executor,
	                    executor));
		HttpChannelAssociations.channels = new ConcurrentHashMap<Channel, Channel>();
	    
	    
		Integer httpPort = 2222;
		if(balancerRunner.balancerContext.properties != null) {
			String httpPortString = balancerRunner.balancerContext.properties.getProperty("httpPort", "2080");
			httpPort = Integer.parseInt(httpPortString);
		}
		logger.info("HTTP LB listening on port " + httpPort);
		HttpChannelAssociations.serverBootstrap.setPipelineFactory(new HttpServerPipelineFactory(balancerRunner));
		HttpChannelAssociations.serverBootstrap.bind(new InetSocketAddress(httpPort));
        HttpChannelAssociations.inboundBootstrap.setPipelineFactory(new HttpClientPipelineFactory());
	}
	
	public void stop() {
		if(executor == null) return; // already stopped
		HttpChannelAssociations.serverBootstrap.releaseExternalResources();
		HttpChannelAssociations.inboundBootstrap.releaseExternalResources();
		executor.shutdown();
		executor = null;
		System.gc();
	}
}
