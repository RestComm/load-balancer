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

import java.net.InetSocketAddress;
import java.util.Map;
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
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.SIPNode;
import org.mobicents.tools.smpp.balancer.api.ClientConnection;
import org.mobicents.tools.smpp.balancer.timers.ServerTimerEnquire;
import org.mobicents.tools.smpp.balancer.timers.ServerTimerResponse;
import org.mobicents.tools.smpp.balancer.timers.ServerTimerConnectionCheck;
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

public class MClientConnectionImpl implements ClientConnection{
	
	private static final Logger logger = Logger.getLogger(MClientConnectionImpl.class);
	
    private Channel channel;
	private ClientBootstrap clientBootstrap;
    private MClientConnectionHandlerImpl clientConnectionHandler;
    private SmppSessionConfiguration config;
    private SIPNode node;
    private boolean isSslConnection = false;


	private final PduTranscoder transcoder;
    private ClientState clientState=ClientState.INITIAL;
    private AtomicInteger lastSequenceNumberSent = new AtomicInteger(0);

    private Long serverSessionID;
    private UserSpace userSpace;
 	private Map<Integer, TimerData> packetMap =  new ConcurrentHashMap <Integer, TimerData>();
 	private Map<Integer, Integer> sequenceMap =  new ConcurrentHashMap <Integer, Integer>();
 	
 	private Map<Integer, CustomerPacket> sequenceCustomerMap =  new ConcurrentHashMap <Integer, CustomerPacket>();
 	
    private ScheduledExecutorService monitorExecutor;
    private long timeoutResponse;
    private long timeoutEnquire;

    
    private ScheduledFuture<?> connectionCheckServerSideTimer;    
    private ServerTimerConnectionCheck connectionCheck;
    
    private ServerTimerEnquire enquireRunnable;
    private ScheduledFuture<?> enquireTimer;
    
    private long timeoutConnectionCheckServerSide;
    
	public SmppSessionConfiguration getConfig() {
		return config;
	}
    public ClientState getClientState() {
		return clientState;
	}
    public void setClientState(ClientState clientState) {
		this.clientState = clientState;
	}

    public enum ClientState 
    {    	
    	INITIAL, OPEN, BINDING, BOUND, REBINDING, UNBINDING, CLOSED    	
    }
    
