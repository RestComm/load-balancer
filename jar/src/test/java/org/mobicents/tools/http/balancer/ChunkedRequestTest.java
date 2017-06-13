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

import static org.junit.Assert.assertEquals;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.tools.configuration.LoadBalancerConfiguration;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.smpp.balancer.ClientListener;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;


/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class ChunkedRequestTest
{
	private static BalancerRunner balancerRunner;
	private static HttpServer server;
	private static HttpUser user;
	private static ExecutorService executor;
	
	@BeforeClass
	public static void initialization() 
	{
		executor = Executors.newCachedThreadPool();
		server = new HttpServer(8080, 4444, 2222);
		server.start();	
		balancerRunner = new BalancerRunner();
		LoadBalancerConfiguration lbConfig = new LoadBalancerConfiguration();
		lbConfig.getSipConfiguration().getInternalLegConfiguration().setTcpPort(5065);
		lbConfig.getSipConfiguration().getExternalLegConfiguration().setTcpPort(5060);
		balancerRunner.start(lbConfig);
		try 
		{
			Thread.sleep(1000);
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
	}

	//tests http balancer
	@Test
    public void testHttpBalancer() 
    {  
		Locker locker = new Locker(1);
		user = new HttpUser(locker);
		user.start();
		locker.waitForClients();

		try 
		{
			Thread.sleep(1000);
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
		
		assertEquals(1,server.getRequstCount().get());
    }
	
	@AfterClass
	public static void finalization()
	{
		server.stop();
		
		balancerRunner.stop();
	}
	private class HttpUser extends Thread
	{
		int codeResponse;
		ClientListener listener;
		public HttpUser(ClientListener listener)
		{
		 this.listener = listener;
		}

		public void run()
		{
			try 
			{ 
				String responseString = ""; 
				for(int i = 0; i < 1000; i++)
					responseString+="HOW MUCH IS THE FISH";
			
				ChannelBuffer buf = ChannelBuffers.copiedBuffer(responseString, Charset.forName("UTF-8"));
				
				ClientBootstrap inboundBootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(executor, executor));
				inboundBootstrap.setPipelineFactory(new HttpClientPipelineFactory(balancerRunner, false));
				ChannelFuture future = inboundBootstrap.connect(new InetSocketAddress("127.0.0.1", 2080));
				future.awaitUninterruptibly();
				DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/app?fName=Konstantin&lName=Nosach");
				request.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
				request.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, "chunked");
				future.getChannel().write(request);
				
				
				
				while(buf.readableBytes()>0)
	        	{
	        		int maxBytes=1000;
	        		if(buf.readableBytes()<1000)
	        			maxBytes=buf.readableBytes();
	        	
	        		HttpChunk currChunk=new DefaultHttpChunk(buf.readBytes(maxBytes));
	        		future.getChannel().write(currChunk);
	        	}
	        
	        	HttpChunk currChunk=new DefaultHttpChunk(buf);
	        	future.getChannel().write(currChunk);
	        	
				
	        	Thread.sleep(3000);
	        	listener.clientCompleted();
			} 
			catch (Exception e) 
			{
				listener.clientCompleted();
				e.printStackTrace();
			}
		}
	}
	private class Locker implements ClientListener{
    	private Semaphore clientsSemaphore;
    	private Locker(int clients)
    	{
    		clientsSemaphore=new Semaphore(1-clients);
    	}
    	
		@Override
		public void clientCompleted() 
		{
			clientsSemaphore.release();
		}
    	public void waitForClients()
    	{
    		try
    		{
    			clientsSemaphore.acquire();
    		}
    		catch(InterruptedException ex)
    		{
    			
    		}
    	}
    }
	
}

