/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
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
package org.mobicents.tools.heartbeat.impl;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.jboss.netty.channel.MessageEvent;
import org.mobicents.tools.heartbeat.api.Node;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.mobicents.tools.heartbeat.interfaces.IClient;
import org.mobicents.tools.heartbeat.interfaces.IClientListener;

import com.google.gson.JsonObject;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class ClientController implements IClientListener {
	
	private static final Logger logger = Logger.getLogger(ClientController.class.getCanonicalName());
	
	private IClient client;
	private IClientListener listener;
	private Timer startTimer, heartbeatTimer;
	private int heartbeatPeriod;
	private int startPeriod;
	private String lbAddress;
	private int lbPort;
	private int maxErrors = 3;
	private AtomicInteger failResponsesCounter = new AtomicInteger(0);
	
	public ClientController(IClientListener listener, String lbAddress, int lbPort, Node node, int startPeriod, int heartbeatPeriod, ExecutorService executor)
	{
		this.listener = listener;
		this.heartbeatPeriod = heartbeatPeriod;
		this.startPeriod = startPeriod;
		this.lbAddress = lbAddress;
		this.lbPort = lbPort;
		client = new Client(this, lbAddress, lbPort, node, executor);		
	}
	public ClientController(IClientListener listener, String lbAddress, int lbPort, Node node, int startPeriod, int heartbeatPeriod, int maxErrors, ExecutorService executor)
	{
		this.listener = listener;
		this.heartbeatPeriod = heartbeatPeriod;
		this.startPeriod = startPeriod;
		this.lbAddress = lbAddress;
		this.lbPort = lbPort;
		this.maxErrors = maxErrors;
		client = new Client(this, lbAddress, lbPort, node, executor);		
	}
	
	public void updateNode(Node node)
	{
		this.client.updateNode(node);
		this.restartClient();
	}
	
	public void startClient()
	{
		startTimer = new Timer();
		client.start();
		startTimer.scheduleAtFixedRate(new TimerTask() {
				public void run() {
					client.sendPacket(Protocol.START);
				}
			}, 1000, startPeriod);
	}
	
	public void restartClient()
	{
		if(heartbeatTimer != null)
			{
				heartbeatTimer.cancel();
				startTimer.cancel();
				startTimer =  new Timer();
				startTimer.scheduleAtFixedRate(new TimerTask() {
					public void run() {
					client.sendPacket(Protocol.START);
						}
				}, 1000, startPeriod);
			}
	}
	
	public void switchover(String fromJvmRoute, String toJvmRoute)
	{
		client.switchover(fromJvmRoute, toJvmRoute);
	}

	@Override
	public void responseReceived(JsonObject json) 
	{
		if(json.get(Protocol.START)!=null)
		{
			startTimer.cancel();
			heartbeatTimer = new Timer();
			heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
				public void run() {
					if(failResponsesCounter.get() == maxErrors)
					{
						failResponsesCounter.set(0);
						logger.info("restart heartbeat client because of not got responses on heartbeats");
						restartClient();
					}
					else
					{
						failResponsesCounter.incrementAndGet();
						client.sendPacket(Protocol.HEARTBEAT);
					}
				}
			}, 1000, heartbeatPeriod);
		}
		
		if(json.get(Protocol.STOP)!=null)
		{
			 new Thread(new Runnable() {
			        public void run() {
			        	client.stop();
			        }
			    }).start();
		}
		if(listener!=null)
			listener.responseReceived(json);		
	}

	@Override
	public void stopRequestReceived(MessageEvent e, JsonObject json) 
	{
		listener.stopRequestReceived(e, json);
	}
	
	public void stopClient(boolean isGracefully)
	{
		if(!isGracefully&&heartbeatTimer != null)
		{
			heartbeatTimer.cancel();
			client.sendPacket(Protocol.STOP);
		}
		else
		{
			client.sendPacket(Protocol.SHUTDOWN);
		}
	}

	public String getLbAddress() {
		return lbAddress;
	}

	public void setLbAddress(String lbAddress) {
		this.lbAddress = lbAddress;
	}

	public int getLbPort() {
		return lbPort;
	}

	public void setLbPort(int lbPort) {
		this.lbPort = lbPort;
	}

	public AtomicInteger getFailResponsesCounter() {
		return failResponsesCounter;
	}
}
