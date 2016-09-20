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

package org.mobicents.tools.smpp.multiplexer;

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
import com.cloudhopper.smpp.pdu.Unbind;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;
import com.cloudhopper.smpp.transcoder.PduTranscoder;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class MServerConnectionImpl implements ServerConnection {
	
	private static final Logger logger = Logger.getLogger(MServerConnectionImpl.class);
	
	private ServerState serverState = ServerState.OPEN;
	private MBalancerDispatcher lbServerListener;
	private Long sessionId;
    private SmppSessionConfiguration config = new SmppSessionConfiguration();
	private Channel channel;
	private final PduTranscoder transcoder;
	private Map<Integer, TimerData> packetMap =  new ConcurrentHashMap <Integer, TimerData>();
	private Map<Integer, CustomerPacket> sequenceMap =  new ConcurrentHashMap <Integer, CustomerPacket>();
 	
	private UserSpace userSpace;
	private BaseBind<?> bindRequest;
	public BaseBind<?> getBindRequest() {
		return bindRequest;
	}
    
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
	
	private AtomicInteger lastSequenceNumberSent = new AtomicInteger(1);

	private boolean isClientSideOk;
	private boolean isServerSideOk;

    
    public MServerConnectionImpl(Long sessionId, Channel channel, MBalancerDispatcher lbServerListener, BalancerRunner balancerRunner, ScheduledExecutorService monitorExecutor, boolean useSsl)
    {
    	this.lbServerListener = lbServerListener;
    	this.channel = channel;
    	this.sessionId = sessionId;
    	this.config.setUseSsl(useSsl);
    	this.transcoder = new DefaultPduTranscoder(new DefaultPduTranscoderContext());
    	this.timeoutResponse = Long.parseLong(balancerRunner.balancerContext.properties.getProperty("timeoutResponse", "10000"));
    	this.timeoutConnection = Long.parseLong(balancerRunner.balancerContext.properties.getProperty("timeoutConnection", "10000"));
    	this.timeoutEnquire = Long.parseLong(balancerRunner.balancerContext.properties.getProperty("timeoutEnquire", "10000"));
    	this.timeoutConnectionCheckClientSide = Long.parseLong(balancerRunner.balancerContext.properties.getProperty("timeoutConnectionCheckClientSide", "10000"));
    	this.monitorExecutor = monitorExecutor;
    	this.connectionRunnable = new CustomerTimerConnection(this, sessionId);
    	this.connectionTimer =  monitorExecutor.schedule(connectionRunnable,timeoutConnection,TimeUnit.MILLISECONDS);
    }
    
    public Long getSessionId() {
		return sessionId;
	}

	public SmppSessionConfiguration getConfig() 
    {
		return config;
	}

	public enum ServerState 
    {    	
    	OPEN, BINDING, BOUND, REBINDING, UNBINDING, CLOSED    	
    }
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
				logger.error("Unable to convert a BaseBind request of client. session ID: " + sessionId);
				sendGenericNack(packet);
				channel.close();
				serverState = ServerState.CLOSED;
			} else {
				
				if(logger.isDebugEnabled())
					logger.debug("LB received bind request (" + packet + ") from client. session ID : " + sessionId);
				serverState = ServerState.BINDING;
				
				enquireRunnable=new CustomerTimerEnquire(this);
				enquireTimer =  monitorExecutor.scheduleAtFixedRate(enquireRunnable,timeoutEnquire,timeoutEnquire,TimeUnit.MILLISECONDS);
				
				if(connectionTimer!=null)
				{
					connectionRunnable.cancel();
					connectionTimer.cancel(false);
				}
				
				this.bindRequest = (BaseBind<?>) packet;
				//config.setName("LoadBalancerSession." + this.bindRequest.getSystemId() + "." + this.bindRequest.getSystemType());
				config.setSystemId(this.bindRequest.getSystemId());
				config.setPassword(this.bindRequest.getPassword());
				config.setSystemType(this.bindRequest.getSystemType());
				config.setAddressRange(this.bindRequest.getAddressRange());
				config.setInterfaceVersion(this.bindRequest.getInterfaceVersion());
				CustomerTimerResponse responseTimer=new CustomerTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(responseTimer,timeoutResponse,TimeUnit.MILLISECONDS),responseTimer));
				
				userSpace = lbServerListener.bindRequested(sessionId, this, this.bindRequest);
				userSpace.bind(this, packet);
				

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
					logger.debug("LB received unbind request from client. session ID : " + sessionId);
				
				correctPacket = true;

				enquireRunnable.cancel();
				enquireTimer.cancel(false);
				CustomerTimerResponse responseTimer=new CustomerTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(responseTimer,timeoutResponse,TimeUnit.MILLISECONDS),responseTimer));
				userSpace.unbind(sessionId, (Unbind)packet);
				serverState = ServerState.UNBINDING;
				break;
			case SmppConstants.CMD_ID_CANCEL_SM:
			case SmppConstants.CMD_ID_DATA_SM:
			case SmppConstants.CMD_ID_QUERY_SM:
			case SmppConstants.CMD_ID_REPLACE_SM:
			case SmppConstants.CMD_ID_SUBMIT_SM:
			case SmppConstants.CMD_ID_SUBMIT_MULTI:
				//GENERIC_NACK doesn't have responses so we don't have to start response timer
				responseTimer=new CustomerTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(responseTimer,timeoutResponse,TimeUnit.MILLISECONDS),responseTimer));
			case SmppConstants.CMD_ID_GENERIC_NACK:
				
				if(logger.isDebugEnabled())
					logger.debug("LB received SMPP request (" + packet + ") from client. session ID : " + sessionId);
				
				correctPacket = true;
				
				userSpace.sendRequestToServer(sessionId, packet);
				break;
			case SmppConstants.CMD_ID_ENQUIRE_LINK:
				
				if(logger.isDebugEnabled())
					logger.debug("LB received enquire_link request from client. session ID : " + sessionId);
				
				correctPacket = true;
				EnquireLinkResp resp=new EnquireLinkResp();
				resp.setSequenceNumber(packet.getSequenceNumber());
				sendResponse(resp);
				break;				
			case SmppConstants.CMD_ID_DATA_SM_RESP:
			case SmppConstants.CMD_ID_DELIVER_SM_RESP:
				
				if(logger.isDebugEnabled())
					logger.debug("LB received SMPP response (" + packet + ") from client. session ID : " + sessionId);
				
				CustomerPacket originalSequence=sequenceMap.remove(packet.getSequenceNumber());
				if(originalSequence!=null)
				{
					packet.setSequenceNumber(originalSequence.getSequence());
					correctPacket = true;
					userSpace.sendResponseToServer(sessionId,packet,originalSequence.getSessionId());
				}
				
				break;				
			case SmppConstants.CMD_ID_ENQUIRE_LINK_RESP:
				
				if(logger.isDebugEnabled())
					logger.debug("LB received enquire_link response from client. session ID : " + sessionId);

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
				logger.debug("LB received packet (" + packet + ") in REBINDING state from client. session ID : " + sessionId+". LB sent SYSERR responses!" );
			
			PduResponse pduResponse = ((PduRequest<?>) packet).createResponse();
			pduResponse.setCommandStatus(SmppConstants.STATUS_SYSERR);
			sendResponse(pduResponse);
			break;
		case UNBINDING:
			correctPacket = false;
			if (packet.getCommandId() == SmppConstants.CMD_ID_UNBIND_RESP)
				correctPacket = true;

			if (!correctPacket)
				logger.error("LB received invalid packet in unbinding state from client. session ID : " + sessionId +". packet : " + packet);
			else {
				
				if(logger.isDebugEnabled())
					logger.debug("LB received unbind response from client. session ID : " + sessionId);

				enquireRunnable.cancel();
				enquireTimer.cancel(false);				
				packetMap.clear();
				sequenceMap.clear();
				channel.close();
				serverState = ServerState.CLOSED;
			}
			break;
		case CLOSED:
			logger.error("LB received packet in incorrect state (CLOSED) from client. session ID : " + sessionId +". packet : " + packet);
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
		
		if(logger.isDebugEnabled())
			logger.debug("LB  sent response (" + packet + ") to client. session ID : " + sessionId);
		
		serverState = ServerState.BOUND;
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
			logger.debug("LB sent  unbind response ("+ packet +") to client. session ID : " + sessionId);
		
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
			logger.debug("LB sent SMPP response ("+ packet +") to client. session ID : " + sessionId);
		channel.write(buffer);
	}

	@Override
    public void sendUnbindRequest(Pdu packet) {

		Integer currSequence=lastSequenceNumberSent.incrementAndGet();
		sequenceMap.put(currSequence, new CustomerPacket(null,packet.getSequenceNumber()));
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
			logger.debug("LB sent unbind request ("+ packet +") to client. session ID : " + sessionId);

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
			logger.debug("LB sent generic_nack response for packet ("+ packet +") to client. session ID : " + sessionId);
		channel.write(buffer);
	}
	
	
	
	@Override
	public void sendRequest(Long serverSessionID,Pdu packet) {
		Integer currSequence=lastSequenceNumberSent.incrementAndGet();
		sequenceMap.put(currSequence, new CustomerPacket(serverSessionID,packet.getSequenceNumber()));
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
			logger.debug("LB sent SMPP request ("+ packet +") to client. session ID : " + sessionId);
		channel.write(buffer);
	}
	@Override
	public void reconnectState(boolean isReconnect) {
		if (isReconnect){
			serverState = ServerState.REBINDING;

			enquireRunnable.cancel();			
			enquireTimer.cancel(false);
		}
		else
		{
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
				logger.debug("<<requestTimeout>> LB received SMPP response from server in time for client. session ID : " + sessionId);	
		}	
		else 
		{
			if(logger.isDebugEnabled())
				logger.info("<<requestTimeout>> LB did NOT receive SMPP response from server in time for client. session ID : " + sessionId);
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
			logger.debug("<<connectionTimeout>> Session initialization failed and will be closed. session ID: " + sessionId);
		
			lbServerListener.getNotBindClients().incrementAndGet();
			channel.close();
	}

	@Override
	public void enquireTimeout() 
	{
		if(logger.isDebugEnabled())
			logger.debug("<<enquireTimeout>> LB should check connection to client. session ID: "+ sessionId + ". LB must generate enquire_link.");

		isServerSideOk = false;
		isClientSideOk = false;	
		//generates enquire link to client and waits for response
		generateEnquireLink();
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
			logger.debug("LB sent enquire_link request ("+ packet +") to client. session ID : " + sessionId);
		
		connectionCheckRunnable=new CustomerTimerConnectionCheck(this, sessionId);
		connectionCheckTimer = monitorExecutor.schedule(connectionCheckRunnable, timeoutConnectionCheckClientSide, TimeUnit.MILLISECONDS);
		
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
				logger.debug("Connection to client is OK. session ID : " + sessionId);
		}
		else 
		{
			if(logger.isDebugEnabled())
				logger.debug("Connection to client will be closed. session ID " + sessionId + " . LB did not receive enquire response from client or servers");
			//remove client form userspase and close connection to client
			enquireRunnable.cancel();
			enquireTimer.cancel(false);
			userSpace.getCustomers().remove(sessionId);
			closeChannel();
		
		}		
	}

	@Override
	public void serverSideOk() {
		isServerSideOk = true;
	}
	
	private  void closeChannel() 
	{
		if(channel.getPipeline().getLast()!=null)
			channel.getPipeline().removeLast();
		
		channel.close();	
		logger.info("Connection to  client. session ID : " + sessionId + " closed");
	}

	@Override
	public void sendRequest(Pdu packet) {
		
	}

}
