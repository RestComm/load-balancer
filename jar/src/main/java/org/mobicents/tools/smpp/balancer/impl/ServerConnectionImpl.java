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

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.mobicents.tools.smpp.balancer.api.LbServerListener;
import org.mobicents.tools.smpp.balancer.api.ServerConnection;
import org.mobicents.tools.smpp.balancer.timers.ServerTimerConnection;
import org.mobicents.tools.smpp.balancer.timers.ServerTimerConnectionCheck;
import org.mobicents.tools.smpp.balancer.timers.ServerTimerEnquire;
import org.mobicents.tools.smpp.balancer.timers.ServerTimerResponse;
import org.mobicents.tools.smpp.balancer.timers.TimerData;

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.EnquireLink;
import com.cloudhopper.smpp.pdu.EnquireLinkResp;
import com.cloudhopper.smpp.pdu.GenericNack;
import com.cloudhopper.smpp.pdu.Pdu;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;
import com.cloudhopper.smpp.transcoder.PduTranscoder;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class ServerConnectionImpl implements ServerConnection {
	
	private static final Logger logger = Logger.getLogger(ServerConnectionImpl.class);
	
	private ServerState serverState = ServerState.OPEN;
	private LbServerListener lbServerListener;
	private Long sessionId;
    private SmppSessionConfiguration config = new SmppSessionConfiguration();
	private Channel channel;
	private final PduTranscoder transcoder;
	private Map<Integer, TimerData> packetMap =  new ConcurrentHashMap <Integer, TimerData>();
	private Map<Integer, Integer> sequenceMap =  new ConcurrentHashMap <Integer, Integer>();
    
	private ScheduledFuture<?> connectionTimer;
	private ServerTimerConnection connectionRunnable;
	private ScheduledFuture<?> enquireTimer;
	private ServerTimerEnquire enquireRunnable;
	private ScheduledFuture<?> connectionCheckTimer;	
	private ServerTimerConnectionCheck connectionCheckRunnable;
	
	private long timeoutResponse;
	private long timeoutConnection;
	private long timeoutEnquire;
	private long timeoutConnectionCheckClientSide;
	private ScheduledExecutorService monitorExecutor;
	
	private AtomicInteger lastSequenceNumberSent = new AtomicInteger(0);

	private boolean isClientSideOk;
	private boolean isServerSideOk;

    
    public ServerConnectionImpl(Long sessionId, Channel channel, LbServerListener lbServerListener, Properties properties, ScheduledExecutorService monitorExecutor)
    {
    	this.lbServerListener = lbServerListener;
    	this.channel = channel;
    	this.sessionId = sessionId;
    	this.transcoder = new DefaultPduTranscoder(new DefaultPduTranscoderContext());
    	this.timeoutResponse = Long.parseLong(properties.getProperty("timeoutResponse"));
    	this.timeoutConnection = Long.parseLong(properties.getProperty("timeoutConnection"));
    	this.timeoutEnquire = Long.parseLong(properties.getProperty("timeoutEnquire"));
    	this.timeoutConnectionCheckClientSide = Long.parseLong(properties.getProperty("timeoutConnectionCheckClientSide"));
    	this.monitorExecutor = monitorExecutor;
    	connectionRunnable=new ServerTimerConnection(this, sessionId);
    	this.connectionTimer =  monitorExecutor.schedule(connectionRunnable,timeoutConnection,TimeUnit.MILLISECONDS);
    }
    
    public SmppSessionConfiguration getConfig() 
    {
		return config;
	}

	public enum ServerState 
    {    	
    	OPEN, BINDING, BOUND, REBINDING, UNBINDING, CLOSED    	
    }
	
	@SuppressWarnings("rawtypes")
	@Override
	public void packetReceived(Pdu packet) {
	
		switch (serverState) {

		case OPEN:
			
			Boolean correctPacket = false;

			switch (packet.getCommandId()) {

			case SmppConstants.CMD_ID_BIND_RECEIVER:
				correctPacket = true;
				config.setType(SmppBindType.RECEIVER);
				break;
			case SmppConstants.CMD_ID_BIND_TRANSCEIVER:
				correctPacket = true;
				config.setType(SmppBindType.TRANSCEIVER);
				break;
			case SmppConstants.CMD_ID_BIND_TRANSMITTER:
				correctPacket = true;
				config.setType(SmppBindType.TRANSMITTER);
				break;
			}

			if (!correctPacket) 
			{
				logger.error("Unable to convert a BaseBind request into SmppSessionConfiguration");
				sendGenericNack(packet);
				serverState = ServerState.CLOSED;
			} else {			
				enquireRunnable=new ServerTimerEnquire(this, sessionId);
				enquireTimer =  monitorExecutor.scheduleAtFixedRate(enquireRunnable,timeoutEnquire,timeoutEnquire,TimeUnit.MILLISECONDS);
				
				if(connectionTimer!=null)
				{
					connectionRunnable.cancel();
					connectionTimer.cancel(false);
				}
				
				BaseBind bindRequest = (BaseBind) packet;
				config.setName("LoadBalancerSession." + bindRequest.getSystemId() + "."	+ bindRequest.getSystemType());
				config.setSystemId(bindRequest.getSystemId());
				config.setPassword(bindRequest.getPassword());
				config.setSystemType(bindRequest.getSystemType());
				config.setAddressRange(bindRequest.getAddressRange());
				config.setInterfaceVersion(bindRequest.getInterfaceVersion());
				ServerTimerResponse responseTimer=new ServerTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(responseTimer,timeoutResponse,TimeUnit.MILLISECONDS),responseTimer));
				lbServerListener.bindRequested(sessionId, this, bindRequest);
				serverState = ServerState.BINDING;

			}
			break;
			
		case BINDING:
			logger.error("Server received packet in incorrect state (BINDING)");
			break;
			
		case BOUND:
			correctPacket = false;
			switch (packet.getCommandId()) {
			case SmppConstants.CMD_ID_UNBIND:
				correctPacket = true;
				ServerTimerResponse responseTimer=new ServerTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(responseTimer,timeoutResponse,TimeUnit.MILLISECONDS),responseTimer));
				lbServerListener.unbindRequested(sessionId, packet);
				serverState = ServerState.UNBINDING;
				break;
			case SmppConstants.CMD_ID_CANCEL_SM:
			case SmppConstants.CMD_ID_DATA_SM:
			case SmppConstants.CMD_ID_QUERY_SM:
			case SmppConstants.CMD_ID_REPLACE_SM:
			case SmppConstants.CMD_ID_SUBMIT_SM:
			case SmppConstants.CMD_ID_SUBMIT_MULTI:
			case SmppConstants.CMD_ID_GENERIC_NACK:
				correctPacket = true;
				responseTimer=new ServerTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(responseTimer,timeoutResponse,TimeUnit.MILLISECONDS),responseTimer));
				lbServerListener.smppEntityRequested(sessionId, packet);
				break;
			case SmppConstants.CMD_ID_ENQUIRE_LINK:
				correctPacket = true;
				EnquireLinkResp resp=new EnquireLinkResp();
				resp.setSequenceNumber(packet.getSequenceNumber());
				sendResponse(resp);
				break;				
			case SmppConstants.CMD_ID_DATA_SM_RESP:
			case SmppConstants.CMD_ID_DELIVER_SM_RESP:
				Integer originalSequence=sequenceMap.remove(packet.getSequenceNumber());
				if(originalSequence!=null)
				{
					packet.setSequenceNumber(originalSequence);
					correctPacket = true;
					lbServerListener.smppEntityResponseFromClient(sessionId, packet);
				}
				
				break;				
			case SmppConstants.CMD_ID_ENQUIRE_LINK_RESP:
				correctPacket = true;
				isClientSideOk = true;
				break;
			}

			if (!correctPacket) {
				sendGenericNack(packet);
			}
			break;
			
		case REBINDING:
			PduResponse pduResponse = ((PduRequest<?>) packet).createResponse();
			pduResponse.setCommandStatus(SmppConstants.STATUS_SYSERR);
			sendResponse(pduResponse);
			break;
		case UNBINDING:
			correctPacket = false;

			if (packet.getCommandId() == SmppConstants.CMD_ID_UNBIND_RESP)
				correctPacket = true;

			if (!correctPacket)
				logger.error("Received invalid packet in unbinding state,packet type:" + packet.getCommandId());
			else {				
				enquireRunnable.cancel();
				enquireTimer.cancel(false);				
				Integer originalSequence=sequenceMap.remove(packet.getSequenceNumber());
				if(originalSequence!=null)
				{
					packet.setSequenceNumber(originalSequence);
					this.lbServerListener.unbindSuccesfullFromServer(sessionId, packet);
				}
				
				packetMap.clear();
				sequenceMap.clear();
				channel.close();
				serverState = ServerState.CLOSED;
			}
			break;
		case CLOSED:
			logger.error("Server received packet in incorrect state (CLOSED)");
			break;
		}

	}
	@Override
	public void sendBindResponse(Pdu packet){
		
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
		}catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}
				channel.write(buffer);
	
		    serverState = ServerState.BOUND;
	}
	
	@Override
	public void sendUnbindResponse(Pdu packet){
		enquireRunnable.cancel();		
		enquireTimer.cancel(false);
		
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
		}catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}
		channel.write(buffer);
		serverState = ServerState.CLOSED;

		packetMap.clear();
		sequenceMap.clear();		
	}
	@Override
	public void sendResponse(Pdu packet){
		
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
		}catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}
		channel.write(buffer);
	}
	
	@Override
    public void sendUnbindRequest(Pdu packet) {

		Integer currSequence=lastSequenceNumberSent.incrementAndGet();
		sequenceMap.put(currSequence, packet.getSequenceNumber());
		packet.setSequenceNumber(currSequence);

		ChannelBuffer buffer = null;
		try {
			buffer = transcoder.encode(packet);
			
		} catch (UnrecoverablePduException e) {
			logger.error("Encode error: ", e);
		}catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}
		
		channel.write(buffer);
		serverState = ServerState.UNBINDING;
	}
	
	private void sendGenericNack(Pdu packet){
		
		GenericNack genericNack = new GenericNack();
		genericNack.setSequenceNumber(packet.getSequenceNumber());
	    genericNack.setCommandStatus(SmppConstants.STATUS_INVCMDID);

		ChannelBuffer buffer = null;
		try {
			buffer = transcoder.encode(genericNack);
			
		} catch (UnrecoverablePduException e) {
			logger.error("Encode error: ", e);
		}catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}
		channel.write(buffer);
	}

	public void sendRequest(Pdu packet) {
		Integer currSequence=lastSequenceNumberSent.incrementAndGet();
		sequenceMap.put(currSequence, packet.getSequenceNumber());
		packet.setSequenceNumber(currSequence);
		
		ChannelBuffer buffer = null;
		try {
			buffer = transcoder.encode(packet);
			
		} catch (UnrecoverablePduException e) {
			logger.error("Encode error: ", e);
		}catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}

		channel.write(buffer);
	}
	
	public void reconnectState(boolean isReconnect) {
		if (isReconnect){
			serverState = ServerState.REBINDING;
			enquireRunnable.cancel();			
			enquireTimer.cancel(false);
		}
		else{
			if(enquireTimer!=null)
			{
				enquireRunnable.cancel();			
				enquireTimer.cancel(false);
			}
			enquireRunnable=new ServerTimerEnquire(this, sessionId);
			enquireTimer =  monitorExecutor.scheduleAtFixedRate(enquireRunnable,timeoutEnquire,timeoutEnquire,TimeUnit.MILLISECONDS);
			serverState = ServerState.BOUND;
		}
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
			sendResponse(pduResponse);
    		}
	}

	@Override
	public void connectionTimeout(Long sessionId) {

			logger.info("(connectionTimeout)Session initialization failed for sessionId: " + sessionId);
			logger.info("(connectionTimeout)Channel closed for sessionId: " + sessionId);
			channel.close();
	}

	@Override
	public void enquireTimeout(Long sessionId) {
			logger.info("(enquireTimeout)We should check connection for sessionId: "+ sessionId + ". We must generate enquire_link.");
			isServerSideOk = false;
			isClientSideOk = false;			
			lbServerListener.checkConnection(sessionId);
			connectionCheckRunnable=new ServerTimerConnectionCheck(this, sessionId);
			connectionCheckTimer = monitorExecutor.schedule(connectionCheckRunnable, timeoutConnectionCheckClientSide, TimeUnit.MILLISECONDS);
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
		}catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}
		channel.write(buffer);		
	}

	@Override
	public void connectionCheck(Long sessionId) 
	{		
		connectionCheckRunnable.cancel();
		connectionCheckTimer.cancel(false);
		if(isServerSideOk && isClientSideOk)
			logger.info("Connection is ok.");
		else 
		{
			logger.info("Close connection with sessionId " + sessionId + "  because of did not receive enquire response from client or servers");
			enquireRunnable.cancel();
			enquireTimer.cancel(false);
			lbServerListener.closeConnection(sessionId);
		}		
	}

	public void serverSideOk() {
		isServerSideOk = true;
	}

}
