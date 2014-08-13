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
package org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.entities.request.ProxyRequest;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class VoipInnovationStorage {
    
    private Logger logger = Logger.getLogger(VoipInnovationStorage.class);
    
    private static VoipInnovationStorage instance;
//    //Map of Did - Restcomm instance
//    private ConcurrentHashMap<String, RestcommInstance> restcommDidMap;
    //Map of RequestId - HttpRequest. Used for AssignDid requests
    private ConcurrentHashMap<String, ProxyRequest> requestsMap;

    private VoipInnovationStorage(){
//        restcommDidMap = new ConcurrentHashMap<String, RestcommInstance>();
        requestsMap = new ConcurrentHashMap<String, ProxyRequest>();
    }
    
    public static VoipInnovationStorage getStorage() {
        if (instance == null)
            instance = new VoipInnovationStorage();
        return instance;
    }
    
//    public void assignDid(String did, RestcommInstance restcommInstance) {
//        logger.info("Storing DID: "+did+" to instance: "+restcommInstance.getId());
//        restcommDidMap.put(did, restcommInstance);
//    }
    
//    public RestcommInstance getRestcommInstanceByDid(String did) {
//        return restcommDidMap.get(did);
//    }
    
//    public ArrayList<String> getListOfDidByRestcommInstance(String restcommInstance){
//        ArrayList<String> dids = new ArrayList<String>();
//        for (String did: restcommDidMap.keySet()) {
//            if (restcommDidMap.get(did).equals(restcommInstance))
//                dids.add(did);
//        }
//        return dids;
//    }
    
//    public boolean didExists(String did) {
//        return restcommDidMap.containsKey(did);
//    }
    
//    public boolean releaseDid(String did) {
//        return (restcommDidMap.remove(did) != null);
//    }
    
    public void addRequestToMap(String id, ProxyRequest request) {
        requestsMap.put(id, request);
    }
    
    //Get and remove
    public ProxyRequest getProxyRequest(String id) {
        return requestsMap.remove(id);
    }
    
}
