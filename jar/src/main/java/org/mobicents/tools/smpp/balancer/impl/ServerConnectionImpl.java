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

package org.mobicents.tools.smpp.balancer.impl;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.smpp.balancer.api.ServerConnection;
import org.mobicents.tools.smpp.balancer.core.BalancerDispatcher;
import org.mobicents.tools.smpp.balancer.timers.CustomerTimerConnection;
import org.mobicents.tools.smpp.balancer.timers.CustomerTimerConnectionCheck;
import org.mobicents.tools.smpp.balancer.timers.CustomerTimerEnquire;
import org.mobicents.tools.smpp.balancer.timers.CustomerTimerResponse;
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
	private BalancerDispatcher lbServerListener;
	private Long sessionId;
    private SmppSessionConfiguration config = new SmppSessionConfiguration();
	private Channel channel;
	private final PduTranscoder transcoder;
	private Map<Integer, TimerData> packetMap =  new ConcurrentHashMap <Integer, TimerData>();
	private Map<Integer, Integer> sequenceMap =  new ConcurrentHashMap <Integer, Integer>();
    
	private ScheduledFuture<?> connectionTimer;
	private CustomerTimerConnection connectionRunnable;
	private ScheduledFuture<?> enquireTimer;
	private CustomerTimerEnquire enquireRunnable;
	private ScheduledFuture<?> connectionCheckTimer;	
	private CustomerTimerConnectionCheck connectionCheckRunnable;
	
	private long timeoutResponse;
	private long timeoutConnection;
	private long timeoutEnquire;
	private long timeoutConnectionCheckClientSide;
	private ScheduledExecutorService monitorExecutor;
	
	private AtomicInteger lastSequenceNumberSent = new AtomicInteger(0);

	private boolean isClientSideOk;
	private boolean isServerSideOk;

    
    public ServerConnectionImpl(Long sessionId, Channel channel, BalancerDispatcher lbServerListener, BalancerRunner balancerRunner, ScheduledExecutorService monitorExecutor, boolean useSsl)
    {
    	this.lbServerListener = lbServerListener;
    	this.channel = channel;
    	this.sessionId = sessionId;
    	this.config.setUseSsl(useSsl);
    	this.transcoder = new DefaultPduTranscoder(new DefaultPduTranscoderContext());
    	this.timeoutResponse = balancerRunner.balancerContext.lbConfig.getSmppConfiguration().getTimeoutResponse();
    	this.timeoutConnection = balancerRunner.balancerContext.lbConfig.getSmppConfiguration().getTimeoutConnection();
    	this.timeoutEnquire = balancerRunner.balancerContext.lbConfig.getSmppConfiguration().getTimeoutEnquire();
    	this.timeoutConnectionCheckClientSide = balancerRunner.balancerContext.lbConfig.getSmppConfiguration().getTimeoutConnectionCheckClientSide();
    	this.monitorExecutor = monitorExecutor;
    	this.connectionRunnable=new CustomerTimerConnection(this, sessionId);
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
				logger.error("Unable to convert a BaseBind request from " + channel.getRemoteAddress().toString() + ". session ID: " + sessionId);
				sendGenericNack(packet);
				channel.close();
				serverState = ServerState.CLOSED;
			} else {
				
				if(logger.isDebugEnabled())
					logger.debug("LB received bind request (" + packet + ") from " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
				
				enquireRunnable=new CustomerTimerEnquire(this);
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
				CustomerTimerResponse responseTimer=new CustomerTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(responseTimer,timeoutResponse,TimeUnit.MILLISECONDS),responseTimer));
				lbServerListener.bindRequested(sessionId, this, bindRequest);
				serverState = ServerState.BINDING;

			}
			break;
			
		case BINDING:
			logger.error("LB received packet in incorrect state (BINDING). session ID : " + sessionId + " .packet : " + packet);
			break;
			
		case BOUND:
			correctPacket = false;
			switch (packet.getCommandId()) {
			case SmppConstants.CMD_ID_UNBIND:
				
				if(logger.isDebugEnabled()) 
					logger.debug("LB received unbind request from " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
				
				correctPacket = true;
				enquireRunnable.cancel();
				enquireTimer.cancel(false);
				CustomerTimerResponse responseTimer=new CustomerTimerResponse(this ,packet);
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
				
				if(logger.isDebugEnabled())
					logger.debug("LB received SMPP request (" + packet + ") from " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
				
				correctPacket = true;
				responseTimer=new CustomerTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(responseTimer,timeoutResponse,TimeUnit.MILLISECONDS),responseTimer));
				lbServerListener.smppEntityRequested(sessionId, packet);
				break;
			case SmppConstants.CMD_ID_ENQUIRE_LINK:
				
				if(logger.isDebugEnabled())
					logger.debug("LB received enquire_link request from " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
				
				correctPacket = true;
				EnquireLinkResp resp=new EnquireLinkResp();
				resp.setSequenceNumber(packet.getSequenceNumber());
				sendResponse(resp);
				break;				
			case SmppConstants.CMD_ID_DATA_SM_RESP:
			case SmppConstants.CMD_ID_DELIVER_SM_RESP:
				
				if(logger.isDebugEnabled())
					logger.debug("LB received SMPP response (" + packet + ") from " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
				
				Integer originalSequence=sequenceMap.remove(packet.getSequenceNumber());
				if(originalSequence!=null)
				{
					packet.setSequenceNumber(originalSequence);
					correctPacket = true;
					lbServerListener.smppEntityResponseFromClient(sessionId, packet);
				}
				
				break;				
			case SmppConstants.CMD_ID_ENQUIRE_LINK_RESP:
				
				if(logger.isDebugEnabled())
					logger.debug("LB received enquire_link response from " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
				
				correctPacket = true;
				isClientSideOk = true;
				break;
			}

			if (!correctPacket) {
				sendGenericNack(packet);
			}
			break;
			
		case REBINDING:
			
			if(logger.isDebugEnabled())
				logger.debug("LB received packet (" + packet + ") in REBINDING state from " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId+". LB sent SYSERR responses!" );
			if(packet instanceof PduRequest<?>) {
				PduResponse pduResponse = ((PduRequest<?>) packet).createResponse();
				pduResponse.setCommandStatus(SmppConstants.STATUS_SYSERR);
				sendResponse(pduResponse);
			}else if (packet instanceof EnquireLinkResp)
			{
				isClientSideOk = true;
			}
			break;
		case UNBINDING:
			correctPacket = false;

			if (packet.getCommandId() == SmppConstants.CMD_ID_UNBIND_RESP)
				correctPacket = true;

			if (!correctPacket)
				logger.error("LB received invalid packet in unbinding state from " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId +". packet : " + packet);
			else {
				
				if(logger.isDebugEnabled())
					logger.debug("LB received unbind response from " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
				
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
			logger.error("LB received packet in incorrect state (CLOSED) from " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId +". packet : " + packet);
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
		serverState = ServerState.BOUND;
		if(logger.isDebugEnabled())
			logger.debug("LB  sent bind response (" + packet + ") to " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
		channel.write(buffer);
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
		serverState = ServerState.CLOSED;
		packetMap.clear();
		sequenceMap.clear();
		if(logger.isDebugEnabled())
			logger.debug("LB sent  unbind response ("+ packet +") to " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
		
		channel.write(buffer);
		
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
		
		if(logger.isDebugEnabled())
			logger.debug("LB sent SMPP response ("+ packet +") to " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
		
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
		
		serverState = ServerState.UNBINDING;
		if(logger.isDebugEnabled()) 
			logger.debug("LB sent unbind request ("+ packet +") to " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);

		channel.write(buffer);
		
	}
	/**
	*Send generic_nack to client if unable to convert request
	*from client
	*@param packet PDU packet
	*/
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
		
		if(logger.isDebugEnabled())
			logger.debug("LB sent generic_nack response for packet ("+ packet +") to " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
		channel.write(buffer);
	}
	@Override
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
		if(logger.isDebugEnabled())
			logger.debug("LB sent SMPP request ("+ packet +") to " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
		channel.write(buffer);
	}
	@Override
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
			enquireRunnable=new CustomerTimerEnquire(this);
			enquireTimer =  monitorExecutor.scheduleAtFixedRate(enquireRunnable,timeoutEnquire,timeoutEnquire,TimeUnit.MILLISECONDS);
			serverState = ServerState.BOUND;
		}
	}
	@Override
	public void requestTimeout(Pdu packet) 
	{
		if (!packetMap.containsKey(packet.getSequenceNumber()))
		{
			if(logger.isDebugEnabled())
				logger.debug("<<requestTimeout>> LB received SMPP response from server in time for client " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
		}	
		else 
		{
			if(logger.isDebugEnabled())
				logger.debug("<<requestTimeout>> LB did NOT receive SMPP response from server in time for client " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
			lbServerListener.getNotRespondedPackets().incrementAndGet();
			packetMap.remove(packet.getSequenceNumber());
			PduResponse pduResponse = ((PduRequest<?>) packet).createResponse();
			pduResponse.setCommandStatus(SmppConstants.STATUS_SYSERR);
			sendResponse(pduResponse);
		}
	}

	@Override
	public void connectionTimeout(Long sessionId) 
	{
		if(logger.isDebugEnabled())
			logger.debug("<<connectionTimeout>> Session initialization failed and will be closed " + channel.getRemoteAddress().toString() + ". session ID: " + sessionId);

			lbServerListener.getNotBindClients().incrementAndGet();
			channel.close();
	}

	@Override
	public void enquireLinkTimerCheck() 
	{
		if(logger.isDebugEnabled())
			logger.debug("<<enquireTimeout>> LB should check connection to " + channel.getRemoteAddress().toString() + ". session ID : "+ sessionId + ". LB must generate enquire_link.");
		
		isServerSideOk = false;
		isClientSideOk = false;			
		lbServerListener.checkConnection(sessionId);
		connectionCheckRunnable=new CustomerTimerConnectionCheck(this, sessionId);
		connectionCheckTimer = monitorExecutor.schedule(connectionCheckRunnable, timeoutConnectionCheckClientSide, TimeUnit.MILLISECONDS);
	}
	
	@Override
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
		if(logger.isDebugEnabled())
			logger.debug("LB sent enquire_link request ("+ packet +") to " + channel.getRemoteAddress().toString() + ". session ID : " + sessionId);
		channel.write(buffer);		
	}


	@Override
	public void connectionCheck(Long sessionId) 
	{		
		connectionCheckRunnable.cancel();
		connectionCheckTimer.cancel(false);
		if(isServerSideOk && isClientSideOk)
		{
			if(logger.isDebugEnabled())
				logger.debug("Connection to " + channel.getRemoteAddress().toString() + " is OK. session ID : " + sessionId);
		}
		else 
		{
			if(logger.isDebugEnabled())
				logger.debug("Connection to " + channel.getRemoteAddress().toString() + " will be closed. session ID " + sessionId + " . LB did not receive enquire response from client or server");
			enquireRunnable.cancel();
			enquireTimer.cancel(false);
			lbServerListener.closeConnection(sessionId);
		}		
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.tools.smpp.balancer.api.ServerConnection#updateLastTimeSMPPLinkUpdated()
	 */
	@Override
	public void updateLastTimeSMPPLinkUpdated() {
		isServerSideOk = true;
	}

	@Override
	public void sendRequest(Long serverSessionID, Pdu packet) {
		// TODO Auto-generated method stub
		
	}
}
