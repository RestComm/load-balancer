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
public class VoipInnovationMessages {

    private static String didsResponse = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><!DOCTYPE response SYSTEM \"https://www.loginto.us/Voip/Back-Office\">"
            + "<response id=\"YOURREQUESTIDHERE\"><header><sessionid>d46146b4132d611304269388cffabb17</sessionid></header><body><search><name>npa = '415' </name>"
            + "<status>Results Found</status><statuscode>100</statuscode><state><name>CA</name><lata><name>722</name><rate_center><name>BELVEDERE</name><npa><name>415</name>"
            + "<nxx><name>690</name><tn tier=\"0\" t38=\"1\" cnamStorage=\"1\">4156902867</tn></nxx>"
            + "<nxx><name>691</name><tn tier=\"0\" t38=\"1\" cnamStorage=\"1\">4156914883</tn>"
            + "<tn tier=\"0\" t38=\"1\" cnamStorage=\"1\">4156914885</tn><tn tier=\"0\" t38=\"1\" cnamStorage=\"1\">4156914887</tn><tn tier=\"0\" t38=\"1\" cnamStorage=\"1\">4156914995</tn>"
            + "<tn tier=\"0\" t38=\"1\" cnamStorage=\"1\">4156914996</tn></nxx><nxx><name>797</name>"
            + "<tn tier=\"0\" t38=\"1\" cnamStorage=\"1\">4157977554</tn></nxx></npa></rate_center><rate_center><name>CORTEMADRA</name><npa><name>415</name><nxx><name>329</name>"
            + "<tn tier=\"777\" t38=\"0\" cnamStorage=\"0\">4153290373</tn></nxx></npa></rate_center></lata></state></search></body></response>";

    private static String isValidResponse = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><!DOCTYPE response SYSTEM \"https://www.loginto.us/Voip/Back-Office\">"
            + "<response id=\"YOURREQUESTIDHERE\"><header><sessionid>d03492b3976f8eebca97c559a9fa927e</sessionid></header><body>"
            + "<did><tn>4156902867</tn><status>Number currently Available</status><statusCode>100</statusCode></did></body></response>";

    private static String assignDidResponse = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><!DOCTYPE response SYSTEM \"https://www.loginto.us/Voip/Back-Office\">"
            + "<response id=\"YOURREQUESTIDHERE\"><header><sessionid>a13b72c3ca20dc2174f9fb964bbc0111</sessionid></header><body>"
            + "<did><TN>4156902867</TN><status>Assigned to endpoint '11858' rewritten as '+14156902867' Tier 0</status><statuscode>100</statuscode><refid></refid><cnam>0</cnam>"
            + "<tier>0</tier></did></body></response>";
    
    private static String releaseDidResponse = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><!DOCTYPE response SYSTEM \"https://www.loginto.us/Voip/Back-Office\">"
            + "<response id=\"YOURREQUESTIDHERE\"><header><sessionid>fcb4bae9401df4d88bbb1f37f55042b9</sessionid></header><body><did><TN>4156902867</TN>"
            + "<status>Released</status><statuscode>100</statuscode></did></body></response>";

    public static String getDidsResponse(String requestId) {
        return didsResponse.replaceFirst("YOURREQUESTIDHERE", requestId);
    }
    public static String getIsValidResponse(String requestId) {
        return isValidResponse.replaceFirst("YOURREQUESTIDHERE", requestId);
    }
    public static String getAssignDidResponse(String requestId) {
        return assignDidResponse.replaceFirst("YOURREQUESTIDHERE", requestId);
    }
    public static String getAssignDidResponse(String requestId, String did) {
        String result = assignDidResponse.replaceFirst("YOURREQUESTIDHERE", requestId);
        result = result.replaceAll("4156902867", did);
        return result;
    }
    public static String getReleaseDidResponse(String requestId) {
        return releaseDidResponse.replaceFirst("YOURREQUESTIDHERE", requestId);
    }
}
