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
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.tuckey.web.filters.urlrewrite.Conf;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 *
 * @author Paul Tuckey
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */

public class BalancerUrlRewriteFilter {

	private static final Logger log = Logger.getLogger(BalancerUrlRewriteFilter.class.getCanonicalName());

	private BalancerUrlRewriter urlRewriter = null;

    public void init(BalancerRunner balancerRunner) throws ServletException 
    {
        log.info("filter init called");
        Conf conf = new BalancerConf(balancerRunner.getConfiguration().getHttpConfiguration().getUrlrewriteRule());
        checkConfLocal(conf);
    }
    private void checkConfLocal(Conf conf) {
        if (log.isDebugEnabled()) {
            if (conf.getRules() != null) {
                log.debug("inited with " + conf.getRules().size() + " rules");
            }
            log.debug("conf is " + (conf.isOk() ? "ok" : "NOT ok"));
        }
        if (conf.isOk() && conf.isEngineEnabled()) {
            urlRewriter = new BalancerUrlRewriter(conf);
            log.info("loaded (conf ok)");

        } else {
            if (!conf.isOk()) {
                log.error("Conffailed to load");
            }
            if (!conf.isEngineEnabled()) {
                log.error("Engine explicitly disabled in conf"); // not really an error but we want ot to show in logs
            }
            if (urlRewriter != null) {
                log.error("unloading existing conf");
                urlRewriter = null;
            }
        }
    }

    /**
     * The main method called for each request that this filter is mapped for.
     *
     * @param request  the request to filter
     * @throws IOException
     * @throws ServletException
     */
    public void doFilter(final HttpRequest httpRequest, MessageEvent e) throws IOException, ServletException  {
    	
    	HttpServletRequest servletRequest = new NettyHttpServletRequestAdaptor(httpRequest, e.getChannel());
        final HttpServletRequest hsRequest = (HttpServletRequest) servletRequest;
        if (urlRewriter != null) {
            String newUrl = urlRewriter.processRequest(hsRequest);
            if(!newUrl.equals(httpRequest.getUri()))
            {
            	if (log.isDebugEnabled())
            		log.debug("request rewrited from : [" + httpRequest.getUri() + "] to : ["+newUrl+"]");
            	httpRequest.setUri(newUrl);
            }
            else
            {
            	if (log.isDebugEnabled())
            		log.debug("request not rewrited : [" + httpRequest.getUri() + "]");
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("urlRewriter engine not loaded ignoring request (could be a conf file problem)");
            }
        }
    }
}

