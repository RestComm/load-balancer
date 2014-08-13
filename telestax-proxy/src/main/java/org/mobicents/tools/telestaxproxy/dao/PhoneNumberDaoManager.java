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

import java.util.ArrayList;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.log4j.Logger;
import org.mobicents.tools.telestaxproxy.dao.mappers.PhoneNumbersMapper;
import org.mobicents.tools.telestaxproxy.dao.mappers.RestcommInstanceMapper;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.DidEntity;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.RestcommInstance;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class PhoneNumberDaoManager {

    private Logger logger = Logger.getLogger(RestcommInstanceDaoManager.class);
    private SqlSessionFactory sessionFactory;
    private SqlSession session;

    public PhoneNumberDaoManager(SqlSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public synchronized void addDid(DidEntity did){
        synchronized (this) {
            session = sessionFactory.openSession();
            try{
                PhoneNumbersMapper didMapper = session.getMapper(PhoneNumbersMapper.class);
                didMapper.addDid(did);
                session.commit(true);
            } catch (Exception e){
                logger.error("Error while adding the DID : "+e);
            } finally {
                session.close();
            }
        }
    }

    public synchronized RestcommInstance getInstanceByDid(String did){
        synchronized (this) {
            RestcommInstance restcommInstance = null;
            session = sessionFactory.openSession();
            try{
                PhoneNumbersMapper didMapper = session.getMapper(PhoneNumbersMapper.class);
                RestcommInstanceMapper restcommMapper = session.getMapper(RestcommInstanceMapper.class);
                String restcommId = didMapper.getInstanceByDId(did);
                restcommInstance = restcommMapper.getInstanceById(restcommId);
                session.commit(true);
            } catch (Exception e){
                logger.error("Error while getting the Restcomm Instance: "+e);
            } finally {
                session.close();
            }
            return restcommInstance;
        }
    }

    public synchronized void removeDid(String did) {
        synchronized (this) {
            session = sessionFactory.openSession();
            try{
                PhoneNumbersMapper didMapper = session.getMapper(PhoneNumbersMapper.class);
                didMapper.removeDid(did);
                session.commit(true);
            } catch (Exception e){
                logger.error("Error while removing the DID : "+e);
            } finally {
                session.close();
            }
        }
    }

    public synchronized ArrayList<String> getDidListByRestcommInstance(String restcommInstance) {
        synchronized (this) {
            ArrayList<String> didList = new ArrayList<String>();
            session = sessionFactory.openSession();
            try{
                PhoneNumbersMapper didMapper = session.getMapper(PhoneNumbersMapper.class);
                didList = (ArrayList<String>)didMapper.getDidListByRestcommInstance(restcommInstance);
                session.commit(true);
            } catch (Exception e){
                logger.error("Error while trying to get a list of DID by Restcomm Instance: "+e);
            } finally {
                session.close();
            }
            return didList;
        }
    }
    
    public synchronized Boolean didExists(String did) {
        synchronized (this) {
            Integer size = null;
            session = sessionFactory.openSession();
            try{
                PhoneNumbersMapper didMapper = session.getMapper(PhoneNumbersMapper.class);
                size = didMapper.didExists(did);
                session.commit(true);
            } catch (Exception e){
                logger.error("Error checking if DID exists : "+e);
            } finally {
                session.close();
            }
            return size==1;
        }
    }
}
