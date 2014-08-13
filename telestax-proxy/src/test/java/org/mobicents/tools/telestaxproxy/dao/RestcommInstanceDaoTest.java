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
package org.mobicents.tools.telestaxproxy.dao;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.RestcommInstance;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class RestcommInstanceDaoTest {

    private Logger logger = Logger.getLogger(RestcommInstanceDaoTest.class);
    DaoManager daoManager ;
    SqlSessionFactory sessionFactory;
    RestcommInstanceDaoManager instanceDao;
    
    @Before
    public void setup() throws Exception {
        daoManager = new DaoManager();
        daoManager.run();
        sessionFactory = daoManager.getSessionFactory();
        instanceDao = new RestcommInstanceDaoManager(sessionFactory);
    }
    
    @After
    public void cleanup() {
        daoManager = null;
        instanceDao = null;
    }
    
    @Test
    public void testDaoManager() throws Exception {
        assertTrue(daoManager.getSessionFactory()!=null);
    }
    
    @Test
    public void testAddRestcommInstance() throws Exception {
        String restcommId = "rest-12345";
        List<String> addresses = new ArrayList<String>();
        addresses.add("127.0.0.1:5080:udp");
        addresses.add("127.0.0.1:5080:tcp");
        addresses.add("127.0.0.1:5081:tls");
        addresses.add("127.0.0.1:5082:ws");
        RestcommInstance restcomm = new RestcommInstance(restcommId, addresses);
        
        instanceDao.addRestcommInstance(restcomm);
        
        RestcommInstance retrievedInstance = instanceDao.getInstanceById(restcommId);
        assertTrue(retrievedInstance != null);
        assertTrue(retrievedInstance.getId().equalsIgnoreCase(restcommId));
    }
}
