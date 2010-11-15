package org.mobicents.tools.http.balancer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.apache.tools.ant.taskdefs.ExecTask;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class HttpChannelAssocialtions {
	private static final Logger logger = Logger.getLogger(HttpChannelAssocialtions.class.getCanonicalName());
	static Executor executor;
    static ServerBootstrap serverBootstrap;
    static ClientBootstrap inboundBootstrap;
    static ConcurrentHashMap<Channel, Channel> channels;

}
