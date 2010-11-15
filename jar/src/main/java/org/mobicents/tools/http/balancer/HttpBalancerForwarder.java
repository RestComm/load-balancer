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

public class HttpBalancerForwarder {
	private static final Logger logger = Logger.getLogger(HttpBalancerForwarder.class.getCanonicalName());
	ExecutorService executor;
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
		if(BalancerContext.balancerContext.properties != null) {
			String httpPortString = BalancerContext.balancerContext.properties.getProperty("httpPort", "2080");
			httpPort = Integer.parseInt(httpPortString);
		}
		logger.info("HTTP LB listening on port " + httpPort);
		HttpChannelAssociations.serverBootstrap.setPipelineFactory(new HttpServerPipelineFactory());
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
