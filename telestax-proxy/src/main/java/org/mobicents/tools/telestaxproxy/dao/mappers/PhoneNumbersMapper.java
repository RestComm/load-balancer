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
package org.mobicents.tools.telestaxproxy.dao.mappers;

import java.util.ArrayList;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.DidEntity;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public abstract interface PhoneNumbersMapper {

    public static final String INSERT = "INSERT INTO phone_numbers (did, restcomm_instance, dateCreated) "
            + "VALUES (#{did}, #{restcommInstance}, #{dateCreated})";
    public static final String SELECT_RESTCOMM_INSTANCE_BY_DID = "SELECT restcomm_instance FROM phone_numbers WHERE did=#{did}";
    public static final String DID_LIST_BY_RESTCOMM_INSTANCE = "SELECT did FROM phone_numbers WHERE restcomm_instance=#{restcommInstance}";
    public static final String REMOVE_DID = "DELETE FROM phone_numbers WHERE did=#{did}";
    public static final String DID_EXISTS = "SELECT COUNT(did) FROM phone_numbers WHERE did=#{did}";

    @Insert(INSERT)
    public abstract void addDid(DidEntity did);
    
    @Select(SELECT_RESTCOMM_INSTANCE_BY_DID)
    public abstract String getInstanceByDId(String did);
    
    @Delete(REMOVE_DID)
    public abstract void removeDid(String did);
    
    @Select(DID_LIST_BY_RESTCOMM_INSTANCE)
    public abstract ArrayList<String> getDidListByRestcommInstance(String restcommInstance);
    
    @Select(DID_EXISTS)
    public abstract Integer didExists(String did);
}