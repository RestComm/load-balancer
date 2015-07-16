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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.common.ProvisionProvider;
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
        File mybatisConfFile = new File("extra-resources/mybatis.xml");
        daoManager = new DaoManager(mybatisConfFile);
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
        RestcommInstance restcomm = new RestcommInstance(restcommId, addresses, ProvisionProvider.PROVIDER.UNKNOWN);
        
        instanceDao.addRestcommInstance(restcomm);
        
        RestcommInstance retrievedInstance = instanceDao.getInstanceById(restcommId);
        assertTrue(retrievedInstance != null);
        assertTrue(retrievedInstance.getId().equalsIgnoreCase(restcommId));
    }
    
    @Test
    public void testUpdateRestcommInstance() throws Exception {
        String restcommId = "rest-12345";
        List<String> addresses = new ArrayList<String>();
        addresses.add("127.0.0.1:5080:udp");
        addresses.add("127.0.0.1:5080:tcp");
        addresses.add("127.0.0.1:5081:tls");
        addresses.add("127.0.0.1:5082:ws");
        RestcommInstance restcomm = new RestcommInstance(restcommId, addresses, ProvisionProvider.PROVIDER.UNKNOWN);
        
        instanceDao.addRestcommInstance(restcomm);
        
        RestcommInstance retrievedInstance = instanceDao.getInstanceById(restcommId);
        assertTrue(retrievedInstance != null);
        assertTrue(retrievedInstance.getId().equalsIgnoreCase(restcommId));
        assertTrue(retrievedInstance.getUdpInterface().equalsIgnoreCase("127.0.0.1:5080"));
        assertTrue(retrievedInstance.getTcpInterface().equalsIgnoreCase("127.0.0.1:5080"));
        assertTrue(retrievedInstance.getTlsInterface().equalsIgnoreCase("127.0.0.1:5081"));
        assertTrue(retrievedInstance.getWsInterface().equalsIgnoreCase("127.0.0.1:5082"));
        
        List<String> newAddresses = new ArrayList<String>();
        addresses.add("192.168.1.70:5080:udp");
        addresses.add("192.168.1.70:5080:tcp");
        addresses.add("192.168.1.70:5081:tls");
        addresses.add("192.168.1.70:5082:ws");
        RestcommInstance updatedRestcomm = new RestcommInstance(restcommId, addresses, ProvisionProvider.PROVIDER.UNKNOWN);
        
        instanceDao.addRestcommInstance(updatedRestcomm);
        
        retrievedInstance = instanceDao.getInstanceById(restcommId);
        assertTrue(retrievedInstance != null);
        assertTrue(retrievedInstance.getId().equalsIgnoreCase(restcommId));
        assertTrue(retrievedInstance.getUdpInterface().equalsIgnoreCase("192.168.1.70:5080"));
        assertTrue(retrievedInstance.getTcpInterface().equalsIgnoreCase("192.168.1.70:5080"));
        assertTrue(retrievedInstance.getTlsInterface().equalsIgnoreCase("192.168.1.70:5081"));
        assertTrue(retrievedInstance.getWsInterface().equalsIgnoreCase("192.168.1.70:5082"));        
    }
    
    @Test //https://telestax.atlassian.net/browse/SPP-11
    public void testMovePublicIpAddress() throws Exception {
        String restcommId = "rest-12345";
        String publicIp = "10.10.10.10";
        List<String> addresses = new ArrayList<String>();
        addresses.add("127.0.0.1:5080:udp");
        addresses.add("127.0.0.1:5080:tcp");
        addresses.add("127.0.0.1:5081:tls");
        addresses.add("127.0.0.1:5082:ws");
        RestcommInstance restcomm = new RestcommInstance(restcommId, addresses, ProvisionProvider.PROVIDER.UNKNOWN, publicIp);
        
        instanceDao.addRestcommInstance(restcomm);
        
        RestcommInstance retrievedInstance = instanceDao.getInstanceById(restcommId);
        assertTrue(retrievedInstance != null);
        assertTrue(retrievedInstance.getId().equalsIgnoreCase(restcommId));
        assertTrue(retrievedInstance.getUdpInterface().equalsIgnoreCase("127.0.0.1:5080"));
        assertTrue(retrievedInstance.getTcpInterface().equalsIgnoreCase("127.0.0.1:5080"));
        assertTrue(retrievedInstance.getTlsInterface().equalsIgnoreCase("127.0.0.1:5081"));
        assertTrue(retrievedInstance.getWsInterface().equalsIgnoreCase("127.0.0.1:5082"));
        assertTrue(retrievedInstance.getPublicIpAddress().equalsIgnoreCase(publicIp));
        
        logger.info("************ Preparing second instance with the same public ip address");
        
        String newRestcommId = "12345-rest";
        List<String> newAddresses = new ArrayList<String>();
        newAddresses.add("192.168.1.70:5080:udp");
        newAddresses.add("192.168.1.70:5080:tcp");
        newAddresses.add("192.168.1.70:5081:tls");
        newAddresses.add("192.168.1.70:5082:ws");
        RestcommInstance updatedRestcomm = new RestcommInstance(newRestcommId, newAddresses, ProvisionProvider.PROVIDER.UNKNOWN, publicIp);
        
        instanceDao.addRestcommInstance(updatedRestcomm);
        RestcommInstance newRetrievedInstance = instanceDao.getInstanceById(newRestcommId);
        assertTrue(newRetrievedInstance != null);
        
        assertTrue(instanceDao.getInstanceByPublicIpAddress(publicIp).getId().equalsIgnoreCase(newRestcommId));
        
        assertTrue(newRetrievedInstance.getId().equalsIgnoreCase(newRestcommId));
        assertTrue(newRetrievedInstance.getUdpInterface().equalsIgnoreCase("192.168.1.70:5080"));
        assertTrue(newRetrievedInstance.getTcpInterface().equalsIgnoreCase("192.168.1.70:5080"));
        assertTrue(newRetrievedInstance.getTlsInterface().equalsIgnoreCase("192.168.1.70:5081"));
        assertTrue(newRetrievedInstance.getWsInterface().equalsIgnoreCase("192.168.1.70:5082"));
        assertTrue(newRetrievedInstance.getPublicIpAddress().equalsIgnoreCase(publicIp));
    }
}
