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

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class BandwidthMessages {

    private static String didsResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><SearchResult><ResultCount>5</ResultCount><TelephoneNumberDetailList>"
    + "<TelephoneNumberDetail><City>DORA</City><LATA>AREACODEHERE</LATA><RateCenter>DORA</RateCenter><State>AL</State><FullNumber>AREACODEHERE2355024</FullNumber><Tier>0</Tier><VendorId>49</VendorId>"
    +  "<VendorName>Bandwidth CLEC</VendorName></TelephoneNumberDetail><TelephoneNumberDetail><City>DORA</City><LATA>476</LATA><RateCenter>DORA</RateCenter><State>AL</State>"
    + "<FullNumber>AREACODEHERE2355025</FullNumber><Tier>0</Tier><VendorId>49</VendorId><VendorName>Bandwidth CLEC</VendorName></TelephoneNumberDetail><TelephoneNumberDetail>"
    + "<City>DORA</City><LATA>476</LATA><RateCenter>DORA      </RateCenter><State>AL</State><FullNumber>AREACODEHERE2355026</FullNumber><Tier>0</Tier><VendorId>49</VendorId>"
    + "<VendorName>Bandwidth CLEC</VendorName></TelephoneNumberDetail><TelephoneNumberDetail><City>DORA</City><LATA>476</LATA><RateCenter>DORA      </RateCenter>"
    + "<State>AL</State><FullNumber>AREACODEHERE2355027</FullNumber><Tier>0</Tier><VendorId>49</VendorId><VendorName>Bandwidth CLEC</VendorName></TelephoneNumberDetail>"
    + "<TelephoneNumberDetail><City>DORA</City><LATA>476</LATA><RateCenter>DORA      </RateCenter><State>AL</State><FullNumber>AREACODEHERE2355028</FullNumber><Tier>0</Tier>"
    + "<VendorId>49</VendorId><VendorName>Bandwidth CLEC</VendorName></TelephoneNumberDetail></TelephoneNumberDetailList></SearchResult>";

    private static String assignDidResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><OrderResponse><Order><CustomerOrderId>i-54321</CustomerOrderId>"
            + "<OrderCreateDate>2015-01-07T17:01:17.478Z</OrderCreateDate><BackOrderRequested>false</BackOrderRequested><id>577edbd0-89e2-487c-80c1-c5ff3d9b0c55</id>"
            + "<ExistingTelephoneNumberOrderType><TelephoneNumberList><TelephoneNumber>YOURDIDHERE</TelephoneNumber></TelephoneNumberList></ExistingTelephoneNumberOrderType>"
            + "<PartialAllowed>true</PartialAllowed><SiteId>1381</SiteId></Order><OrderStatus>RECEIVED</OrderStatus></OrderResponse>";
    
    private static String releaseDidResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?><DisconnectTelephoneNumberOrderResponse><orderRequest>"
            + "<Name>Disconnect Order For Number: YOURDIDHERE</Name><OrderCreateDate>2015-01-08T19:12:04.588Z</OrderCreateDate><id>d3a09a2c-b64c-4a6f-ae81-cbf54b26c78c</id>"
            + "<DisconnectTelephoneNumberOrderType><TelephoneNumberList><TelephoneNumber>YOURDIDHERE</TelephoneNumber></TelephoneNumberList><DisconnectMode>normal</DisconnectMode>"
            + "</DisconnectTelephoneNumberOrderType></orderRequest><OrderStatus>RECEIVED</OrderStatus></DisconnectTelephoneNumberOrderResponse>";

    public static String getDidsResponse(String areaCode) {
        return didsResponse.replaceAll("AREACODEHERE", areaCode);
    }
    public static String getAssignDidResponse(String did) {
        return assignDidResponse.replaceAll("YOURDIDHERE", did);
    }
    public static String getAssignDidResponse(String siteId, String did) {
        String result = assignDidResponse.replaceAll("YOURDIDHERE", did);
        result = result.replaceFirst("i-54321", siteId);
        return result;
    }
    public static String getReleaseDidResponse(String did) {
        return releaseDidResponse.replaceAll("YOURDIDHERE", did);
    }
}
