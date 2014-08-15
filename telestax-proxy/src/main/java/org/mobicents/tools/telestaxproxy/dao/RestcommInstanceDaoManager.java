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

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.log4j.Logger;
import org.mobicents.tools.telestaxproxy.dao.mappers.RestcommInstanceMapper;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.RestcommInstance;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class RestcommInstanceDaoManager {

    private Logger logger = Logger.getLogger(RestcommInstanceDaoManager.class);
    private SqlSessionFactory sessionFactory;
    private SqlSession session;

    public RestcommInstanceDaoManager(SqlSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public synchronized void addRestcommInstance(RestcommInstance restcommInstance) {
        synchronized(this){
            session = sessionFactory.openSession();
            try{
                RestcommInstanceMapper restcommMapper = session.getMapper(RestcommInstanceMapper.class);
                RestcommInstance restcomm = (RestcommInstance)restcommMapper.getInstanceById(restcommInstance.getId());
                if(restcomm == null) {
                    restcommMapper.addRestcommInstance(restcommInstance);
                } else {
                    restcommMapper.updateRestcommInstance(restcommInstance);
                }
                session.commit(true);
            } catch (Exception e){
                logger.error("Error while adding the RestcommInstance : "+e);
            } finally {
                session.close();
            }
        }
    }

    public RestcommInstance getInstanceById(String id) {
        synchronized(this){
            RestcommInstance restcomm = null;
            session = sessionFactory.openSession();
            try{
                RestcommInstanceMapper restcommMapper = session.getMapper(RestcommInstanceMapper.class);
                restcomm = (RestcommInstance)restcommMapper.getInstanceById(id);
                session.commit(true);
            } catch (Exception e){
                logger.error("Error while adding the RestcommInstance : "+e);
            } finally {
                session.close();
            }
            return restcomm;
        }
    }

}
