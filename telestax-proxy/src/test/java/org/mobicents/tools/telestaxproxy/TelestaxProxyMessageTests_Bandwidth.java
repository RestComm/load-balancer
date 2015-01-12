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
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;
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
public class TelestaxProxyMessageTests_Bandwidth {

    private static Logger logger = Logger.getLogger(TelestaxProxyMessageTests_Bandwidth.class);
    private static BalancerRunner balancer;

    private HttpClient restcomm;

    @BeforeClass
    public static void beforeClass() throws Exception {
        logger.info("About to start LoadBalancer");
        balancer = new org.mobicents.tools.telestaxproxy.sip.balancer.BalancerRunner(); 
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
        properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
        // You need 16 for logging traces. 32 for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
        properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "64");
        properties.setProperty("host", "127.0.0.1");
        properties.setProperty("internalPort", "5065");
        properties.setProperty("externalPort", "5060");
        properties.setProperty("earlyDialogWorstCase", "true");
        properties.setProperty("algorithmClass", "org.mobicents.tools.telestaxproxy.sip.balancer.TelestaxProxyAlgorithm");
        properties.setProperty("vi-login","username13");
        properties.setProperty("vi-password","password13");
        properties.setProperty("vi-endpoint", "131313");
        properties.setProperty("vi-uri", "http://127.0.0.1:8090/test");
        properties.setProperty("extraServerNodes", "127.0.0.1:5090,127.0.0.1:5091,127.0.0.1:5092");
        properties.setProperty("performanceTestingMode", "true");
        properties.setProperty("mybatis-config","extra-resources/mybatis.xml");
        properties.setProperty("bw-login", "customer");
        properties.setProperty("bw-password", "password");
        properties.setProperty("bw-accountId","9500149");
        properties.setProperty("bw-siteId", "1381");
        properties.setProperty("bw-uri", "http://127.0.0.1:8090");
        properties.setProperty("blocked-values", "sipvicious,sipcli,friendly-scanner"); 
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
    public void testGetDids() throws ClientProtocolException, IOException, URISyntaxException {
        String areaCode = "415";
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(BandwidthMessages.getDidsResponse(areaCode)));
        server.play(8090);
        //        http://192.168.1.151:2080/v1.0/accounts/account1234/availableNumbers?areaCode=626&enableTNDetail=true&quantity=5

        URIBuilder builder = new URIBuilder("http://127.0.0.1:2080");
        builder.setPath("/v1.0/accounts/i-12345/availableNumbers");
        builder.addParameter("areaCode", areaCode);
        builder.addParameter("enableTNDetail", "true");
        builder.addParameter("quantity", "5");

        HttpGet get = new HttpGet();
        get.setURI(builder.build());

        get.addHeader("TelestaxProxy", "true");
        get.addHeader("RequestType", ProvisionProvider.REQUEST_TYPE.GETDIDS.toString());
        get.addHeader("OutboundIntf", "127.0.0.1:5080:udp");
        get.addHeader("Provider", "org.mobicents.servlet.restcomm.provisioning.number.bandwidth.BandwidthNumberProvisioningManager");


        final HttpResponse response = restcomm.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<FullNumber>"+areaCode+"2355024</FullNumber>"));
        server.shutdown();
    }

    @Test
    public void testAssignDid() throws ClientProtocolException, IOException {
        String did = "2052355024";

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(BandwidthMessages.getAssignDidResponse(did)));
        server.play(8090);

        final StringBuilder buffer = new StringBuilder();
        buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        buffer.append("<Order>");
        buffer.append("<BackOrderRequested>false</BackOrderRequested>");
        buffer.append("<Name>Order For Number: "+did+"</Name>");
        buffer.append("<SiteId>i-123456</SiteId>");
        buffer.append("<PartialAllowed>false</PartialAllowed>");
        buffer.append("<ExistingTelephoneNumberOrderType>");
        buffer.append("<TelephoneNumberList>");
        buffer.append("<TelephoneNumber>"+did+"</TelephoneNumber>");
        buffer.append("</TelephoneNumberList>");
        buffer.append("</ExistingTelephoneNumberOrderType>");
        buffer.append("</Order>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080/v1.0/accounts/i-123456/orders");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", ProvisionProvider.REQUEST_TYPE.ASSIGNDID.toString());
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");
        post.addHeader("Provider", "org.mobicents.servlet.restcomm.provisioning.number.bandwidth.BandwidthNumberProvisioningManager");

        HttpEntity entity = new ByteArrayEntity(body.getBytes("UTF-8"));
        post.setEntity(entity);


        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<TelephoneNumber>"+did+"</TelephoneNumber>"));
        server.shutdown();
    }

    @Test
    public void testReleaseDid() throws ClientProtocolException, IOException {
        String did = "2052355024";

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(BandwidthMessages.getReleaseDidResponse(did)));
        server.play(8090);

        final StringBuilder buffer = new StringBuilder();
        buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>");
        buffer.append("<DisconnectTelephoneNumberOrder>");
        buffer.append("<Name>Disconnect Order For Number: "+did+"</Name>");
        buffer.append("<DisconnectTelephoneNumberOrderType>");
        buffer.append("<TelephoneNumberList>");
        buffer.append("<TelephoneNumber>"+did+"</TelephoneNumber>");
        buffer.append("</TelephoneNumberList>");
        buffer.append("</DisconnectTelephoneNumberOrderType>");
        buffer.append("</DisconnectTelephoneNumberOrder>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", ProvisionProvider.REQUEST_TYPE.RELEASEDID.toString());
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");
        post.addHeader("Provider", "org.mobicents.servlet.restcomm.provisioning.number.bandwidth.BandwidthNumberProvisioningManager");

        HttpEntity entity = new ByteArrayEntity(body.getBytes("UTF-8"));
        post.setEntity(entity);

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<TelephoneNumber>"+did+"</TelephoneNumber>"));
        assertTrue(responseContent.contains("<DisconnectMode>normal</DisconnectMode>"));
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
        buffer.append("</item>");
        buffer.append("</body>");
        buffer.append("</request>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", ProvisionProvider.REQUEST_TYPE.PING.toString());
        post.addHeader("OutboundIntf", "127.0.0.1:5080:udp");
        post.addHeader("Provider", "org.mobicents.servlet.restcomm.provisioning.number.bandwidth.BandwidthNumberProvisioningManager");

        HttpEntity entity = new ByteArrayEntity(body.getBytes("UTF-8"));
        post.setEntity(entity);

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        server.shutdown();
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
