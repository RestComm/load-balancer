/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.tools.telestaxproxy.sip.balancer.entities;

import java.util.Date;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class DidEntity {

    private String did;
    private String restcommInstance;
    private Date dateCreated;

    public DidEntity(String did, String restcommInstance) {
        this.did = did;
        this.restcommInstance = restcommInstance;
        this.dateCreated = new Date();
    }
    
    public DidEntity(){
        dateCreated = new Date();
    }

    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public String getRestcommInstance() {
        return restcommInstance;
    }

    public void setRestcommInstance(String restcommInstance) {
        this.restcommInstance = restcommInstance;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }
    

    @Override
    public String toString() {
        return "<Did: "+did+" | Restcomm Instance: "+restcommInstance+" | Date Created: "+dateCreated+">";
    }
}
