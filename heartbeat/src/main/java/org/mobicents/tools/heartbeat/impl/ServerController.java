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

import org.apache.log4j.Logger;
import org.jboss.netty.channel.MessageEvent;
import org.mobicents.tools.heartbeat.interfaces.IServer;
import org.mobicents.tools.heartbeat.interfaces.IServerListener;
import org.mobicents.tools.heartbeat.interfaces.Protocol;

import com.google.gson.JsonObject;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class ServerController implements IServerListener{

	private static final Logger logger = Logger.getLogger(ServerController.class.getCanonicalName());
	
	private IServerListener listener;
	private IServer server;
	
	public ServerController(IServerListener listener, String serverAddress, int serverPort)
	{
		this.listener = listener;
		this.server = new Server(this, serverAddress, serverPort);
	}
	
	public void startServer()
	{
		server.start();
	}
	
	@Override
	public void responseReceived(JsonObject json) 
	{
		//if response shutdown then stop
		 if(json.get(Protocol.STOP)!=null)
		 {
			 new Thread(new Runnable() {
			        public void run() {
			        	server.stop();
			        }
			    }).start();
		 }
		;
		listener.responseReceived(json);
	}

	@Override
	public void startRequestReceived(MessageEvent e, JsonObject json) 
	{
		listener.startRequestReceived(e, json);
	}

	@Override
	public void heartbeatRequestReceived(MessageEvent e, JsonObject json) 
	{
		listener.heartbeatRequestReceived(e, json);
	}

	@Override
	public void shutdownRequestReceived(MessageEvent e, JsonObject json) 
	{
		listener.shutdownRequestReceived(e, json);
	}
	
	@Override
	public void stopRequestReceived(MessageEvent e, JsonObject json) 
	{
		listener.stopRequestReceived(e, json);	
	}
	
	public void sendPacket(String host, int port)
	{
		server.sendPacket(Protocol.STOP, host, port);
	}

	public void stopServer()
	{
		server.stop();
	}

	@Override
	public void switchoverRequestReceived(MessageEvent e, JsonObject json) 
	{
		listener.switchoverRequestReceived(e, json);
	}
}
