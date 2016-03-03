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

package org.mobicents.tools.smpp.balancer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.jboss.netty.buffer.ChannelBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.SmppServerHandler;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.pdu.Pdu;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class TestSmppServer extends DefaultSmppServer {

	private static final Logger logger = LoggerFactory.getLogger(TestSmppServer.class);
	
	public TestSmppServer(SmppServerConfiguration configuration, SmppServerHandler serverHandler, 
			ExecutorService executor,	ScheduledExecutorService monitorExecutor) {
		super(configuration, serverHandler, executor, monitorExecutor);
	}
	public void sendData(Pdu pdu) {
        try {
            // encode the pdu into a buffer
            ChannelBuffer buffer = this.getTranscoder().encode(pdu);

            this.getChannels().write(buffer);

        } catch (Exception e) {
            logger.error("Fatal exception thrown while attempting to send response PDU: {}", e);
        }
    }

}
