package org.mobicents.tools.http.balancer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class HttpChannelAssocialtions {
	private static final Logger logger = Logger.getLogger(HttpChannelAssocialtions.class.getCanonicalName());
    static ServerBootstrap serverBootstrap = new ServerBootstrap(
            new NioServerSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool()));
    static ClientBootstrap inboundBootstrap = new ClientBootstrap(
            new NioClientSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool()));
    static ConcurrentHashMap<Channel, Channel> channels = new ConcurrentHashMap<Channel, Channel>();

}
