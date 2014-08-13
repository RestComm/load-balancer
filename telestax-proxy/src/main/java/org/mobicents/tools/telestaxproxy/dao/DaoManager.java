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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mobicents.tools.telestaxproxy.dao.mappers.CallDetailsRecordMapper;
import org.mobicents.tools.telestaxproxy.dao.mappers.PhoneNumbersMapper;
import org.mobicents.tools.telestaxproxy.dao.mappers.RestcommInstanceMapper;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class DaoManager {
    private static SqlSessionFactory sessionFactory;
    private FileReader reader;

    public DaoManager() throws IOException {
        File confFile = Resources.getResourceAsFile("mybatis.xml");
        this.reader = new FileReader(confFile);
    }
    
    public DaoManager(File confFile) throws FileNotFoundException {
        this.reader = new FileReader(confFile);
    }

    public void run() throws Exception {
        sessionFactory = new SqlSessionFactoryBuilder().build(reader);
        Configuration conf = sessionFactory.getConfiguration();
        conf.getMapperRegistry().addMapper(RestcommInstanceMapper.class);
        conf.getMapperRegistry().addMapper(PhoneNumbersMapper.class);
        conf.getMapperRegistry().addMapper(CallDetailsRecordMapper.class);
    }

    public SqlSessionFactory getSessionFactory() throws Exception {
        if (sessionFactory==null)
            run();
      
        return sessionFactory;
    }
    
}