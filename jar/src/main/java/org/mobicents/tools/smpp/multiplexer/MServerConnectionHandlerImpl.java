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

import org.apache.log4j.Logger;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import com.cloudhopper.smpp.pdu.Pdu;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class MServerConnectionHandlerImpl extends SimpleChannelHandler{

	private static final Logger logger = Logger.getLogger(MServerConnectionHandlerImpl.class);
	private MServerConnectionImpl listener;
	
	public MServerConnectionHandlerImpl(MServerConnectionImpl listener)
	{
		this.setListener(listener);
	}
	
	@Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) 
	{		 
		if (e.getMessage() instanceof Pdu) 
		{
            Pdu pdu = (Pdu)e.getMessage();
            this.getListener().packetReceived(pdu);
 	    }
    }
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
	{
		logIssue(ctx, e);
	}
	    
    public void logIssue(ChannelHandlerContext ctx, ExceptionEvent e) {
        logger.error("Exeption in Channel " + e.getChannel().getRemoteAddress(), e.getCause());
        e.getChannel().close();
    }

	public MServerConnectionImpl getListener() {
		return listener;
	}

	public void setListener(MServerConnectionImpl listener) {
		this.listener = listener;
	}
}
