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
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.telestaxproxy.http.balancer.provision.common.ProvisionProvider;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class TelestaxProxyMessageTests_VoipInnovation {

    private static Logger logger = Logger.getLogger(TelestaxProxyMessageTests_VoipInnovation.class);
    private static BalancerRunner balancer;
    
    private HttpClient restcomm;

    @BeforeClass
    public static void beforeClass() throws InterruptedException {
        logger.info("Starting LoadBalancer");
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
    
    @AfterClass
    public static void afterClass() {
        balancer.stop();
        balancer = null;
    }
    
    @Before
    public void setup() throws InterruptedException, IOException {
        restcomm = HttpClientBuilder.create().build();
    }
    
    @After
    public void cleanup() {
        restcomm = null;
    }

    @Test
    public void testGetDids() throws ClientProtocolException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(VoipInnovationMessages.getDidsResponse(requestId)));
        server.play(8090);
                
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
        post.addHeader("RequestType", ProvisionProvider.REQUEST_TYPE.GETDIDS.name());
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");
        post.addHeader("Provider", "org.mobicents.servlet.restcomm.provisioning.number.vi.VoIPInnovationsNumberProvisioningManager");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statuscode>100</statuscode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
        server.shutdown();
    }

    @Test
    public void testIsValidDid() throws ClientProtocolException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(VoipInnovationMessages.getIsValidResponse(requestId)));
        server.play(8090);
        
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
        post.addHeader("RequestType", ProvisionProvider.REQUEST_TYPE.QUERYDID.name());
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");
        post.addHeader("Provider", "org.mobicents.servlet.restcomm.provisioning.number.vi.VoIPInnovationsNumberProvisioningManager");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statusCode>100</statusCode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
        assertTrue(responseContent.contains("<tn>4156902867</tn>"));
        server.shutdown();
        
    }

    @Test
    public void testAssignDid() throws ClientProtocolException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(VoipInnovationMessages.getAssignDidResponse(requestId)));
        server.play(8090);
        
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
        post.addHeader("RequestType", ProvisionProvider.REQUEST_TYPE.ASSIGNDID.name());
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");
        post.addHeader("Provider", "org.mobicents.servlet.restcomm.provisioning.number.vi.VoIPInnovationsNumberProvisioningManager");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statuscode>100</statuscode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
        assertTrue(responseContent.contains("<TN>4156902867</TN>"));
        server.shutdown();
    }
    
    @Test
    public void testReleaseDid() throws ClientProtocolException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(VoipInnovationMessages.getReleaseDidResponse(requestId)));
        server.play(8090);

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
        post.addHeader("RequestType", ProvisionProvider.REQUEST_TYPE.RELEASEDID.name());
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");
        post.addHeader("Provider", "org.mobicents.servlet.restcomm.provisioning.number.vi.VoIPInnovationsNumberProvisioningManager");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statuscode>100</statuscode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
        assertTrue(responseContent.contains("<TN>4156902867</TN>"));
        server.shutdown();
    }
    
    @Test
    public void testPing() throws ClientProtocolException, IOException, InterruptedException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setStatus("200"));
        server.play(8090);
        
        final StringBuilder buffer = new StringBuilder();
        buffer.append("<request id=\""+requestId+"\">");
        buffer.append(header());
        buffer.append("<body>");
        buffer.append("<requesttype>").append("ping").append("</requesttype>");
        buffer.append("<item>");
        buffer.append("<endpointgroup>").append("Restcomm_Instance_Id").append("</endpointgroup>");
        buffer.append("<provider>").append("org.mobicents.servlet.restcomm.provisioning.number.vi.VoIPInnovationsNumberProvisioningManager").append("</provider>");
        buffer.append("</item>");
        buffer.append("</body>");
        buffer.append("</request>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", ProvisionProvider.REQUEST_TYPE.PING.name());
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");
        post.addHeader("Provider", "org.mobicents.servlet.restcomm.provisioning.number.vi.VoIPInnovationsNumberProvisioningManager");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        server.shutdown();
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