	public  MClientConnectionImpl(Long serverSessionID, UserSpace userSpace, ScheduledExecutorService monitorExecutor, BalancerRunner balancerRunner, SIPNode node, boolean isSslConnection) 
	{
		  this.serverSessionID = serverSessionID;
		  this.timeoutResponse = Long.parseLong(balancerRunner.balancerContext.properties.getProperty("timeoutResponse", "10000"));
		  this.timeoutEnquire = Long.parseLong(balancerRunner.balancerContext.properties.getProperty("timeoutEnquire", "10000"));
		  this.timeoutConnectionCheckServerSide = Long.parseLong(balancerRunner.balancerContext.properties.getProperty("timeoutConnectionCheckServerSide", "10000"));
		  this.isSslConnection = isSslConnection;
		  this.monitorExecutor = monitorExecutor;
		  this.node = node;
		  this.config = new SmppSessionConfiguration();	
		  this.transcoder = new DefaultPduTranscoder(new DefaultPduTranscoderContext());
		  this.userSpace = userSpace;
		  this.clientConnectionHandler = new MClientConnectionHandlerImpl(this);	
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
				isSslConnection = true;
	      	    SslConfiguration sslConfig = config.getSslConfiguration();
	      	    if (sslConfig == null) throw new IllegalStateException("sslConfiguration must be set");
	      	    try 
	      	    {
	      	    	SslContextFactory factory = new SslContextFactory(sslConfig);
	      	    	SSLEngine sslEngine = factory.newSslEngine();
	      	    	sslEngine.setUseClientMode(true);
	      	    	channel.getPipeline().addFirst(SmppChannelConstants.PIPELINE_SESSION_SSL_NAME, new SslHandler(sslEngine));
	      	    } 
	      	    catch (Exception e) 
	      	    {
	      	    	logger.error("Unable to create SSL session: " + e.getMessage(), e);
	      	    	
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
			if(clientState!=ClientState.REBINDING)
			    clientState=ClientState.BINDING;
			channel.write(buffer);
	}

	@Override
	public void packetReceived(Pdu packet) 
	{
		switch (clientState) {

		case INITIAL:
		case OPEN:
			logger.error("LB received packet in initial or open state. server session ID : " + serverSessionID + ". packet : " + packet);
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
				logger.error("LB received invalid packet in binding state. server session ID : " + serverSessionID + ". packet : " + packet);
			else {
				if (packet.getCommandStatus() == SmppConstants.STATUS_OK) 
				{
					logger.info("Connection to server : " + config.getHost() + " : " + config.getPort()+ " established. Server session ID : " + serverSessionID);
					if(logger.isDebugEnabled())
						logger.debug("LB received bind response (" + packet + ") from server. server session ID : " + serverSessionID);
					
					enquireRunnable=new ServerTimerEnquire(this);
					enquireTimer =  monitorExecutor.scheduleAtFixedRate(enquireRunnable,timeoutEnquire,timeoutEnquire,TimeUnit.MILLISECONDS);
					clientState = ClientState.BOUND;
					userSpace.bindSuccesfull(node);
					
				} else {
					logger.error("Binding to server is unsuccesful.serverSessionId" + serverSessionID + " , error code: " + packet.getCommandStatus());
					closeChannel();
					userSpace.bindFailed(serverSessionID,  packet);
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
				
				if(logger.isDebugEnabled())
					logger.debug("LB received SMPP response (" + packet + ") form server. server session ID: " + serverSessionID);

				CustomerPacket originalCustomerPacket = sequenceCustomerMap.remove(packet.getSequenceNumber());
				correctPacket = true;
				userSpace.sendResponseToClient(originalCustomerPacket,packet);
				break;
			case SmppConstants.CMD_ID_ENQUIRE_LINK_RESP:
				if(logger.isDebugEnabled())
					logger.debug("LB received enquire_link response from server with server session ID : "+ serverSessionID);
				
				correctPacket = true;
				connectionCheck.cancel();
				connectionCheckServerSideTimer.cancel(false);
				userSpace.enquireLinkReceivedFromServer();
								
				break;
			case SmppConstants.CMD_ID_DATA_SM:
			case SmppConstants.CMD_ID_DELIVER_SM:
			case SmppConstants.CMD_ID_GENERIC_NACK:
				
				if(logger.isDebugEnabled())
					logger.debug("LB received SMPP request (" + packet + ") from server. server session ID : " + serverSessionID);
				
				lastSequenceNumberSent.getAndIncrement();
				
				correctPacket = true;
				ServerTimerResponse response=new ServerTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(response,timeoutResponse,TimeUnit.MILLISECONDS),response));
				userSpace.sendRequestToClient(packet, serverSessionID);
			
				break;
			case SmppConstants.CMD_ID_ENQUIRE_LINK:
				if(logger.isDebugEnabled())
					logger.debug("LB received enquire_link request from server. server session ID : " + serverSessionID);
				
				correctPacket = true;
				EnquireLinkResp resp=new EnquireLinkResp();
				resp.setSequenceNumber(packet.getSequenceNumber());
				sendSmppResponse(resp);
				break;		
			case SmppConstants.CMD_ID_UNBIND:
				if(logger.isDebugEnabled())
					logger.debug("LB received unbind request from server. server session ID : " + serverSessionID);
				
				enquireRunnable.cancel();			
				enquireTimer.cancel(false);
				
				correctPacket = true;
				response=new ServerTimerResponse(this ,packet);
				packetMap.put(packet.getSequenceNumber(), new TimerData(packet, monitorExecutor.schedule(response,timeoutResponse,TimeUnit.MILLISECONDS),response));
				userSpace.unbindRequestedFromServer((Unbind)packet, serverSessionID);
				clientState = ClientState.UNBINDING;
				break;

			}
			if (!correctPacket)
			{
				logger.error("LB received invalid packet in bound state. server session ID : " + serverSessionID + ". packet : " + packet);
			}
			break;
			
		case REBINDING:
			
            switch (packet.getCommandId()) 
            {
			case SmppConstants.CMD_ID_BIND_RECEIVER_RESP:
			case SmppConstants.CMD_ID_BIND_TRANSCEIVER_RESP:
			case SmppConstants.CMD_ID_BIND_TRANSMITTER_RESP:
				if (packet.getCommandStatus() == SmppConstants.STATUS_OK)
				{
					if(logger.isDebugEnabled())
						logger.debug("Connection reconnected to server. server session ID : " + serverSessionID);
					
					if(enquireTimer!=null)
					{
						enquireRunnable.cancel();			
						enquireTimer.cancel(false);
					}
					enquireRunnable=new ServerTimerEnquire(this);
					enquireTimer =  monitorExecutor.scheduleAtFixedRate(enquireRunnable,timeoutEnquire,timeoutEnquire,TimeUnit.MILLISECONDS);

				    userSpace.reconnectSuccesful(serverSessionID,this);
				    clientState = ClientState.BOUND;
				}else
				{
					if(logger.isDebugEnabled())
						logger.debug("Reconnection to server unsuccessful. server session ID : " + serverSessionID + ". LB will disconnect all clients!");
					
					userSpace.unbindRequestedFromServer(new Unbind(), serverSessionID);
				}
            }
			break;
			
		case UNBINDING:
			
			correctPacket = false;

			if (packet.getCommandId() == SmppConstants.CMD_ID_UNBIND_RESP)
				correctPacket = true;

			if (!correctPacket)
				logger.error("LB received invalid packet in unbinding state. server session ID : " + serverSessionID + ". packet : " + packet);
			else 
			{
				if(logger.isDebugEnabled())
					logger.debug("LB received unbind response form server. server sessionId : " + serverSessionID);
				
				sequenceMap.clear();
				packetMap.clear();
				
				closeChannel();
				clientState = ClientState.CLOSED;
			}
			break;
		case CLOSED:
			logger.error("LB received packet in closed state. server session ID : " + serverSessionID + ". packet : " + packet);
			break;
		}
	}

