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

import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.VoipInnovationStorage;
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.entities.responses.VoipInnovationAssignDidResponse;
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.entities.responses.VoipInnovationResponse;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.mapper.MapperWrapper;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class TestMain {
    
    public static void main(String[] args) {
        String response = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><!DOCTYPE response SYSTEM \"https://www.loginto.us/Voip/Back-Office\"><response id=\"cc20ec78105d48be95ddb9668c4eed4d\"><header><sessionid>0d0563a1dc1c49b8ffdea653e4a28ff1</sessionid></header><body><did><TN>4083416126</TN><status>Assigned to endpoint '11858' rewritten as '+14083416126' Tier 1</status><statuscode>100</statuscode><refid></refid><cnam>0</cnam><tier>1</tier></did></body></response>";
        
        XStream xstream = new XStream();
//        xstream.alias("response", VoipInnovationAssignDidResponse.class);
//        xstream.processAnnotations(VoipInnovationAssignDidResponse.class);

        xstream.ignoreUnknownElements();
        
        xstream.alias("response", VoipInnovationResponse.class);
        xstream.processAnnotations(VoipInnovationResponse.class);
        
//        xstream.useAttributeFor("myId", String.class);
//        
//        xstream.aliasAttribute("myId", "id");
//        VoipInnovationAssignDidResponse viResponse = (VoipInnovationAssignDidResponse) xstream.fromXML(response);
        VoipInnovationResponse viResponse = (VoipInnovationResponse) xstream.fromXML(response);
        System.out.println(viResponse.toString());

        xstream = new XStream();
        xstream.ignoreUnknownElements();
        xstream.alias("response", VoipInnovationAssignDidResponse.class);
        xstream.processAnnotations(VoipInnovationAssignDidResponse.class);
        
        VoipInnovationAssignDidResponse viAssignResponse = (VoipInnovationAssignDidResponse) xstream.fromXML(response);

        
        String did = viAssignResponse.getTN();
        Integer statusCode = viAssignResponse.getStatusCode();
        
        if(did != null && statusCode == 100){
            System.out.println("********** Will store the new assignDID request to map for DID: "+did+" ,Restcomm instance: ");//+proxyRequest.getViRequest().getEndpointGroup());
//            logger.info("Will store the new assignDID request to map for DID: "+did+" ,Restcomm instance: "+proxyRequest.getViRequest().getEndpointGroup());
//            VoipInnovationStorage.getStorage().assignDid(did, proxyRequest.getViRequest().getEndpointGroup());
        } else {
            System.out.println("Either DID was null (did!=null) ->: "+(did!=null)+" did was: "+did+" or statusCode was not 100: "+statusCode);
        }
        
        
    }
    
    

}
