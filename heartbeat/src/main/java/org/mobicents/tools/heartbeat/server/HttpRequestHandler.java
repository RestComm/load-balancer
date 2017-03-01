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
package org.mobicents.tools.heartbeat.server;

import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.mobicents.tools.heartbeat.api.IListener;
import org.mobicents.tools.heartbeat.api.IServerListener;
import org.mobicents.tools.heartbeat.api.Protocol;
import org.mobicents.tools.heartbeat.interfaces.IClientListener;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class HttpRequestHandler extends SimpleChannelUpstreamHandler{
	
	private static final Logger logger = Logger.getLogger(HttpRequestHandler.class.getCanonicalName());
	
	private volatile HttpRequest request;
	private IListener listener;
	private JsonParser parser = new JsonParser();
	
	public HttpRequestHandler(IListener listener)
	{
		this.listener = listener;
	}
	
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception 
    {
    	request = (HttpRequest)e.getMessage();
    	JsonObject json = parser.parse(request.getContent().toString("UTF-8")).getAsJsonObject();
    	Entry<String, JsonElement> jsonEntry = json.entrySet().iterator().next();
    	String command =  jsonEntry.getKey();
    	if(listener instanceof IServerListener)
    	{
    		if (command.equals(Protocol.HEARTBEAT))
    			((IServerListener) listener).heartbeatRequestReceived(e, jsonEntry.getValue().getAsJsonObject());
    		else if(command.equals(Protocol.START))
        		((IServerListener) listener).startRequestReceived(e, jsonEntry.getValue().getAsJsonObject());
    		else if (command.equals(Protocol.SHUTDOWN))
    			((IServerListener) listener).shutdownRequestReceived(e, jsonEntry.getValue().getAsJsonObject());
    		else if (command.equals(Protocol.STOP))
    			((IServerListener) listener).stopRequestReceived(e, jsonEntry.getValue().getAsJsonObject());
    		else if (command.equals(Protocol.SWITCHOVER))
    			((IServerListener) listener).switchoverRequestReceived(e, jsonEntry.getValue().getAsJsonObject());
    		else
    			throw new RuntimeException("Incorrect command name!"); 
    	}
    	else if(listener instanceof IClientListener)
    	{
    		if (command.equals(Protocol.STOP))
    			((IClientListener) listener).stopRequestReceived(e, jsonEntry.getValue().getAsJsonObject());
    	}
    }
    

}
