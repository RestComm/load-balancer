/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package org.mobicents.tools.http.balancer;

import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public final class DisableSSLCertificateCheckUtil 
{
	/**
	 * Trust manager that does not perform nay checks.
	 */
	private static class NullX509TrustManager implements X509TrustManager 
	{
		public void checkClientTrusted(X509Certificate[] chain, String authType)throws CertificateException 
		{
		}

		public void checkServerTrusted(X509Certificate[] chain, String authType)throws CertificateException 
		{
		}
		public X509Certificate[] getAcceptedIssuers() 
		{
			return new X509Certificate[0];
		}
	}

	/**
	 * Host name verifier that does not perform nay checks.
	 */
	private static class NullHostnameVerifier implements HostnameVerifier 
	{
		public boolean verify(String hostname, SSLSession session) 
		{
			return true;
		}
	}

	/**
	 * Disable trust checks for SSL connections.
	 */
	public static void disableChecks() throws NoSuchAlgorithmException,	KeyManagementException 
	{

		try 
		{
			new URL("https://0.0.0.0/").getContent();
		} 
		catch (IOException e) 
		{
			// This invocation will always fail, but it will register the
			// default SSL provider to the URL class.
		}
		SSLContext context = SSLContext.getInstance("SSLv3");
		TrustManager[] trustManagerArray = { new NullX509TrustManager() };
		context.init(null, trustManagerArray, null);

		HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
		HttpsURLConnection.setDefaultHostnameVerifier(new NullHostnameVerifier());
	}
}
