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

import org.tuckey.web.filters.urlrewrite.CatchElem;
import org.tuckey.web.filters.urlrewrite.ClassRule;
import org.tuckey.web.filters.urlrewrite.Condition;
import org.tuckey.web.filters.urlrewrite.Conf;
import org.tuckey.web.filters.urlrewrite.NormalRule;
import org.tuckey.web.filters.urlrewrite.OutboundRule;
import org.tuckey.web.filters.urlrewrite.Rule;
import org.tuckey.web.filters.urlrewrite.RuleBase;
import org.tuckey.web.filters.urlrewrite.Run;
import org.tuckey.web.filters.urlrewrite.Runnable;
import org.tuckey.web.filters.urlrewrite.SetAttribute;
import org.tuckey.web.filters.urlrewrite.gzip.GzipFilter;
import org.tuckey.web.filters.urlrewrite.utils.Log;
import org.tuckey.web.filters.urlrewrite.utils.ModRewriteConfLoader;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import javax.servlet.ServletContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Configuration object for urlrewrite filter.
 *
 * @author Paul Tuckey
 * @author Konstantin Nosach (kostyantyn.nosach@telestax.com)
 */
public class BalancerConf extends Conf{

    private static Log log = Log.getLog(BalancerConf.class);

    private final List errors = new ArrayList();
    private final List rules = new ArrayList(50);
    private final List catchElems = new ArrayList(10);
    private List outboundRules = new ArrayList(50);
    private boolean ok = false;
    private Date loadedDate = null;
    private int ruleIdCounter = 0;
    private int outboundRuleIdCounter = 0;

    protected boolean useQueryString;
    protected boolean useContext;

    private static final String NONE_DECODE_USING = "null";
    private static final String HEADER_DECODE_USING = "header";
    private static final String DEFAULT_DECODE_USING = "header,utf-8";

    protected String decodeUsing = DEFAULT_DECODE_USING;
    private boolean decodeUsingEncodingHeader;

    protected String defaultMatchType = null;

    private ServletContext context;
    private boolean docProcessed = false;
    private boolean engineEnabled = true;

    public BalancerConf(Document doc) {
    	processConfDoc(doc);
        if (docProcessed) initialise();
        loadedDate = new Date();
    }

    protected void loadModRewriteStyle(InputStream inputStream) {
        ModRewriteConfLoader loader = new ModRewriteConfLoader();
        try {
            loader.process(inputStream, this);
            docProcessed = true;
        } catch (IOException e) {
            addError("Exception loading conf " + " " + e.getMessage(), e);
        }
    }

