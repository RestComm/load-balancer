/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015-2016, Red Hat, Inc. and individual contributors
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

package org.mobicents.tools.smpp.balancer.impl;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.mobicents.tools.smpp.balancer.api.ClientConnection;
import org.mobicents.tools.smpp.balancer.api.LbClientListener;
import org.mobicents.tools.smpp.balancer.timers.ClientTimerResponse;
import org.mobicents.tools.smpp.balancer.timers.ClientTimerServerSideConnectionCheck;
import org.mobicents.tools.smpp.balancer.timers.TimerData;

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.channel.SmppChannelConstants;
import com.cloudhopper.smpp.channel.SmppSessionPduDecoder;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BindReceiver;
import com.cloudhopper.smpp.pdu.BindTransceiver;
import com.cloudhopper.smpp.pdu.BindTransmitter;
import com.cloudhopper.smpp.pdu.EnquireLink;
import com.cloudhopper.smpp.pdu.EnquireLinkResp;
import com.cloudhopper.smpp.pdu.Pdu;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.pdu.Unbind;
import com.cloudhopper.smpp.ssl.SslConfiguration;
import com.cloudhopper.smpp.ssl.SslContextFactory;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;
import com.cloudhopper.smpp.transcoder.PduTranscoder;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class ClientConnectionImpl implements ClientConnection{
	private static final Logger logger = Logger.getLogger(ClientConnectionImpl.class);
	
    private Channel channel;
	private ClientBootstrap clientBootstrap;
    private ClientConnectionHandlerImpl clientConnectionHandler;
    private SmppSessionConfiguration config;
    private Pdu bindPacket;
	private final PduTranscoder transcoder;
    private ClientState clientState=ClientState.INITIAL;
    private AtomicInteger lastSequenceNumberSent = new AtomicInteger(0);

	private LbClientListener lbClientListener;
    private Long sessionId;
 	private Map<Integer, TimerData> packetMap =  new ConcurrentHashMap <Integer, TimerData>();
 	private Map<Integer, Integer> sequenceMap =  new ConcurrentHashMap <Integer, Integer>();
    private ScheduledExecutorService monitorExecutor;
    private long timeoutResponse;
    private int serverIndex;
    private boolean isEnquireLinkSent;
    
    private ScheduledFuture<?> connectionCheckServerSideTimer;    
    private ClientTimerServerSideConnectionCheck connectionCheck;
    
    private long timeoutConnectionCheckServerSide;
    
    public boolean isEnquireLinkSent() {
		return isEnquireLinkSent;
	}
	public SmppSessionConfiguration getConfig() {
		return config;
	}
    public ClientState getClientState() {
		return clientState;
	}
    public void setClientState(ClientState clientState) {
		this.clientState = clientState;
	}
    public Long getSessionId() {
		return sessionId;
	}

    public enum ClientState 
    {    	
    	INITIAL, OPEN, BINDING, BOUND, REBINDING, UNBINDING, CLOSED    	
    }
    
	public  ClientConnectionImpl(Long sessionId,SmppSessionConfiguration config, LbClientListener clientListener, ScheduledExecutorService monitorExecutor, 
			Properties properties, Pdu bindPacket, int serverIndex) 
	{

		  this.serverIndex = serverIndex;
		  this.bindPacket = bindPacket;
		  this.timeoutResponse = Long.parseLong(properties.getProperty("timeoutResponse"));
		  this.timeoutConnectionCheckServerSide = Long.parseLong(properties.getProperty("timeoutConnectionCheckServerSide"));
		  this.monitorExecutor = monitorExecutor;
		  this.sessionId = sessionId;
		  this.config = config;
		  this.transcoder = new DefaultPduTranscoder(new DefaultPduTranscoderContext());
		  this.lbClientListener=clientListener;
		  this.clientConnectionHandler = new ClientConnectionHandlerImpl(this);	
          this.clientBootstrap = new ClientBootstrap(new NioClientSocketChannelFactory());
          this.clientBootstrap.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_PDU_DECODER_NAME, new SmppSessionPduDecoder(transcoder));
          this.clientBootstrap.getPipeline().addLast(SmppChannelConstants.PIPELINE_CLIENT_CONNECTOR_NAME, this.clientConnectionHandler); 		  
	}
	
	@Override
	public Boolean connect() {
		ChannelFuture channelFuture = null;
		try 
		{
			channelFuture = clientBootstrap.connect(new InetSocketAddress(config.getHost(), config.getPort())).sync();
			channel = channelFuture.getChannel();
			
			if (config.isUseSsl()) 
	          {
	      	    SslConfiguration sslConfig = config.getSslConfiguration();
	      	    if (sslConfig == null) throw new IllegalStateException("sslConfiguration must be set");
	      	    try 
	      	    {
	      	    	SslContextFactory factory = new SslContextFactory(sslConfig);
	      	    	SSLEngine sslEngine = factory.newSslEngine();
	      	    	sslEngine.setUseClientMode(true);
	      	    	channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_SSL_NAME, new SslHandler(sslEngine));
	      	    } 
	      	    catch (Exception e) 
	      	    {
	      	    	logger.error("Unable to create SSL session]: " + e.getMessage(), e);
	      	    	
	      	    }
	          }

		} catch (Exception ex) 
		{
			return false;
		}

		if(clientState!=ClientState.REBINDING)
		clientState = ClientState.OPEN;

		return true;
	}
		
	@SuppressWarnings("rawtypes")
	@Override
	public void bind()
	{
		 BaseBind packet = null;
	        if (config.getType() == SmppBindType.TRANSCEIVER) 
	        	packet = new BindTransceiver();
	        else if (config.getType() == SmppBindType.RECEIVER)
	        	packet = new BindReceiver();
	        else if (config.getType() == SmppBindType.TRANSMITTER)
	        	packet = new BindTransmitter();
	       

	        packet.setSystemId(config.getSystemId());
	        packet.setPassword(config.getPassword());
	        packet.setSystemType(config.getSystemType());
	        packet.setInterfaceVersion(config.getInterfaceVersion());
	        packet.setAddressRange(config.getAddressRange());
	        packet.setSequenceNumber(lastSequenceNumberSent.incrementAndGet());
  
	        ChannelBuffer buffer = null;
			try {
				buffer = transcoder.encode(packet);
				
			} catch (UnrecoverablePduException e) {			
				logger.error("Encode error: ", e);
			} catch(RecoverablePduException e) {
				logger.error("Encode error: ", e);
			}

			channel.write(buffer);
		    if(clientState!=ClientState.REBINDING)
		    clientState=ClientState.BINDING;			
   
	}

	@Override
	public void packetReceived(Pdu packet) 
	{
		switch (clientState) {

		case INITIAL:
		case OPEN:
			logger.error("Received packet in initial or open state");
			break;
		case BINDING:
			Boolean correctPacket = false;
			switch (config.getType()) {
			case TRANSCEIVER:
				if (packet.getCommandId() == SmppConstants.CMD_ID_BIND_TRANSCEIVER_RESP)
					correctPacket = true;
				break;
			case RECEIVER:
				if (packet.getCommandId() == SmppConstants.CMD_ID_BIND_RECEIVER_RESP)
					correctPacket = true;
				break;
			case TRANSMITTER:
				if (packet.getCommandId() == SmppConstants.CMD_ID_BIND_TRANSMITTER_RESP)
					correctPacket = true;
				break;

			}

			if (!correctPacket)
				logger.error("Received invalid packet in binding state, packet type: " + packet.getCommandId());
			else {
				if (packet.getCommandStatus() == SmppConstants.STATUS_OK) {
					
					lbClientListener.bindSuccesfull(sessionId, packet);
					clientState = ClientState.BOUND;

				} else {
					logger.info("Client " + config.getSystemId() + " bound unsuccesful, error code: " + packet.getCommandStatus());
					lbClientListener.bindFailed(sessionId, packet);
					closeChannel();
					clientState = ClientState.CLOSED;
				}
			}
			break;
			
		case BOUND:
			correctPacket = false;
			switch (packet.getCommandId()) {
			
			case SmppConstants.CMD_ID_CANCEL_SM_RESP:
			case SmppConstants.CMD_ID_DATA_SM_RESP:
			case SmppConstants.CMD_ID_QUERY_SM_RESP:
			case SmppConstants.CMD_ID_REPLACE_SM_RESP:
			case SmppConstants.CMD_ID_SUBMIT_SM_RESP:
			case SmppConstants.CMD_ID_SUBMIT_MULTI_RESP:
				Integer originalSequence=sequenceMap.remove(packet.getSequenceNumber());
				if(originalSequence!=null)
				{
					packet.setSequenceNumber(originalSequence);
					correctPacket = true;
					this.lbClientListener.smppEntityResponse(sessionId, packet);
				}
				break;
			case SmppConstants.CMD_ID_GENERIC_NACK:
				correctPacket = true;
				this.lbClientListener.smppEntityResponse(sessionId, packet);
				break;
			case SmppConstants.CMD_ID_ENQUIRE_LINK_RESP:
				correctPacket = true;
				isEnquireLinkSent = false;
				connectionCheck.cancel();
				connectionCheckServerSideTimer.cancel(false);
				this.lbClientListener.enquireLinkReceivedFromServer(sessionId);				
				break;
			case SmppConstants.CMD_ID_DATA_SM:
			case SmppConstants.CMD_ID_DELIVER_SM:
				correctPacket = true;
				ClientTimerResponse response=new ClientTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(response,timeoutResponse,TimeUnit.MILLISECONDS),response));				
				this.lbClientListener.smppEntityRequestFromServer(sessionId, packet);				
				break;
			case SmppConstants.CMD_ID_ENQUIRE_LINK:
				correctPacket = true;
				EnquireLinkResp resp=new EnquireLinkResp();
				resp.setSequenceNumber(packet.getSequenceNumber());
				sendSmppResponse(resp);
				break;		
			case SmppConstants.CMD_ID_UNBIND:
				correctPacket = true;
				response=new ClientTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(response,timeoutResponse,TimeUnit.MILLISECONDS),response));
				
				lbClientListener.unbindRequestedFromServer(sessionId, packet);
				clientState = ClientState.UNBINDING;
				break;

			}
			if (!correctPacket){
				logger.error("Received invalid packet in bound state, packet type: " + packet.getCommandId());
			}
			break;
			
		case REBINDING:
			
            switch (packet.getCommandId()) 
            {
			case SmppConstants.CMD_ID_BIND_RECEIVER_RESP:
			case SmppConstants.CMD_ID_BIND_TRANSCEIVER_RESP:
			case SmppConstants.CMD_ID_BIND_TRANSMITTER_RESP:
				if (packet.getCommandStatus() == SmppConstants.STATUS_OK){
				logger.info("Connection reconnected for sessionId : " + sessionId);
				this.lbClientListener.reconnectSuccesful(sessionId);
				clientState = ClientState.BOUND;
				}else{
					this.lbClientListener.unbindRequestedFromServer(sessionId, new Unbind());
				}
            }
			break;
			
		case UNBINDING:
			correctPacket = false;

			if (packet.getCommandId() == SmppConstants.CMD_ID_UNBIND_RESP)
				correctPacket = true;

			if (!correctPacket)
				logger.error("Received invalid packet in unbinding state,packet type:" + packet.getCommandId());
			else {
				Integer originalSequence=sequenceMap.remove(packet.getSequenceNumber());
				if(originalSequence!=null)
				{
					packet.setSequenceNumber(originalSequence);
					this.lbClientListener.unbindSuccesfull(sessionId, packet);
				}
				
				sequenceMap.clear();
				packetMap.clear();
				closeChannel();
				clientState = ClientState.CLOSED;
			}
			break;
		case CLOSED:
			logger.error("Received packet in closed state");
			break;
		}
	}
	
	@Override
	public void sendUnbindRequest(Pdu packet)
	{		
		Integer currSequence=lastSequenceNumberSent.incrementAndGet();
		sequenceMap.put(currSequence, packet.getSequenceNumber());
		packet.setSequenceNumber(currSequence);
		
		ChannelBuffer buffer = null;
		try {
			buffer = transcoder.encode(packet);
			
		} catch (UnrecoverablePduException e ) {
			logger.error("Encode error: ", e);
		} catch (RecoverablePduException e){
			logger.error("Encode error: ", e);
		}

		channel.write(buffer);
		clientState = ClientState.UNBINDING;
	}
	
	@Override
	public void sendSmppRequest(Pdu packet) 
	{		
		Integer currSequence=lastSequenceNumberSent.incrementAndGet();
		sequenceMap.put(currSequence, packet.getSequenceNumber());
		packet.setSequenceNumber(currSequence);
		
		ChannelBuffer buffer = null;
		try {
			buffer = transcoder.encode(packet);
			
		} catch (UnrecoverablePduException e) {
			logger.error("Encode error: ", e);
		} catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}

		channel.write(buffer);
	}
	@Override
	public void sendSmppResponse(Pdu packet) 
	{
		
		if(packetMap.containsKey(packet.getSequenceNumber()))
		{
			TimerData data=packetMap.remove(packet.getSequenceNumber());
			if(data!=null)
			{
				data.getRunnable().cancel();
				data.getScheduledFuture().cancel(false);
			}
		}
		
		ChannelBuffer buffer = null;
		try {
			buffer = transcoder.encode(packet);
			
		} catch (UnrecoverablePduException e) {
			logger.error("Encode error: ", e);
		} catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}
		channel.write(buffer);
	}
	@Override
     public void sendUnbindResponse(Pdu packet) {
		
		if(packetMap.containsKey(packet.getSequenceNumber()))
		{
			TimerData data=packetMap.remove(packet.getSequenceNumber());
			if(data!=null)
			{
				data.getRunnable().cancel();
				data.getScheduledFuture().cancel(false);
			}
		}
		 
		ChannelBuffer buffer = null;
		try {
				buffer = transcoder.encode(packet);
				
			} catch (UnrecoverablePduException e) {
				logger.error("Encode error: ", e);
			} catch(RecoverablePduException e){
				logger.error("Encode error: ", e);
			}
			channel.write(buffer);
			clientState = ClientState.CLOSED;
			sequenceMap.clear();
			packetMap.clear();
			closeChannel();
		
	}
	
	@Override
	public void rebind() {
		StackTraceElement[] elements=Thread.currentThread().getStackTrace();
		for(int i=0;i<elements.length;i++)
			System.out.println(elements[i].getClassName() + ":" + elements[i].getLineNumber() + ":" + elements[i].getMethodName());
		
		
		clientState = ClientState.REBINDING;		
		this.lbClientListener.connectionLost(sessionId, bindPacket, serverIndex);
		
	}

	@Override
	public void requestTimeout(Pdu packet) 
	{
		if(!packetMap.containsKey(packet.getSequenceNumber()))
    		logger.info("(requestTimeout)We take SMPP response from client in time");
		else
		{
			logger.info("(requestTimeout)We did NOT take SMPP response from client in time");
			
		packetMap.remove(packet.getSequenceNumber());
    	PduResponse pduResponse = ((PduRequest<?>) packet).createResponse();
		pduResponse.setCommandStatus(SmppConstants.STATUS_SYSERR);
		sendSmppResponse(pduResponse);
		}
	}
	
	public void generateEnquireLink() 
	{		
		Pdu packet = new EnquireLink();
		packet.setSequenceNumber(lastSequenceNumberSent.incrementAndGet());
		
		ChannelBuffer buffer = null;
		try {
			buffer = transcoder.encode(packet);
			
		} catch (UnrecoverablePduException e) {
			logger.error("Encode error: ", e);
		} catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}
		channel.write(buffer);
		isEnquireLinkSent = true;
		connectionCheck=new ClientTimerServerSideConnectionCheck(this, sessionId);
		connectionCheckServerSideTimer = monitorExecutor.schedule(connectionCheck,timeoutConnectionCheckServerSide,TimeUnit.MILLISECONDS);
	}
	
	public void closeChannel() 
	{
		if(channel.getPipeline().getLast()!=null)
			channel.getPipeline().removeLast();
		
		channel.close();		
	}
	
	@Override
	public void connectionCheckServerSide(Long sessionId) 
	{
		rebind();		
	}
	
}