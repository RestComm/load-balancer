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

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.EnquireLinkResp;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class DefaultSmppClientHandler extends DefaultSmppSessionHandler 
{
	AtomicInteger enqLinkNumber = new AtomicInteger(0);
    public AtomicInteger getEnqLinkNumber() 
    {
		return enqLinkNumber;
	}
	@Override
	public PduResponse firePduRequestReceived(@SuppressWarnings("rawtypes") PduRequest pduRequest) 
	{
		if (pduRequest.getCommandId() == SmppConstants.CMD_ID_ENQUIRE_LINK)
		{
			enqLinkNumber.incrementAndGet();
			EnquireLinkResp resp=new EnquireLinkResp();
			resp.setSequenceNumber(pduRequest.getSequenceNumber());
			return resp;
		}
		else
		{
			return null;
		}
	}
}
