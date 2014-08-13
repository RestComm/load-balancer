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



import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.mobicents.tools.telestaxproxy.dao.PhoneNumberDaoManager;
import org.mobicents.tools.telestaxproxy.dao.RestcommInstanceDaoManager;
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.entities.request.ProxyRequest;
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.entities.responses.VoipInnovationAssignDidResponse;
import org.mobicents.tools.telestaxproxy.http.balancer.voipinnovation.entities.responses.VoipInnovationReleaseDidResponse;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.DidEntity;
import org.mobicents.tools.telestaxproxy.sip.balancer.entities.RestcommInstance;

import com.thoughtworks.xstream.XStream;
/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class VoipInnovationDispatcher {

    private static Logger logger = Logger.getLogger(VoipInnovationDispatcher.class);
    static enum RequestType { GetAvailablePhoneNumbersByAreaCode, AssignDid, IsValidDid, ReleaseDid };

    private String login;
    private String password;
    private String endpoint;
    private String uri;
    private XStream xstream;
    private RestcommInstanceDaoManager restcommInstanceManager;
    private PhoneNumberDaoManager phoneNumberManager;

    /**
     * @param login
     * @param password
     * @param endpoint
     * @param uri
     */
    public VoipInnovationDispatcher(String login, String password, String endpoint, String uri, RestcommInstanceDaoManager restcommInstanceManager, PhoneNumberDaoManager phoneNumberManager) {
        this.login = login;
        this.password = password;
        this.endpoint = endpoint;
        this.uri = uri;
        this.phoneNumberManager = phoneNumberManager;
        this.restcommInstanceManager = restcommInstanceManager;
        //        XStream xstream = new XStream();
        //        xstream.ignoreUnknownElements();
    }

    public HttpRequest patchHttpRequest(HttpRequest request) {
        String requestType = request.headers().get("RequestType");
        HttpRequest newRequest = new DefaultHttpRequest(request.getProtocolVersion(), request.getMethod(), uri);

        String body = getContent(request);

        try {
            body = URLDecoder.decode(body, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.error("There was a problem to decode the Request's content ",e);
        }

        if(logger.isDebugEnabled()) {
            logger.info("Patch Request with body: "+body);
        }

        body = body.replaceFirst("<login>.*</login>", "<login>"+login+"</login>");
        body = body.replaceFirst("<password>.*</password>", "<password>"+password+"</password>");

        body = body.replaceFirst("apidata=", "apidata=<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><!DOCTYPE request SYSTEM \"https://www.loginto.us/Voip/api2.pl\">");

        if(requestType.equalsIgnoreCase(RequestType.AssignDid.name())){
            body = body.replaceFirst("<endpointgroup>.*</endpointgroup>", "<endpointgroup>"+endpoint+"</endpointgroup>");
            newRequest.headers().set("RequestType", RequestType.AssignDid.name());
        } else if (requestType.equalsIgnoreCase(RequestType.IsValidDid.name())) {
            newRequest.headers().set("RequestType", RequestType.IsValidDid.name());
        } else if (requestType.equalsIgnoreCase(RequestType.GetAvailablePhoneNumbersByAreaCode.name())) {
            newRequest.headers().set("RequestType", RequestType.GetAvailablePhoneNumbersByAreaCode.name());
        } else if (requestType.equalsIgnoreCase(RequestType.ReleaseDid.name())) {
            newRequest.headers().set("RequestType", RequestType.ReleaseDid.name());
        } 

        ChannelBuffer newContent = null;
        newContent = ChannelBuffers.copiedBuffer(body, Charset.forName("UTF-8"));

        newRequest.setContent(newContent);
        newRequest.headers().set("Host", "backoffice.voipinnovations.com");
        //        newRequest.headers().set("Content-Type", "application/xml");
        newRequest.headers().set("Content-Type", "application/x-www-form-urlencoded");
        //That was one setting to make it work with VI. If the body length is wrong then the VI doesn't accept the request
        newRequest.headers().set("Content-Length", body.length());

        if (logger.isDebugEnabled()){
            logger.debug("****************** VI Request ********************************");
            logger.debug("VoipInnovation Request, original request: "+ request +"\nnew request: "+ newRequest+"\nand body: "+body);
            logger.debug("****************** VI Request ********************************");
        }
        return newRequest;
    }

    /**
     * @param response
     */
    public void processHttpResponse(HttpResponse response, ProxyRequest proxyRequest) {
        String body = getContent(response);

        if (proxyRequest != null && proxyRequest.getRequest() != null) {
            if (proxyRequest.getRequest().headers().get("RequestType").equals(RequestType.AssignDid.name())) {
                xstream = new XStream();
                xstream.ignoreUnknownElements();
                xstream.alias("response", VoipInnovationAssignDidResponse.class);
                xstream.processAnnotations(VoipInnovationAssignDidResponse.class);
                VoipInnovationAssignDidResponse viAssignResponse = (VoipInnovationAssignDidResponse) xstream.fromXML(body);
                String did = viAssignResponse.getTN();
                Integer statusCode = viAssignResponse.getStatusCode();
                if(did != null && statusCode == 100){
                    logger.info("Will store the new assignDID request to map for DID: "+did+" ,Restcomm instance: "+proxyRequest.getViRequest().getEndpointGroup());
                    RestcommInstance restcomm = new RestcommInstance(proxyRequest.getViRequest().getEndpointGroup(), proxyRequest.getRequest().headers().getAll("OutboundIntf"));
                    restcommInstanceManager.addRestcommInstance(restcomm);
                    DidEntity didEntity = new DidEntity();
                    didEntity.setDid(did);
                    didEntity.setRestcommInstance(restcomm.getId());
                    phoneNumberManager.addDid(didEntity);
                }
            } else if (proxyRequest.getRequest().headers().get("RequestType").equals(RequestType.ReleaseDid.name())) {
                xstream = new XStream();
                xstream.ignoreUnknownElements();
                xstream.alias("response", VoipInnovationReleaseDidResponse.class);
                xstream.processAnnotations(VoipInnovationReleaseDidResponse.class);
                VoipInnovationReleaseDidResponse viReleaseResponse = (VoipInnovationReleaseDidResponse) xstream.fromXML(body);
                String did = viReleaseResponse.getTN();
                Integer statusCode = viReleaseResponse.getStatusCode();
                if(did != null && statusCode == 100){
                    logger.info("Release DID request to VI was succesfully executed. Will now remove the DID: "+did+" ,from the map for Restcomm instance: "+proxyRequest.getViRequest().getEndpointGroup());
                    phoneNumberManager.removeDid(did);
                }
            }
        } else {
            logger.info("Either ProxyRequest is null or HttpRequest");
        }
    }

    private String getContent(HttpMessage message){
        byte[] bodyBytes = new byte[message.getContent().capacity()];
        message.getContent().getBytes(0, bodyBytes);
        return new String(bodyBytes);
    }
}
