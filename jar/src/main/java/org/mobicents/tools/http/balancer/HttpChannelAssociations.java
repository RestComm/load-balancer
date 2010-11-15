package org.mobicents.tools.http.balancer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;

public class HttpChannelAssociations {
	private static final Logger logger = Logger.getLogger(HttpChannelAssociations.class.getCanonicalName());
	static Executor executor;
    static ServerBootstrap serverBootstrap;
    static ClientBootstrap inboundBootstrap;
    static ConcurrentHashMap<Channel, Channel> channels;

}
