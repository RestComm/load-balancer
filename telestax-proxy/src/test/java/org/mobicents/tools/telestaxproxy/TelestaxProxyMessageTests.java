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
package org.mobicents.tools.telestaxproxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.BalancerRunner;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class TelestaxProxyMessageTests {

    private static Logger logger = Logger.getLogger(TelestaxProxyMessageTests.class);
    BalancerRunner balancer;
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090);

    private HttpClient restcomm;

    @Before
    public void setup() throws InterruptedException, IOException {
        balancer = new org.mobicents.tools.telestaxproxy.sip.balancer.BalancerRunner(); 
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
        properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
        properties.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT","false");
        properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "16");
        properties.setProperty("host", "127.0.0.1");
        properties.setProperty("internalPort", "5065");
        properties.setProperty("externalPort", "5060");
        properties.setProperty("earlyDialogWorstCase", "true");
        properties.setProperty("algorithmClass", "org.mobicents.tools.telestaxproxy.sip.balancer.TelestaxProxyAlgorithm");
        properties.setProperty("vi-login","username13");
        properties.setProperty("vi-password","password13");
        properties.setProperty("vi-endpoint", "131313");
        properties.setProperty("vi-uri", "http://127.0.0.1:8090/test");
        properties.setProperty("mybatis-config","extra-resources/mybatis.xml");
        balancer.start(properties);
        Thread.sleep(1000);
        logger.info("Balancer Started");
    }
    
    @After
    public void cleanup() {
        restcomm = null;
        balancer.stop();
        balancer = null;
    }

    @Test
    public void testGetDids() throws ClientProtocolException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("getDIDs"))
                .withRequestBody(containing("415"))
                .withRequestBody(containing("username13"))
                .withRequestBody(containing("password13"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(VoipInnovationMessages.getDidsResponse(requestId))));
        
        restcomm = new DefaultHttpClient();
        
        final StringBuilder buffer = new StringBuilder();
        buffer.append("<request id=\""+requestId+"\">");
        buffer.append(header());
        buffer.append("<body>");
        buffer.append("<requesttype>").append("getDIDs").append("</requesttype>");
        buffer.append("<item>");
        buffer.append("<npa>415</npa>");
        buffer.append("</item>");
        buffer.append("</body>");
        buffer.append("</request>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", "GetAvailablePhoneNumbersByAreaCode");
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statuscode>100</statuscode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
    }

    @Test
    public void testIsValidDid() throws ClientProtocolException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("415"))
                .withRequestBody(containing("username13"))
                .withRequestBody(containing("password13"))
                .withRequestBody(containing("4156902867"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(VoipInnovationMessages.getIsValidResponse(requestId))));
        
        restcomm = new DefaultHttpClient();
        
        final StringBuilder buffer = new StringBuilder();
        buffer.append("<request id=\""+requestId+"\">");
        buffer.append(header());
        buffer.append("<body>");
        buffer.append("<requesttype>").append("queryDID").append("</requesttype>");
        buffer.append("<item>");
        buffer.append("<did>").append("4156902867").append("</did>");
        buffer.append("</item>");
        buffer.append("</body>");
        buffer.append("</request>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", "IsValidDid");
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statusCode>100</statusCode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
        assertTrue(responseContent.contains("<tn>4156902867</tn>"));
    }

    @Test
    public void testAssignDid() throws ClientProtocolException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("username13"))
                .withRequestBody(containing("password13"))
                .withRequestBody(containing("4156902867"))
                .withRequestBody(containing("131313"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(VoipInnovationMessages.getAssignDidResponse(requestId))));
        
        restcomm = new DefaultHttpClient();
        
        final StringBuilder buffer = new StringBuilder();
        buffer.append("<request id=\""+requestId+"\">");
        buffer.append(header());
        buffer.append("<body>");
        buffer.append("<requesttype>").append("assignDID").append("</requesttype>");
        buffer.append("<item>");
        buffer.append("<did>").append("4156902867").append("</did>");
        buffer.append("<endpointgroup>").append("Restcomm_Instance_Id").append("</endpointgroup>");
        buffer.append("</item>");
        buffer.append("</body>");
        buffer.append("</request>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", "AssignDid");
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statuscode>100</statuscode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
        assertTrue(responseContent.contains("<TN>4156902867</TN>"));
    }
    
    @Test
    public void testReleaseDid() throws ClientProtocolException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("username13"))
                .withRequestBody(containing("password13"))
                .withRequestBody(containing("4156902867"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(VoipInnovationMessages.getReleaseDidResponse(requestId))));
        
        restcomm = new DefaultHttpClient();
        
        final StringBuilder buffer = new StringBuilder();
        buffer.append("<request id=\""+requestId+"\">");
        buffer.append(header());
        buffer.append("<body>");
        buffer.append("<requesttype>").append("releaseDID").append("</requesttype>");
        buffer.append("<item>");
        buffer.append("<did>").append("4156902867").append("</did>");
        buffer.append("</item>");
        buffer.append("</body>");
        buffer.append("</request>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", "ReleaseDid");
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statuscode>100</statuscode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
        assertTrue(responseContent.contains("<TN>4156902867</TN>"));
    }
    
    @Test
    public void testPing() throws ClientProtocolException, IOException, InterruptedException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("ping"))
                .withRequestBody(containing("username13"))
                .withRequestBody(containing("password13"))
//                .withRequestBody(containing("131313"))
                .willReturn(aResponse()
                        .withStatus(200)));
//                        .withHeader("Content-Type", "text/xml")
//                        .withBody(VoipInnovationMessages.getReleaseDidResponse(requestId))));
        
        restcomm = new DefaultHttpClient();
        
        final StringBuilder buffer = new StringBuilder();
        buffer.append("<request id=\""+requestId+"\">");
        buffer.append(header());
        buffer.append("<body>");
        buffer.append("<requesttype>").append("ping").append("</requesttype>");
        buffer.append("<item>");
        buffer.append("<endpointgroup>").append("Restcomm_Instance_Id").append("</endpointgroup>");
        buffer.append("</item>");
        buffer.append("</body>");
        buffer.append("</request>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", "Ping");
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        Thread.sleep(2000);
//        String responseContent = EntityUtils.toString(response.getEntity());
//        assertTrue(responseContent.contains("<statuscode>100</statuscode>"));
//        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
//        assertTrue(responseContent.contains("<TN>4156902867</TN>"));
    }

    private String header() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("<header><sender>");
        buffer.append("<login>restcomm</login>");
        buffer.append("<password>restcomm</password>");
        buffer.append("</sender></header>");
        return buffer.toString();
    }

}
