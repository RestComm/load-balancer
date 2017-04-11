package org.mobicents.tools.http.urlrewriting;

import javax.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yihua.huang@dianping.com
 */
public abstract class NettyToServletCookieConvertor {

    public static Cookie convert(org.jboss.netty.handler.codec.http.Cookie nettyCookie){
        Cookie servletCookie = new Cookie(nettyCookie.getName(),nettyCookie.getValue());
        servletCookie.setDomain(nettyCookie.getDomain());
        servletCookie.setMaxAge(nettyCookie.getMaxAge());
        servletCookie.setPath(nettyCookie.getPath());
        servletCookie.setSecure(nettyCookie.isSecure());
        servletCookie.setVersion(nettyCookie.getVersion());
        servletCookie.setComment(nettyCookie.getComment());
        return servletCookie;
    }

    public static List<Cookie> convert(Collection<org.jboss.netty.handler.codec.http.Cookie> nettyCookies){
        List<Cookie> servletCookies = new ArrayList<Cookie>(nettyCookies.size());
        for (org.jboss.netty.handler.codec.http.Cookie nettyCookie : nettyCookies) {
            servletCookies.add(convert(nettyCookie));
        }
        return servletCookies;
    }
}