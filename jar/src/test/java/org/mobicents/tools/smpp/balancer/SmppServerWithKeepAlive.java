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

package org.mobicents.tools.smpp.balancer;

import java.net.InetSocketAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.jboss.netty.handler.timeout.WriteTimeoutHandler;
import org.mobicents.tools.sip.balancer.NodeRegisterRMIStub;
import org.mobicents.tools.sip.balancer.SIPNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.SmppServerHandler;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.channel.SmppChannelConstants;
import com.cloudhopper.smpp.channel.SmppServerConnector;
import com.cloudhopper.smpp.channel.SmppSessionLogger;
import com.cloudhopper.smpp.channel.SmppSessionThreadRenamer;
import com.cloudhopper.smpp.channel.SmppSessionWrapper;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.impl.DefaultSmppServerCounters;
import com.cloudhopper.smpp.impl.DefaultSmppSession;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.pdu.Pdu;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;
import com.cloudhopper.smpp.transcoder.PduTranscoder;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppProcessingException;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class SmppServerWithKeepAlive extends DefaultSmppServer{
	
	private static final Logger logger = LoggerFactory.getLogger(SmppServerWithKeepAlive.class);

	private Timer timer;
	private SIPNode appServerNode;
	private AtomicBoolean stopFlag = new AtomicBoolean(false);
	private boolean sendHeartbeat = true;
	private String balancers;
	private String lbAddress = "127.0.0.1";
	private int lbRMIport = 2000;
    private final ChannelGroup channels;
    private final SmppServerConnector serverConnector;
    private final SmppServerConfiguration configuration;
    private final SmppServerHandler serverHandler;
    private final PduTranscoder transcoder;
    private ExecutorService bossThreadPool;
    private ChannelFactory channelFactory;
    private ServerBootstrap serverBootstrap;
    private Channel serverChannel; 
    private final org.jboss.netty.util.Timer writeTimeoutTimer;
    private final Timer bindTimer;
    private final AtomicLong sessionIdSequence;
    private final ScheduledExecutorService monitorExecutor;
    private DefaultSmppServerCounters counters;
    
    public SmppServerWithKeepAlive(final SmppServerConfiguration configuration, SmppServerHandler serverHandler, ExecutorService executor, ScheduledExecutorService monitorExecutor) {
    	super(configuration, serverHandler, executor, monitorExecutor);
        this.configuration = configuration;
        this.channels = new DefaultChannelGroup();
        this.serverHandler = serverHandler;
        this.bossThreadPool = Executors.newCachedThreadPool();
        
         if (configuration.isNonBlockingSocketsEnabled()) {
            this.channelFactory = new NioServerSocketChannelFactory(this.bossThreadPool, executor, configuration.getMaxConnectionSize());
        } else {
            this.channelFactory = new OioServerSocketChannelFactory(this.bossThreadPool, executor);
        }
        this.serverBootstrap = new ServerBootstrap(this.channelFactory);
        this.serverBootstrap.setOption("reuseAddress", configuration.isReuseAddress());
        this.serverConnector = new SmppServerConnector(channels, this);
        this.serverBootstrap.getPipeline().addLast(SmppChannelConstants.PIPELINE_SERVER_CONNECTOR_NAME, this.serverConnector);
        this.writeTimeoutTimer = new org.jboss.netty.util.HashedWheelTimer();
        this.bindTimer = new Timer(configuration.getName() + "-BindTimer0", true);
        this.transcoder = new DefaultPduTranscoder(new DefaultPduTranscoderContext());
        this.sessionIdSequence = new AtomicLong(0);        
        this.monitorExecutor = monitorExecutor;
        this.counters = new DefaultSmppServerCounters();
   }

    public PduTranscoder getTranscoder() {
        return this.transcoder;
    }

    @Override
    public ChannelGroup getChannels() {
        return this.channels;
    }

    public SmppServerConfiguration getConfiguration() {
        return this.configuration;
    }
    
    @Override
    public DefaultSmppServerCounters getCounters() {
        return this.counters;
    }

    public Timer getBindTimer() {
        return this.bindTimer;
    }
    
    @Override
    public boolean isStarted() {
        return (this.serverChannel != null && this.serverChannel.isBound());
    }

    @Override
    public boolean isStopped() {
        return (this.serverChannel == null);
    }

    @Override
    public boolean isDestroyed() {
        return (this.serverBootstrap == null);
    }
    
    @Override
    public void start() throws SmppChannelException {
        if (isDestroyed()) {
            throw new SmppChannelException("Unable to start: server is destroyed");
        }
        try {
            serverChannel = this.serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
            logger.info(configuration.getName()+ "started at"+ configuration.getHost() + ":" + configuration.getPort());
        } catch (ChannelException e) {
            throw new SmppChannelException(e.getMessage(), e);
        }
        
		//ping
		 timer = new Timer();
		    appServerNode = new SIPNode("HttpServer", "127.0.0.1");		
			appServerNode.getProperties().put("version", "0");
			if(!configuration.isUseSsl())
				appServerNode.getProperties().put("smppPort", configuration.getPort());
			else
				appServerNode.getProperties().put("smppSslPort", configuration.getPort());
			
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

    @Override
    public void stop() {
    	
    	stopFlag.getAndSet(true);
		timer.cancel();
		
        if (this.channels.size() > 0) {
            logger.info("{} currently has [{}] open child channel(s) that will be closed as part of stop()", configuration.getName(), this.channels.size());
        }
        this.channels.close().awaitUninterruptibly();
        if (this.serverChannel != null) {
            this.serverChannel.close().awaitUninterruptibly();
            this.serverChannel = null;
        }
        
        logger.info(configuration.getName() + " stopped at " + configuration.getHost() + ":" + configuration.getPort());
    }
    
    @Override
    public void destroy() {
        this.bindTimer.cancel();
        stop();
        this.serverBootstrap.releaseExternalResources();
        this.serverBootstrap = null;
        this.writeTimeoutTimer.stop();
        logger.info("{} destroyed on SMPP port [{}]", configuration.getName(), configuration.getPort());
    }

    protected Long nextSessionId() {
        return this.sessionIdSequence.getAndIncrement();
    }

    protected byte autoNegotiateInterfaceVersion(byte requestedInterfaceVersion) {
        if (!this.configuration.isAutoNegotiateInterfaceVersion()) {
            return requestedInterfaceVersion;
        } else {
            if (requestedInterfaceVersion >= SmppConstants.VERSION_3_4) {
                return SmppConstants.VERSION_3_4;
            } else {
                return SmppConstants.VERSION_3_3;
            }
        }
    }

    protected BaseBindResp createBindResponse(BaseBind bindRequest, int statusCode) {
        BaseBindResp bindResponse = (BaseBindResp)bindRequest.createResponse();
        bindResponse.setCommandStatus(statusCode);
        bindResponse.setSystemId(configuration.getSystemId());

        if (configuration.getInterfaceVersion() >= SmppConstants.VERSION_3_4 && bindRequest.getInterfaceVersion() >= SmppConstants.VERSION_3_4) {
            Tlv scInterfaceVersion = new Tlv(SmppConstants.TAG_SC_INTERFACE_VERSION, new byte[] { configuration.getInterfaceVersion() });
            bindResponse.addOptionalParameter(scInterfaceVersion);
        }

        return bindResponse;
    }    

    protected void bindRequested(Long sessionId, SmppSessionConfiguration config, BaseBind bindRequest) throws SmppProcessingException {
        counters.incrementBindRequestedAndGet();
        this.serverHandler.sessionBindRequested(sessionId, config, bindRequest);
    }


    protected void createSession(Long sessionId, Channel channel, SmppSessionConfiguration config, BaseBindResp preparedBindResponse) throws SmppProcessingException {

        channel.setReadable(false).awaitUninterruptibly();

        byte interfaceVersion = this.autoNegotiateInterfaceVersion(config.getInterfaceVersion());

        DefaultSmppSession session = new DefaultSmppSession(SmppSession.Type.SERVER, config, channel, this, sessionId, preparedBindResponse, interfaceVersion, monitorExecutor);

        SmppSessionThreadRenamer threadRenamer = (SmppSessionThreadRenamer)channel.getPipeline().get(SmppChannelConstants.PIPELINE_SESSION_THREAD_RENAMER_NAME);
        threadRenamer.setThreadName(config.getName());

        SmppSessionLogger loggingHandler = new SmppSessionLogger(DefaultSmppSession.class.getCanonicalName(), config.getLoggingOptions());
        channel.getPipeline().addAfter(SmppChannelConstants.PIPELINE_SESSION_THREAD_RENAMER_NAME, SmppChannelConstants.PIPELINE_SESSION_LOGGER_NAME, loggingHandler);

	if (config.getWriteTimeout() > 0) {
	    WriteTimeoutHandler writeTimeoutHandler = new WriteTimeoutHandler(writeTimeoutTimer, config.getWriteTimeout(), TimeUnit.MILLISECONDS);
	    channel.getPipeline().addAfter(SmppChannelConstants.PIPELINE_SESSION_LOGGER_NAME, SmppChannelConstants.PIPELINE_SESSION_WRITE_TIMEOUT_NAME, writeTimeoutHandler);
	}

        channel.getPipeline().remove(SmppChannelConstants.PIPELINE_SESSION_WRAPPER_NAME);
        channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_WRAPPER_NAME, new SmppSessionWrapper(session));
        
        if (this.channels.size() > this.configuration.getMaxConnectionSize()) {
            logger.warn("The current connection size [{}] exceeds the configured max connection size [{}]", this.channels.size(), this.configuration.getMaxConnectionSize());
        }
        
        counters.incrementSessionCreatedAndGet();
        incrementSessionSizeCounters(session);
        this.serverHandler.sessionCreated(sessionId, session, preparedBindResponse);
        
        if (configuration.isJmxEnabled()) {
            session.registerMBean(configuration.getJmxDomain() + ":type=" + configuration.getName() + "Sessions,name=" + sessionId);
        }
    }


    protected void destroySession(Long sessionId, DefaultSmppSession session) {
        counters.incrementSessionDestroyedAndGet();
        decrementSessionSizeCounters(session);
        serverHandler.sessionDestroyed(sessionId, session);
        
        if (configuration.isJmxEnabled()) {
            session.unregisterMBean(configuration.getJmxDomain() + ":type=" + configuration.getName() + "Sessions,name=" + sessionId);
        }
    }
    
    private void incrementSessionSizeCounters(DefaultSmppSession session) {
        this.counters.incrementSessionSizeAndGet();
        switch (session.getBindType()) {
            case TRANSCEIVER:
                this.counters.incrementTransceiverSessionSizeAndGet();
                break;
            case RECEIVER:
                this.counters.incrementTransmitterSessionSizeAndGet();
                break;
            case TRANSMITTER:
                this.counters.incrementReceiverSessionSizeAndGet();
                break;
        }
    }
    
    private void decrementSessionSizeCounters(DefaultSmppSession session) {
        this.counters.decrementSessionSizeAndGet();
        switch (session.getBindType()) {
            case TRANSCEIVER:
                this.counters.decrementTransceiverSessionSizeAndGet();
                break;
            case RECEIVER:
                this.counters.decrementTransmitterSessionSizeAndGet();
                break;
            case TRANSMITTER:
                this.counters.decrementReceiverSessionSizeAndGet();
                break;
        }
    }

    public void sendData(Pdu pdu) {
        try {
            // encode the pdu into a buffer
            ChannelBuffer buffer = this.getTranscoder().encode(pdu);

            this.getChannels().write(buffer);

        } catch (Exception e) {
            logger.error("Fatal exception thrown while attempting to send response PDU: {}", e);
        }
    }
	
}
