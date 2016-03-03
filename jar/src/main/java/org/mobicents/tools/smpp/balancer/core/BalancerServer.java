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

package org.mobicents.tools.smpp.balancer.core;

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.smpp.balancer.impl.ServerChannelConnector;

import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.channel.SmppChannelConstants;
import com.cloudhopper.smpp.impl.DefaultSmppServerCounters;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class BalancerServer{

	private static final Logger logger = Logger.getLogger(BalancerServer.class);

    private final ChannelGroup channels;
    private final ServerChannelConnector serverConnector;
    private SmppServerConfiguration regularConfiguration;
    private ServerBootstrap regularBootstrap;
    private ServerChannelConnector securedConnector;
    private SmppServerConfiguration securedConfiguration;
    private ServerBootstrap securedBootstrap;
    private ExecutorService bossThreadPool;
    private ChannelFactory channelFactory;
    private final AtomicLong sessionIdSequence;
    private DefaultSmppServerCounters counters;
     
	public BalancerServer (SmppServerConfiguration regularConfiguration,SmppServerConfiguration securedConfiguration,ExecutorService executor, BalancerRunner balancerRunner, BalancerDispatcher lbServerListener, ScheduledExecutorService monitorExecutor) {
        this.regularConfiguration = regularConfiguration;
        this.securedConfiguration=securedConfiguration;
        this.channels = new DefaultChannelGroup();
        this.bossThreadPool = Executors.newCachedThreadPool();
        if (regularConfiguration.isNonBlockingSocketsEnabled()) 
            this.channelFactory = new NioServerSocketChannelFactory(this.bossThreadPool, executor, regularConfiguration.getMaxConnectionSize());
        else 
            this.channelFactory = new OioServerSocketChannelFactory(this.bossThreadPool, executor);
        
        this.regularBootstrap = new ServerBootstrap(this.channelFactory);
        this.regularBootstrap.setOption("reuseAddress", regularConfiguration.isReuseAddress());
        this.serverConnector = new ServerChannelConnector(channels, this, regularConfiguration, balancerRunner, lbServerListener, monitorExecutor);
        this.regularBootstrap.getPipeline().addLast(SmppChannelConstants.PIPELINE_SERVER_CONNECTOR_NAME, this.serverConnector);
        
        if(this.securedConfiguration!=null)
        {
        	this.securedBootstrap = new ServerBootstrap(this.channelFactory);
            this.securedBootstrap.setOption("reuseAddress", securedConfiguration.isReuseAddress());
            this.securedConnector = new ServerChannelConnector(channels, this, securedConfiguration, balancerRunner, lbServerListener, monitorExecutor);
            this.securedBootstrap.getPipeline().addLast(SmppChannelConstants.PIPELINE_SERVER_CONNECTOR_NAME, this.securedConnector);            
        }
        
        this.sessionIdSequence = new AtomicLong(0);        
        this.counters = new DefaultSmppServerCounters();
    }	
	
	public DefaultSmppServerCounters getCounters() 
	{
		return counters;
	}
	/**
	*Start load balancer server
	*/
	public void start() 
	{
	        try 
	        {
	            this.regularBootstrap.bind(new InetSocketAddress(regularConfiguration.getHost(), regularConfiguration.getPort()));
	            if(logger.isInfoEnabled()) {
	            logger.info(regularConfiguration.getName() + " started at " + regularConfiguration.getHost() + " : " + regularConfiguration.getPort());
	            }
	            if(this.securedConfiguration!=null)
	            {
	            	this.securedBootstrap.bind(new InetSocketAddress(securedConfiguration.getHost(), securedConfiguration.getPort()));	
	            	if(logger.isInfoEnabled()) {
	            	logger.info(securedConfiguration.getName() + " uses port : " + securedConfiguration.getPort() + " for TLS clients.");
	            	}
	            }
	        } 
	        catch (ChannelException e) 
	        {
	        	logger.error("Smpp Channel Exception:", e);
	        }
	}
	/**
	*Generate id for sessions(clients)
	*/
    public Long nextSessionId() 
    {
    	this.sessionIdSequence.compareAndSet(Long.MAX_VALUE, 0);
        return this.sessionIdSequence.getAndIncrement();
    }
    /**
	*Stop load balancer server
	*/
	public void stop() {
		if (this.channels.size() > 0) 
		{
            logger.info(regularConfiguration.getName() + " currently has [" + this.channels.size() + "] open child channel(s) that will be closed as part of stop()");
        }
		this.channelFactory.shutdown();
		this.channels.close().awaitUninterruptibly();
		this.regularBootstrap.shutdown();
		if(this.securedBootstrap!=null)
		this.securedBootstrap.shutdown();
        logger.info(regularConfiguration.getName() + " stopped at " + regularConfiguration.getHost());
	}
}