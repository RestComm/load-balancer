package org.mobicents.tools.http.urlrewriting;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.http.Cookie;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.*;

/**
 * @author code4crafter@gmail.com
 */
public class NettyHttpServletRequestAdaptor implements HttpServletRequest {

    private HttpRequest httpRequest;

    private Channel channel;

    private Cookie[] cookieCache;

    private QueryStringDecoder queryStringDecoder;

    private Map<String, String[]> parameterMap;

    private String characterEncoding;

    private Map<String,Object> attributes;

    public NettyHttpServletRequestAdaptor(HttpRequest httpRequest, Channel channel) {
        this.httpRequest = httpRequest;
        this.channel = channel;
        this.attributes = new HashMap<String, Object>();
    }

    public QueryStringDecoder getQueryStringDecoder() {
        if (queryStringDecoder == null) {
            queryStringDecoder = new QueryStringDecoder(httpRequest.getUri());
        }
        return queryStringDecoder;
    }

    @Override
    public String getAuthType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cookie[] getCookies() {
        if (cookieCache == null) {
            Set<org.jboss.netty.handler.codec.http.Cookie> cookies = new CookieDecoder().decode(HttpHeaders.Names.COOKIE);
            cookieCache = new Cookie[cookies.size()];
            NettyToServletCookieConvertor.convert(cookies).toArray(cookieCache);
        }
        return cookieCache;
    }

    @Override
    public long getDateHeader(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHeader(String name) {
        return httpRequest.headers().get(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return new EnumerationIterableAdaptor(httpRequest.headers().getAll(name));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return new EnumerationIterableAdaptor(httpRequest.headers().names());
    }

    @Override
    public int getIntHeader(String name) {
        return Integer.parseInt(httpRequest.headers().get(name));
    }

    @Override
    public String getMethod() {
        return httpRequest.getMethod().getName();
    }

    @Override
    public String getPathInfo() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPathTranslated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getContextPath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getQueryString() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRemoteUser() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUserInRole(String role) {
        return false;
    }

    @Override
    public Principal getUserPrincipal() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRequestedSessionId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRequestURI() {
        return httpRequest.getUri();
    }

    @Override
    public StringBuffer getRequestURL() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getServletPath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpSession getSession(boolean create) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpSession getSession() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }    

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return new EnumerationIterableAdaptor<String>(attributes.keySet());
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        this.characterEncoding = env;
    }

    @Override
    public int getContentLength() {
        return getIntHeader(HttpHeaders.Names.CONTENT_LENGTH);
    }

    @Override
    public String getContentType() {
        return getHeader(HttpHeaders.Names.CONTENT_TYPE);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new ChannelBufferServletInputStream(httpRequest.getContent());
    }

    @Override
    public String getParameter(String name) {
        if (getParameterMap().get(name) != null && getParameterMap().get(name).length > 0) {
            return getParameterMap().get(name)[0];
        }
        return null;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return new EnumerationIterableAdaptor<String>(getParameterMap().keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return getParameterMap().get(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        if (parameterMap == null) {
            parameterMap = new HashMap<String, String[]>(getQueryStringDecoder().getParameters().size());
            for (Map.Entry<String, List<String>> stringListEntry : getQueryStringDecoder().getParameters().entrySet()) {
                String[] strings = new String[stringListEntry.getValue().size()];
                parameterMap.put(stringListEntry.getKey(), stringListEntry.getValue().toArray(strings));
            }
        }
        return parameterMap;
    }

    @Override
    public String getProtocol() {
        return httpRequest.getProtocolVersion().getText();
    }

    @Override
    public String getScheme() {
        return httpRequest.getProtocolVersion().getProtocolName();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    @Override
    public void setAttribute(String name, Object o) {
        this.attributes.put(name,o);
    }

    @Override
    public void removeAttribute(String name) {
        this.attributes.remove(name);
    }

    @Override
    public Locale getLocale() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public String getRemoteAddr() {
        return ((InetSocketAddress)channel.getRemoteAddress()).getAddress().getHostAddress();
    }

    @Override
    public String getRemoteHost() {
        return ((InetSocketAddress)channel.getRemoteAddress()).getHostName();
    }

    @Override
    public int getRemotePort() {
        return ((InetSocketAddress)channel.getRemoteAddress()).getPort();
    }

    @Override
    public String getLocalName() {
        return ((InetSocketAddress)channel.getLocalAddress()).getHostName();
    }

    @Override
    public String getLocalAddr() {
        return ((InetSocketAddress)channel.getLocalAddress()).getAddress().getHostAddress();
    }

    @Override
    public int getLocalPort() {
        return ((InetSocketAddress)channel.getLocalAddress()).getPort();
    }

    @Override
    public String getServerName() {
        return getLocalName();
    }

    @Override
    public int getServerPort() {
        return getLocalPort();
    }    

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRealPath(String path) {
        throw new UnsupportedOperationException();
    }
}
