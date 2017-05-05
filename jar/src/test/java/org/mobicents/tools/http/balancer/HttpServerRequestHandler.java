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
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class HttpServerRequestHandler extends SimpleChannelUpstreamHandler {
	
	private static final String F_NAME = "fname";
	private static final String L_NAME = "lname";
	
	private static final int PARAM_NAME_IDX = 0;
	private static final int PARAM_VALUE_IDX = 1;
	
	private static final String AND_DELIMITER = "&";
	private static final String EQUAL_DELIMITER = "=";
	private AtomicInteger requestCount;
	
	private volatile boolean readingChunks;
	private HttpRequest request;
	private List <String> requests;
	private boolean chunk = false;
    
	public HttpServerRequestHandler(AtomicInteger requestCount,List <String> requests)
	{
		this.requestCount = requestCount;
		this.requests = requests;
	}
	

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		
		Thread.sleep(2000);
		Object msg = e.getMessage();
		if ((msg instanceof HttpRequest) || (msg instanceof DefaultHttpChunk)) {
			handle(ctx, e);
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {		

	}
	
	public void handle(ChannelHandlerContext ctx,MessageEvent e) throws IOException {
		if (!readingChunks) {
            request = (HttpRequest) e.getMessage();
            requests.add(request.getUri());
            if (request.isChunked())
            	readingChunks = true;
            else
            {   
            		requestCount.incrementAndGet();
            	try
            	{
            		String response = createResponseFromQueryParams(new URI(request.getUri()));
            		writeResponse(e, HttpResponseStatus.OK, response);
            	}
            	catch(Exception ex)
            	{
            		
            		
            	}        		 
            }
		}
		else 
		{
            HttpChunk chunk = (HttpChunk) e.getMessage();
            if (chunk.isLast())
                readingChunks = false;
        }
	}
	
	@SuppressWarnings("deprecation")
	private void writeResponse(MessageEvent e, HttpResponseStatus status, String responseString) {
        // Convert the response content to a ChannelBuffer.
		if(chunk)
			for(int i = 0; i < 1000; i++)
				responseString+="HOW MUCH IS THE FISH";
		
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(responseString, Charset.forName("UTF-8"));

        // Decide whether to close the connection or not.
		boolean close =
                HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.CONNECTION)) ||
                request.getProtocolVersion().equals(HttpVersion.HTTP_1_0) &&
                !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.CONNECTION));

        // Build the response object.
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        if(!chunk)
        	response.setContent(buf);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
        if(chunk)
        	response.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, "chunked");

        if(!chunk)
        	if (!close) {
            	// There's no need to add 'Content-Length' header
            	// if this is the last response.
            	response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(buf.readableBytes()));
        	}

        String cookieString = request.getHeader(HttpHeaders.Names.COOKIE);
        if (cookieString != null) {
            CookieDecoder cookieDecoder = new CookieDecoder();
            Set<Cookie> cookies = cookieDecoder.decode(cookieString);
            if(!cookies.isEmpty()) {
                // Reset the cookies if necessary.
                CookieEncoder cookieEncoder = new CookieEncoder(true);
                for (Cookie cookie : cookies) {
                    cookieEncoder.addCookie(cookie);
                }
                response.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder.encode());
            }
        }

        // Write the response.
        ChannelFuture future = e.getChannel().write(response);

        if(chunk)
        {
        	while(buf.readableBytes()>0)
        	{
        		int maxBytes=1000;
        		if(buf.readableBytes()<1000)
        			maxBytes=buf.readableBytes();
        	
        		HttpChunk currChunk=new DefaultHttpChunk(buf.readBytes(maxBytes));
        		future=e.getChannel().write(currChunk);
        	}
        
        	HttpChunk currChunk=new DefaultHttpChunk(buf);
    		future=e.getChannel().write(currChunk);
    	}
        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
	
	/**
	 * Creates the response from query params.
	 *
	 * @param uri the uri
	 * @return the string
	 */
	private String createResponseFromQueryParams(URI uri) {
		
		String fName = "";
		String lName = "";
		//Get the request query
		String query = uri.getQuery();
		if (query != null) {
			String[] queryParams = query.split(AND_DELIMITER);
			if (queryParams.length > 0) {
				for (String qParam : queryParams) {
					String[] param = qParam.split(EQUAL_DELIMITER);
					if (param.length > 0) {
						for (int i = 0; i < param.length; i++) {
							if (F_NAME.equalsIgnoreCase(param[PARAM_NAME_IDX])) {
								fName = param[PARAM_VALUE_IDX];
							}
							if (L_NAME.equalsIgnoreCase(param[PARAM_NAME_IDX])) {
								lName = param[PARAM_VALUE_IDX];
							}
						}
					}
				}
			}
		}
		
		return "Hello, " + fName + " " + lName;
	}
}
