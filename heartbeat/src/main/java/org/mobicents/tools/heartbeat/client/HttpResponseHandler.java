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
package org.mobicents.tools.heartbeat.client;

import org.apache.log4j.Logger;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.mobicents.tools.heartbeat.impl.ClientController;
import org.mobicents.tools.heartbeat.interfaces.IClientListener;
import org.mobicents.tools.heartbeat.interfaces.IListener;
import org.mobicents.tools.heartbeat.interfaces.IServerListener;
import org.mobicents.tools.heartbeat.interfaces.Protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class HttpResponseHandler extends SimpleChannelUpstreamHandler{
	
	private static final Logger logger = Logger.getLogger(HttpResponseHandler.class.getCanonicalName());
	
	private volatile HttpResponse response;
	private IListener listener;
	private JsonParser parser = new JsonParser();
	
	public HttpResponseHandler(IListener listener)
	{
		this.listener = listener;
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception 
	{
		response = (HttpResponse) e.getMessage();
		logger.debug("Client got response: " + response.getContent().toString("UTF-8"));
		JsonObject json = parser.parse(response.getContent().toString("UTF-8")).getAsJsonObject();
		if(listener instanceof IClientListener)
		{
			if(json.get(Protocol.HEARTBEAT)!=null&&json.get(Protocol.HEARTBEAT).toString().replace("\"","" ).equals(Protocol.OK))
			{
				((ClientController)listener).getFailResponsesCounter().decrementAndGet();
			}
			((IClientListener)listener).responseReceived(json);
		}else if(listener instanceof IServerListener)
		{
			logger.debug("Server got response: : " + response.getContent().toString("UTF-8"));
			((IServerListener)listener).responseReceived(json);
		}
		else throw new RuntimeException("Incorrect type of listener");
	}
	
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception
	{
		logger.error(e.getCause().getMessage());
	}
}