    /**
     * Process dom document and populate Conf object.
     * <p/>
     * Note, protected so that is can be extended.
     */
    protected synchronized void processConfDoc(Document doc) {
        Element rootElement = doc.getDocumentElement();

        if ("true".equalsIgnoreCase(getAttrValue(rootElement, "use-query-string"))) setUseQueryString(true);
        if ("true".equalsIgnoreCase(getAttrValue(rootElement, "use-context"))) {
            log.debug("use-context set to true");
            setUseContext(true);
        }
        setDecodeUsing(getAttrValue(rootElement, "decode-using"));
        setDefaultMatchType(getAttrValue(rootElement, "default-match-type"));

        NodeList rootElementList = rootElement.getChildNodes();
        for (int i = 0; i < rootElementList.getLength(); i++) {
            Node node = rootElementList.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE &&
                    ((Element) node).getTagName().equals("rule")) {
                Element ruleElement = (Element) node;
                // we have a rule node
                NormalRule rule = new NormalRule();

                processRuleBasics(ruleElement, rule);
                procesConditions(ruleElement, rule);
                processRuns(ruleElement, rule);

                Node toNode = ruleElement.getElementsByTagName("to").item(0);
                rule.setTo(getNodeValue(toNode));
                rule.setToType(getAttrValue(toNode, "type"));
                rule.setToContextStr(getAttrValue(toNode, "context"));
                rule.setToLast(getAttrValue(toNode, "last"));
                rule.setQueryStringAppend(getAttrValue(toNode, "qsappend"));
                if ("true".equalsIgnoreCase(getAttrValue(toNode, "encode"))) rule.setEncodeToUrl(true);

                processSetAttributes(ruleElement, rule);

                addRule(rule);

            } else if (node.getNodeType() == Node.ELEMENT_NODE &&
                    ((Element) node).getTagName().equals("class-rule")) {
                Element ruleElement = (Element) node;

                ClassRule classRule = new ClassRule();
                if ("false".equalsIgnoreCase(getAttrValue(ruleElement, "enabled"))) classRule.setEnabled(false);
                if ("false".equalsIgnoreCase(getAttrValue(ruleElement, "last"))) classRule.setLast(false);
                classRule.setClassStr(getAttrValue(ruleElement, "class"));
                classRule.setMethodStr(getAttrValue(ruleElement, "method"));

                addRule(classRule);

            } else if (node.getNodeType() == Node.ELEMENT_NODE &&
                    ((Element) node).getTagName().equals("outbound-rule")) {

                Element ruleElement = (Element) node;
                // we have a rule node
                OutboundRule rule = new OutboundRule();

                processRuleBasics(ruleElement, rule);
                if ("true".equalsIgnoreCase(getAttrValue(ruleElement, "encodefirst"))) rule.setEncodeFirst(true);

                procesConditions(ruleElement, rule);
                processRuns(ruleElement, rule);

                Node toNode = ruleElement.getElementsByTagName("to").item(0);
                rule.setTo(getNodeValue(toNode));
                rule.setToLast(getAttrValue(toNode, "last"));
                if ("false".equalsIgnoreCase(getAttrValue(toNode, "encode"))) rule.setEncodeToUrl(false);

                processSetAttributes(ruleElement, rule);

                addOutboundRule(rule);

            } else if (node.getNodeType() == Node.ELEMENT_NODE &&
                    ((Element) node).getTagName().equals("catch")) {

                Element catchXMLElement = (Element) node;
                // we have a rule node
                CatchElem catchElem = new CatchElem();

                catchElem.setClassStr(getAttrValue(catchXMLElement, "class"));

                processRuns(catchXMLElement, catchElem);

                catchElems.add(catchElem);

            }
        }
        docProcessed = true;
    }

    private void processRuleBasics(Element ruleElement, RuleBase rule) {
      if ("false".equalsIgnoreCase(getAttrValue(ruleElement, "enabled"))) rule.setEnabled(false);

      String ruleMatchType = getAttrValue(ruleElement, "match-type");
      if (StringUtils.isBlank(ruleMatchType)) ruleMatchType = defaultMatchType;
      rule.setMatchType(ruleMatchType);

      Node nameNode = ruleElement.getElementsByTagName("name").item(0);
      rule.setName(getNodeValue(nameNode));

      Node noteNode = ruleElement.getElementsByTagName("note").item(0);
      rule.setNote(getNodeValue(noteNode));

      Node fromNode = ruleElement.getElementsByTagName("from").item(0);
      rule.setFrom(getNodeValue(fromNode));
      if ("true".equalsIgnoreCase(getAttrValue(fromNode, "casesensitive"))) rule.setFromCaseSensitive(true);
  }

    private static void processSetAttributes(Element ruleElement, RuleBase rule) {
        NodeList setNodes = ruleElement.getElementsByTagName("set");
        for (int j = 0; j < setNodes.getLength(); j++) {
            Node setNode = setNodes.item(j);
            if (setNode == null) continue;
            SetAttribute setAttribute = new SetAttribute();
            setAttribute.setValue(getNodeValue(setNode));
            setAttribute.setType(getAttrValue(setNode, "type"));
            setAttribute.setName(getAttrValue(setNode, "name"));
            rule.addSetAttribute(setAttribute);
        }
    }

    private static void processRuns(Element ruleElement, Runnable runnable) {
        NodeList runNodes = ruleElement.getElementsByTagName("run");
        for (int j = 0; j < runNodes.getLength(); j++) {
            Node runNode = runNodes.item(j);
            if (runNode == null) continue;
            Run run = new Run();
            processInitParams(runNode, run);
            run.setClassStr(getAttrValue(runNode, "class"));
            run.setMethodStr(getAttrValue(runNode, "method"));
            run.setJsonHandler("true".equalsIgnoreCase(getAttrValue(runNode, "jsonhandler")));
            run.setNewEachTime("true".equalsIgnoreCase(getAttrValue(runNode, "neweachtime")));
            runnable.addRun(run);
        }

        // gzip element is just a shortcut to run: org.tuckey.web.filters.urlrewrite.gzip.GzipFilter
        NodeList gzipNodes = ruleElement.getElementsByTagName("gzip");
        for (int j = 0; j < gzipNodes.getLength(); j++) {
            Node runNode = gzipNodes.item(j);
            if (runNode == null) continue;
            Run run = new Run();
            run.setClassStr(GzipFilter.class.getName());
            run.setMethodStr("doFilter(ServletRequest, ServletResponse, FilterChain)");
            processInitParams(runNode, run);
            runnable.addRun(run);
        }
    }

    private static void processInitParams(Node runNode, Run run) {
        if (runNode.getNodeType() == Node.ELEMENT_NODE) {
            Element runElement = (Element) runNode;
            NodeList initParamsNodeList = runElement.getElementsByTagName("init-param");
            for (int k = 0; k < initParamsNodeList.getLength(); k++) {
                Node initParamNode = initParamsNodeList.item(k);
                if (initParamNode == null) continue;
                if (initParamNode.getNodeType() != Node.ELEMENT_NODE) continue;
                Element initParamElement = (Element) initParamNode;
                Node paramNameNode = initParamElement.getElementsByTagName("param-name").item(0);
                Node paramValueNode = initParamElement.getElementsByTagName("param-value").item(0);
                run.addInitParam(getNodeValue(paramNameNode), getNodeValue(paramValueNode));
            }
        }
    }

    private static void procesConditions(Element ruleElement, RuleBase rule) {
        NodeList conditionNodes = ruleElement.getElementsByTagName("condition");
        for (int j = 0; j < conditionNodes.getLength(); j++) {
            Node conditionNode = conditionNodes.item(j);
            if (conditionNode == null) continue;
            Condition condition = new Condition();
            condition.setValue(getNodeValue(conditionNode));
            condition.setType(getAttrValue(conditionNode, "type"));
            condition.setName(getAttrValue(conditionNode, "name"));
            condition.setNext(getAttrValue(conditionNode, "next"));
            condition.setCaseSensitive("true".equalsIgnoreCase(getAttrValue(conditionNode, "casesensitive")));
            condition.setOperator(getAttrValue(conditionNode, "operator"));
            rule.addCondition(condition);
        }
    }

    private static String getNodeValue(Node node) {
        if (node == null) return null;
        NodeList nodeList = node.getChildNodes();
        if (nodeList == null) return null;
        Node child = nodeList.item(0);
        if (child == null) return null;
        if ((child.getNodeType() == Node.TEXT_NODE)) {
            String value = ((Text) child).getData();
            return value.trim();
        }
        return null;
    }

    private static String getAttrValue(Node n, String attrName) {
        if (n == null) return null;
        NamedNodeMap attrs = n.getAttributes();
        if (attrs == null) return null;
        Node attr = attrs.getNamedItem(attrName);
        if (attr == null) return null;
        String val = attr.getNodeValue();
        if (val == null) return null;
        return val.trim();
    }

    /**
     * Initialise the conf file.  This will run initialise on each rule and condition in the conf file.
     */
    public void initialise() {
        if (log.isDebugEnabled()) {
            log.debug("now initialising conf");
        }

        initDecodeUsing(decodeUsing);

        boolean rulesOk = true;
        for (int i = 0; i < rules.size(); i++) {
            final Rule rule = (Rule) rules.get(i);
            if (!rule.initialise(context)) {
                // if we failed to initialise anything set the status to bad
                rulesOk = false;
            }
        }
        for (int i = 0; i < outboundRules.size(); i++) {
            final OutboundRule outboundRule = (OutboundRule) outboundRules.get(i);
            if (!outboundRule.initialise(context)) {
                // if we failed to initialise anything set the status to bad
                rulesOk = false;
            }
        }
        for (int i = 0; i < catchElems.size(); i++) {
            final CatchElem catchElem = (CatchElem) catchElems.get(i);
            if (!catchElem.initialise(context)) {
                // if we failed to initialise anything set the status to bad
                rulesOk = false;
            }
        }
        if (rulesOk) {
            ok = true;
        }
        if (log.isDebugEnabled()) {
            log.debug("conf status " + ok);
        }
    }

    private void initDecodeUsing(String decodeUsingSetting) {
        decodeUsingSetting = StringUtils.trimToNull(decodeUsingSetting);
        if (decodeUsingSetting == null) decodeUsingSetting = DEFAULT_DECODE_USING;

        if ( decodeUsingSetting.equalsIgnoreCase(HEADER_DECODE_USING)) { // is 'header'
            decodeUsingEncodingHeader = true;
            decodeUsingSetting = null;

        }   else if ( decodeUsingSetting.startsWith(HEADER_DECODE_USING + ",")) { // is 'header,xxx'
            decodeUsingEncodingHeader = true;
            decodeUsingSetting = decodeUsingSetting.substring((HEADER_DECODE_USING + ",").length());

        }
        if (NONE_DECODE_USING.equalsIgnoreCase(decodeUsingSetting)) {
            decodeUsingSetting = null;
        }
        if ( decodeUsingSetting != null ) {
            try {
                URLDecoder.decode("testUrl", decodeUsingSetting);
                this.decodeUsing = decodeUsingSetting;
            } catch (UnsupportedEncodingException e) {
                addError("unsupported 'decodeusing' " + decodeUsingSetting + " see Java SDK docs for supported encodings");
            }
        }   else {
            this.decodeUsing = null;
        }
    }

    /**
     * Destory the conf gracefully.
     */
    public void destroy() {
        for (int i = 0; i < rules.size(); i++) {
            final Rule rule = (Rule) rules.get(i);
            rule.destroy();
        }
    }

    /**
     * Will add the rule to the rules list.
     *
     * @param rule The Rule to add
     */
    public void addRule(final Rule rule) {
        rule.setId(ruleIdCounter++);
        rules.add(rule);
    }

    /**
     * Will add the rule to the rules list.
     *
     * @param outboundRule The outbound rule to add
     */
    public void addOutboundRule(final OutboundRule outboundRule) {
        outboundRule.setId(outboundRuleIdCounter++);
        outboundRules.add(outboundRule);
    }

    /**
     * Will get the List of errors.
     *
     * @return the List of errors
     */
    public List getErrors() {
        return errors;
    }

    /**
     * Will get the List of rules.
     *
     * @return the List of rules
     */
    public List getRules() {
        return rules;
    }

    /**
     * Will get the List of outbound rules.
     *
     * @return the List of outbound rules
     */
    public List getOutboundRules() {
        return outboundRules;
    }

    /**
     * true if the conf has been loaded ok.
     *
     * @return boolean
     */
    public boolean isOk() {
        return ok;
    }

    private void addError(final String errorMsg, final Exception e) {
        errors.add(errorMsg);
        log.error(errorMsg, e);
    }

    private void addError(final String errorMsg) {
        errors.add(errorMsg);
    }

    public Date getLoadedDate() {
        return (Date) loadedDate.clone();
    }

    public boolean isUseQueryString() {
        return useQueryString;
    }

    public void setUseQueryString(boolean useQueryString) {
        this.useQueryString = useQueryString;
    }

    public boolean isUseContext() {
        return useContext;
    }

    public void setUseContext(boolean useContext) {
        this.useContext = useContext;
    }

    public String getDecodeUsing() {
        return decodeUsing;
    }

    public void setDecodeUsing(String decodeUsing) {
        this.decodeUsing = decodeUsing;
    }

    public void setDefaultMatchType(String defaultMatchType) {
        if (RuleBase.MATCH_TYPE_WILDCARD.equalsIgnoreCase(defaultMatchType)) {
            this.defaultMatchType = RuleBase.MATCH_TYPE_WILDCARD;
        } else {
            this.defaultMatchType = RuleBase.DEFAULT_MATCH_TYPE;
        }
    }

    public String getDefaultMatchType() {
        return defaultMatchType;
    }

    public List getCatchElems() {
        return catchElems;
    }

    public boolean isDecodeUsingCustomCharsetRequired() {
        return decodeUsing != null;
    }

    public boolean isEngineEnabled() {
        return engineEnabled;
    }

    public void setEngineEnabled(boolean engineEnabled) {
        this.engineEnabled = engineEnabled;
    }

    public boolean isDecodeUsingEncodingHeader() {
        return decodeUsingEncodingHeader;
    }
}
