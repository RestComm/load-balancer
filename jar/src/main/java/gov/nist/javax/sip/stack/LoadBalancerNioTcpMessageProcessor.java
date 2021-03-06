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
package gov.nist.javax.sip.stack;

import gov.nist.javax.sip.stack.NioTcpMessageProcessor;
import gov.nist.javax.sip.stack.SIPTransactionStack;

import java.net.InetAddress;

/**
 * Load balancer wrapper for NIO implementation for TCP
 * 
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 *
 */
public class LoadBalancerNioTcpMessageProcessor extends NioTcpMessageProcessor implements Statistic{

    public LoadBalancerNioTcpMessageProcessor(InetAddress ipAddress,  SIPTransactionStack sipStack, int port) 
    {
    	super(ipAddress, sipStack, port);
    }

    @Override
    public int getActiveSipConnections ()
    {
    	return messageChannels.size();
    }
}
