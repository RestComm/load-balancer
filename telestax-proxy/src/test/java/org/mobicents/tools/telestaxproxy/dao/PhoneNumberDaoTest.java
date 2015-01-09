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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.common.ProvisionProvider;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.DidEntity;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.RestcommInstance;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class PhoneNumberDaoTest {

    private Logger logger = Logger.getLogger(this.getClass());
    DaoManager daoManager ;
    SqlSessionFactory sessionFactory;
    PhoneNumberDaoManager phoneNumberDao;
    RestcommInstanceDaoManager instanceDao;

    @Before
    public void setup() throws Exception {
        File mybatisConfFile = new File("extra-resources/mybatis.xml");
        daoManager = new DaoManager(mybatisConfFile);
        daoManager.run();
        sessionFactory = daoManager.getSessionFactory();
        phoneNumberDao = new PhoneNumberDaoManager(sessionFactory);
        instanceDao = new RestcommInstanceDaoManager(sessionFactory);
    }

    @After
    public void cleanup() {
        daoManager = null;
        phoneNumberDao = null;
        instanceDao = null;
    }

    @Test
    public void testAddAndRetrieveDid() {
        String didNumber = "123456789";
        String restcommId = "rest-1234";

        List<String> addresses = new ArrayList<String>();
        addresses.add("127.0.0.1:5080:udp");
        addresses.add("127.0.0.1:5080:tcp");
        addresses.add("127.0.0.1:5081:tls");
        addresses.add("127.0.0.1:5082:ws");
        RestcommInstance restcomm = new RestcommInstance(restcommId, addresses, ProvisionProvider.PROVIDER.UNKNOWN);

        DidEntity did = new DidEntity();
        did.setDid(didNumber);
        did.setRestcommInstance(restcommId);

        logger.info("Will add restcomm instance: "+restcomm);
        instanceDao.addRestcommInstance(restcomm);

        logger.info("Will add DID: "+did);
        phoneNumberDao.addDid(did);


        logger.info("Will now get the restcomm instance based on the the DID number :"+didNumber);
        RestcommInstance retrievedRestcomm = phoneNumberDao.getInstanceByDid(didNumber);
        assertTrue(retrievedRestcomm.getId().equalsIgnoreCase(restcommId));
    }
    
    @Test
    public void testAddAndRetrieveDidOneInterface() {
        String didNumber = "123456789";
        String restcommId = "rest-1234";

        List<String> addresses = new ArrayList<String>();
        addresses.add("127.0.0.1:5080:udp");
        RestcommInstance restcomm = new RestcommInstance(restcommId, addresses, ProvisionProvider.PROVIDER.UNKNOWN);

        DidEntity did = new DidEntity();
        did.setDid(didNumber);
        did.setRestcommInstance(restcommId);

        logger.info("Will add restcomm instance: "+restcomm);
        instanceDao.addRestcommInstance(restcomm);

        logger.info("Will add DID: "+did);
        phoneNumberDao.addDid(did);


        logger.info("Will now get the restcomm instance based on the the DID number :"+didNumber);
        RestcommInstance retrievedRestcomm = phoneNumberDao.getInstanceByDid(didNumber);
        assertTrue(retrievedRestcomm.getId().equalsIgnoreCase(restcommId));
    }
    
    @Test
    public void testAddAndRemoveDid() {
        String didNumber = "123456789";
        String restcommId = "rest-1234";

        List<String> addresses = new ArrayList<String>();
        addresses.add("127.0.0.1:5080:udp");
        addresses.add("127.0.0.1:5080:tcp");
        addresses.add("127.0.0.1:5081:tls");
        addresses.add("127.0.0.1:5082:ws");
        RestcommInstance restcomm = new RestcommInstance(restcommId, addresses, ProvisionProvider.PROVIDER.UNKNOWN);

        DidEntity did = new DidEntity();
        did.setDid(didNumber);
        did.setRestcommInstance(restcommId);

        logger.info("Will add restcomm instance: "+restcomm);
        instanceDao.addRestcommInstance(restcomm);

        logger.info("Will add DID: "+did);
        phoneNumberDao.addDid(did);


        logger.info("Will now remove the DID :"+didNumber);
        phoneNumberDao.removeDid(didNumber);
        assertTrue(!phoneNumberDao.didExists(didNumber));
    }
    
    @Test
    public void testGetDidListByRestcommInstance() {
        String restcommId = "rest-1234";

        List<String> addresses = new ArrayList<String>();
        addresses.add("127.0.0.1:5080:udp");
        addresses.add("127.0.0.1:5080:tcp");
        addresses.add("127.0.0.1:5081:tls");
        addresses.add("127.0.0.1:5082:ws");
        RestcommInstance restcomm = new RestcommInstance(restcommId, addresses, ProvisionProvider.PROVIDER.UNKNOWN);

        logger.info("Will add restcomm instance: "+restcomm);
        instanceDao.addRestcommInstance(restcomm);
        
        for (int i = 1; i < 5 ; i++) {
            String didNumber = "123456789"+i;
            DidEntity did = new DidEntity();
            did.setDid(didNumber);
            did.setRestcommInstance(restcommId);            
            logger.info("Will add DID: "+did);
            phoneNumberDao.addDid(did);
        }

        logger.info("Will now get the Did List based on Restcomm Instance :"+restcommId);
        ArrayList<String> dids = phoneNumberDao.getDidListByRestcommInstance(restcommId);
        assertTrue(dids.size()==4);
        assertTrue(dids.get(0).equalsIgnoreCase("1234567891"));
    }
    
    @Test
    public void testDidExists() {
        String restcommId = "rest-1234";

        List<String> addresses = new ArrayList<String>();
        addresses.add("127.0.0.1:5080:udp");
        addresses.add("127.0.0.1:5080:tcp");
        addresses.add("127.0.0.1:5081:tls");
        addresses.add("127.0.0.1:5082:ws");
        RestcommInstance restcomm = new RestcommInstance(restcommId, addresses, ProvisionProvider.PROVIDER.UNKNOWN);

        logger.info("Will add restcomm instance: "+restcomm);
        instanceDao.addRestcommInstance(restcomm);
        
        for (int i = 1; i < 5 ; i++) {
            String didNumber = "123456789"+i;
            DidEntity did = new DidEntity();
            did.setDid(didNumber);
            did.setRestcommInstance(restcommId);            
            logger.info("Will add DID: "+did);
            phoneNumberDao.addDid(did);
        }

        logger.info("Will now check if DID 1234567893 exists");
        assertTrue(phoneNumberDao.didExists("1234567893"));
    }
}
