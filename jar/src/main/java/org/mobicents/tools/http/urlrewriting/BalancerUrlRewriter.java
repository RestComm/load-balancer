/**
 * Copyright (c) 2005-2007, Paul Tuckey
 * All rights reserved.
 * ====================================================================
 * Licensed under the BSD License. Text as follows.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   - Neither the name tuckey.org nor the names of its contributors
 *     may be used to endorse or promote products derived from this
 *     software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 */
package org.mobicents.tools.http.urlrewriting;

import org.apache.log4j.Logger;
import org.mobicents.tools.http.balancer.HttpRequestHandler;
import org.tuckey.web.filters.urlrewrite.Conf;
import org.tuckey.web.filters.urlrewrite.UrlRewriter;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author Paul Tuckey
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class BalancerUrlRewriter extends UrlRewriter{

	private static final Logger log = Logger.getLogger(HttpRequestHandler.class.getCanonicalName());

    /**
     * The conf for this filter.
     */
    private Conf conf;

    public BalancerUrlRewriter(Conf conf) {
    	super(conf);
        this.conf = conf;
    }
    /**
     * The main method called for each request that this filter is mapped for.
     *
     * @param hsRequest The request to process.
     * @return returns true when response has been handled by url rewriter false when it hasn't.
     */
    public String processRequest(final HttpServletRequest hsRequest) throws IOException, ServletException {
        RuleChain chain = getNewChain(hsRequest);
        if (chain == null) 
        	return null;
        chain.doRules(hsRequest, null);
        return chain.getFinalUrl();
    }

    /**
     * Return the path within the web application for the given request.
     * <p>Detects include request URL if called within a RequestDispatcher include.
     */
    public String getPathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null) 
        	requestUri = "";
        String decodedRequestUri = decodeRequestString(request, requestUri);
        String contextPath = "";//getContextPath(request);
        String path;
        if (StringUtils.startsWithIgnoreCase(decodedRequestUri, contextPath) && !conf.isUseContext()) {
            // Normal case: URI contains context path.
            path = decodedRequestUri.substring(contextPath.length());

        } else if (!StringUtils.startsWithIgnoreCase(decodedRequestUri, contextPath) && conf.isUseContext()) {
            // add the context path on
            path = contextPath + decodedRequestUri;

        } else {
            path = decodedRequestUri;
        }
        return StringUtils.isBlank(path) ? "/" : path;
    }

    private RuleChain getNewChain(final HttpServletRequest hsRequest) {

        String originalUrl = getPathWithinApplication(hsRequest);
        if (originalUrl == null) {
            // for some reason the engine is not giving us the url
            // this isn't good
            log.debug("unable to fetch request uri from request.  This shouldn't happen, it may indicate that " +
                    "the web application server has a bug or that the request was not pased correctly.");
            return null;
        }
         // add the query string on uri (note, some web app containers do this)
        if (originalUrl != null && originalUrl.indexOf("?") == -1 && conf.isUseQueryString()) {
            String query = hsRequest.getQueryString();
            if (query != null) {
                query = query.trim();
                if (query.length() > 0) {
                    originalUrl = originalUrl + "?" + query;
                    log.debug("query string added");
                }
            }
        }

        if (!conf.isOk()) {
            // when conf cannot be loaded for some sort of error
            // continue as normal without looking at the non-existent rules
            log.debug("configuration is not ok.  not rewriting request.");
            return null;
        }

        final List rules = conf.getRules();
        if (rules.size() == 0) {
            // no rules defined
            log.debug("there are no rules setup.  not rewriting request.");
            return null;
        }

        return new RuleChain(this, originalUrl);
    }

    public Conf getConf() {
        return conf;
    }
}