	@Override
	public void sendUnbindRequest(Pdu packet)
	{		
		enquireRunnable.cancel();			
		enquireTimer.cancel(false);
		
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
		clientState = ClientState.UNBINDING;
		if(logger.isDebugEnabled())
			logger.debug("LB sent unbind request (" + packet + ") to server. server session ID : " + serverSessionID);
		
		channel.write(buffer);
		
	}

	@Override
	public void sendSmppRequest(Long sessionId, Pdu packet) 
	{	
		Integer currSequence=lastSequenceNumberSent.incrementAndGet();
		sequenceCustomerMap.put(currSequence, new CustomerPacket(sessionId,packet.getSequenceNumber()));
		packet.setSequenceNumber(currSequence);
		
		ChannelBuffer buffer = null;
		try {
			buffer = transcoder.encode(packet);
			
		} catch (UnrecoverablePduException e) {
			logger.error("Encode error: ", e);
		} catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}
		
		if(logger.isDebugEnabled())
			logger.debug("LB sent SMPP request (" + packet + ") to server. server session ID : " + serverSessionID + " from client with sessionId : " + sessionId);
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
		if(logger.isDebugEnabled())
			logger.debug("LB sent SMPP response (" + packet + ") to server. server session ID : " + serverSessionID);
		
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
		    clientState = ClientState.CLOSED;
		    sequenceMap.clear();
			packetMap.clear();
			if(logger.isDebugEnabled())
				logger.debug("LB sent unbind response (" + packet + ") to server. server session ID : " + serverSessionID);
			
			channel.write(buffer);
			closeChannel();
		
	}

	@Override
	public void rebind() {
		if(logger.isDebugEnabled())
			logger.debug("LB tried to rebind to server. server session ID : " + serverSessionID);
		
		enquireRunnable.cancel();			
		enquireTimer.cancel(false);
		
		clientState = ClientState.REBINDING;		
		userSpace.connectionLost(serverSessionID);
	}

	@Override
	public void requestTimeout(Pdu packet) {
		if (!packetMap.containsKey(packet.getSequenceNumber()))
			if(logger.isDebugEnabled())
				logger.debug("<<requestTimeout>> LB received SMPP response in time from client");
			
		else {
			if(logger.isDebugEnabled())
				logger.debug("<<requestTimeout>> LB did NOT receive SMPP response in time from client");
			userSpace.getDispatcher().getNotRespondedPackets().incrementAndGet();
			packetMap.remove(packet.getSequenceNumber());
			PduResponse pduResponse = ((PduRequest<?>) packet).createResponse();
			pduResponse.setCommandStatus(SmppConstants.STATUS_SYSERR);
			sendSmppResponse(pduResponse);
		}
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
		} catch(RecoverablePduException e){
			logger.error("Encode error: ", e);
		}
		connectionCheck=new ServerTimerConnectionCheck(this);
		connectionCheckServerSideTimer = monitorExecutor.schedule(connectionCheck,timeoutConnectionCheckServerSide,TimeUnit.MILLISECONDS);
		if(logger.isDebugEnabled())
			logger.debug("LB sent enquire_link to server. server sessionID : " + serverSessionID);
		
		channel.write(buffer);
	}

	@Override
	public void closeChannel() 
	{
		if(channel.getPipeline().getLast()!=null)
			channel.getPipeline().removeLast();
		
		channel.close();	
		logger.info("Connection to server closed. server session ID : " + serverSessionID);
	}
	
	@Override
	public void enquireTimeout() 
	{
		if(logger.isDebugEnabled())
			logger.debug("<<enquireTimeout>> LB should check connection to server. server session ID : "+ serverSessionID + ". LB must generate enquire_link.");
		//generates enquire link to client and waits for response
		generateEnquireLink();
	}

	@Override
	public void connectionCheckServerSide() 
	{
		//if we here we should rebind because of lost connection
		rebind();		
	}
	@Override
	public void sendSmppRequest(Pdu packet) {

	}
    public boolean isSslConnection() {
		return isSslConnection;
	}
	public SIPNode getNode() {
		return node;
	}
}