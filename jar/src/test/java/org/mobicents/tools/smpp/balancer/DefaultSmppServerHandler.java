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

import java.util.concurrent.atomic.AtomicInteger;

import com.cloudhopper.smpp.SmppServerHandler;
import com.cloudhopper.smpp.SmppServerSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.type.SmppProcessingException;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class DefaultSmppServerHandler implements SmppServerHandler {

	AtomicInteger smsNumber = new AtomicInteger(0);
	AtomicInteger enqLinkNumber = new AtomicInteger(0);
    public AtomicInteger getEnqLinkNumber() {
		return enqLinkNumber;
	}

	public AtomicInteger getSmsNumber() {
		return smsNumber;
	}

    public void resetCounters()
    {
    	smsNumber.set(0);
    	enqLinkNumber.set(0);
    }
	@SuppressWarnings("rawtypes")
	public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, final BaseBind bindRequest) throws SmppProcessingException 
	{
        sessionConfiguration.setName("Application.SMPP." + sessionConfiguration.getSystemId());
     }

    public void sessionCreated(Long sessionId, SmppServerSession session, BaseBindResp preparedBindResponse) throws SmppProcessingException 
    {
        session.serverReady(new TestSmppSessionHandler());
     }

    public void sessionDestroyed(Long sessionId, SmppServerSession session) 
    {
        	smsNumber.addAndGet(session.getCounters().getRxSubmitSM().getRequest());
        	enqLinkNumber.addAndGet(session.getCounters().getRxEnquireLink().getRequest());
    }
    
    public class TestSmppSessionHandler extends DefaultSmppSessionHandler 
    {
        @SuppressWarnings({ "rawtypes" })
		@Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            return pduRequest.createResponse();
        }
    }
}
