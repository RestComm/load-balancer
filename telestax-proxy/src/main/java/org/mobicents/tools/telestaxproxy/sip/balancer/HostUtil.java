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
package org.mobicents.tools.telestaxproxy.sip.balancer;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class HostUtil {

    private static HostUtil instance;
    private HostUtil(){}
    public static HostUtil getInstance(){
        if (instance == null)
            instance = new HostUtil();
        return instance;
    }
    
    public String getHost(String address){
        URI uri = null;
        try {
            uri = new URI(address);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return uri.getHost();
    }

    public int getPort(String address){
        URI uri = null;
        try {
            uri = new URI(address);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        int port = uri.getPort();
        if (port == -1) {
            if(uri.getScheme().equalsIgnoreCase("https")){
                port = 443;
            } else {
                port = 80;
            }
        }
        return port;
    }
    
}
