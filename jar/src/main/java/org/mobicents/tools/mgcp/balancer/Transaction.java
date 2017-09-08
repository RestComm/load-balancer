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
package org.mobicents.tools.mgcp.balancer;

import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;
/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class Transaction {

	private Integer transactionId;
	private NotifiedEntity notifiedEntity;
	private Call call;
	
	public Transaction(Integer transactionId, NotifiedEntity notifiedEntity, Call call)
	{
		this(transactionId);
		this.notifiedEntity = notifiedEntity;
		this.call = call;
	}
	public Transaction(Integer transactionId)
	{
		this.transactionId = transactionId;
	}

	public Integer getTransactionId() {
		return transactionId;
	}

	public NotifiedEntity getNotifiedEntity() {
		return notifiedEntity;
	}
	
	public Call getCall() {
		return call;
	}

	@Override
    public int hashCode()
    { 
		return (transactionId/*+notifiedEntity*/).hashCode();
    }
	
	@Override
	public boolean equals(Object obj) 
	{
		if ((obj instanceof Transaction)&&(this.transactionId.equals(((Transaction) obj).getTransactionId()))) 
			return true;
		else 
			return false;
	}
	
	@Override
    public String toString() {
        return "transaction [" + transactionId+"] [" + notifiedEntity + "]";
    }

}
