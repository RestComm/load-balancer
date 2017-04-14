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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppServerHandler;
import com.cloudhopper.smpp.SmppServerSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppProcessingException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class DefaultSmppServerHandler implements SmppServerHandler {
	
	private static final Logger logger = Logger.getLogger(DefaultSmppServerHandler.class);

	CopyOnWriteArrayList<Long> invalidPasswordUsers = new CopyOnWriteArrayList<Long>();
	CopyOnWriteArrayList<Long> errorUsers = new CopyOnWriteArrayList<Long>();
	int smsNumber;
	AtomicInteger enqLinkNumber = new AtomicInteger(0);
	AtomicInteger responsesFromClient = new AtomicInteger(0);
	private AtomicInteger requestFromClientNumber = new AtomicInteger(0);
	String validPassword = "password";
    public AtomicInteger getEnqLinkNumber() {
		return enqLinkNumber;
	}

	public int getSmsNumber() {
		return smsNumber;
	}
	

    public AtomicInteger getResponsesFromClient() {
		return responsesFromClient;
	}

	public void resetCounters()
    {
    	smsNumber=0;
    	enqLinkNumber.set(0);
    }
	@SuppressWarnings("rawtypes")
	public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, final BaseBind bindRequest) throws SmppProcessingException 
	{
        if(sessionConfiguration.getPassword().equals("PasswordForSomeErrors"))
        	errorUsers.addIfAbsent(sessionId);
        else if(!sessionConfiguration.getPassword().equals(validPassword))
        	invalidPasswordUsers.addIfAbsent(sessionId);
     }

    public void sessionCreated(Long sessionId, SmppServerSession session, BaseBindResp preparedBindResponse) throws SmppProcessingException 
    {
    	if(invalidPasswordUsers.contains(sessionId))
    	{
    		preparedBindResponse.setCommandStatus(SmppConstants.STATUS_INVPASWD);
    		try {
				session.sendResponsePdu(preparedBindResponse);
			} catch (RecoverablePduException | UnrecoverablePduException | SmppChannelException | InterruptedException e) {
				logger.error("Exception : " + e);
			}
    	}
    	else if (errorUsers.contains(sessionId))
    	{
    		preparedBindResponse.setCommandStatus(SmppConstants.STATUS_INVDSTADR);
    		try {
				session.sendResponsePdu(preparedBindResponse);
			} catch (RecoverablePduException | UnrecoverablePduException | SmppChannelException | InterruptedException e) {
				logger.error("Exception : " + e);
			}
    	}
    	else
    	{
    		session.serverReady(new TestSmppSessionHandler());
    	}
     }

    public void sessionDestroyed(Long sessionId, SmppServerSession session) 
    {
        	smsNumber=session.getCounters().getRxSubmitSM().getRequest();
        	enqLinkNumber.addAndGet(session.getCounters().getRxEnquireLink().getRequest());
         	responsesFromClient.addAndGet(session.getCounters().getTxDataSM().getResponse());
    }
    
    public class TestSmppSessionHandler extends DefaultSmppSessionHandler 
    {
        @SuppressWarnings({ "rawtypes" })
		@Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
        	if(pduRequest.getCommandId() == SmppConstants.CMD_ID_SUBMIT_SM)
        		requestFromClientNumber.getAndIncrement();
            return pduRequest.createResponse();
        }
        @Override
        public void fireUnexpectedPduResponseReceived(PduResponse pduResponse) {
            logger.info("Server get response : " + pduResponse);
        } 
    }

	public AtomicInteger getRequestFromClientNumber() {
		return requestFromClientNumber;
	}

}
