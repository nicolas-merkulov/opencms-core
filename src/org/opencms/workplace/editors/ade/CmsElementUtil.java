/*
 * File   : $Source: /alkacon/cvs/opencms/src/org/opencms/workplace/editors/ade/Attic/CmsElementUtil.java,v $
 * Date   : $Date: 2009/10/13 11:59:43 $
 * Version: $Revision: 1.1.2.14 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) 2002 - 2009 Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software GmbH, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.workplace.editors.ade;

import org.opencms.db.CmsUserSettings;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.types.CmsResourceTypeContainerPage;
import org.opencms.flex.CmsFlexController;
import org.opencms.i18n.CmsLocaleManager;
import org.opencms.i18n.CmsMessages;
import org.opencms.json.JSONArray;
import org.opencms.json.JSONException;
import org.opencms.json.JSONObject;
import org.opencms.jsp.CmsJspTagContainer;
import org.opencms.loader.CmsTemplateLoaderFacade;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsMacroResolver;
import org.opencms.util.CmsUUID;
import org.opencms.workplace.editors.directedit.CmsAdvancedDirectEditProvider;
import org.opencms.workplace.editors.directedit.CmsDirectEditMode;
import org.opencms.workplace.editors.directedit.I_CmsDirectEditProvider;
import org.opencms.workplace.explorer.CmsResourceUtil;
import org.opencms.xml.CmsXmlContentDefinition;
import org.opencms.xml.containerpage.CmsADEManager;
import org.opencms.xml.containerpage.CmsContainerElementBean;
import org.opencms.xml.containerpage.I_CmsContainerBean;
import org.opencms.xml.containerpage.I_CmsContainerElementBean;
import org.opencms.xml.containerpage.I_CmsContainerPageBean;
import org.opencms.xml.content.CmsDefaultXmlContentHandler;
import org.opencms.xml.content.CmsXmlContentProperty;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;

/**
 * Maintains recent and favorite element lists.<p>
 * 
 * @author Michael Moossen 
 * 
 * @version $Revision: 1.1.2.14 $
 * 
 * @since 7.6
 */
public final class CmsElementUtil {

    /** JSON property constant file. */
    public static final String P_ELEMENT_ALLOWEDIT = "allowEdit";

    /** JSON property constant contents. */
    public static final String P_ELEMENT_CONTENTS = "contents";

    /** JSON property constant file. */
    public static final String P_ELEMENT_DATE = "date";

    /** JSON property constant file. */
    public static final String P_ELEMENT_FILE = "file";

    /** JSON property constant formatters. */
    public static final String P_ELEMENT_FORMATTERS = "formatters";

    /** JSON property constant id. */
    public static final String P_ELEMENT_ID = "id";

    /** JSON property constant file. */
    public static final String P_ELEMENT_LOCKED = "locked";

    /** JSON property constant file. */
    public static final String P_ELEMENT_NAVTEXT = "navText";

    /** JSON property constant file. */
    public static final String P_ELEMENT_STATUS = "status";

    /** JSON property constant file. */
    public static final String P_ELEMENT_SUBITEMS = "subItems";

    /** JSON property constant file. */
    public static final String P_ELEMENT_TITLE = "title";

    /** JSON property constant file. */
    public static final String P_ELEMENT_TYPE = "type";

    /** JSON response property constant. */
    public static final String P_ELEMENT_TYPENAME = "typename";

    /** JSON property constant file. */
    public static final String P_ELEMENT_USER = "user";

    /** JSON property constant type. */
    public static final String P_PROPERTY_TYPE = "type";

    /** JSON property constant widget. */
    public static final String P_PROPERTY_WIDGET = "widget";

    /** JSON property constant widgetConf. */
    public static final String P_PROPERTY_WIDGET_CONF = "widgetConf";

    /** JSON property constant ruleType. */
    public static final String P_PROPERTY_RULE_TYPE = "ruleType";

    /** JSON property constant ruleType. */
    public static final String P_PROPERTY_RULE_REGEX = "ruleRegex";

    /** JSON property constant value. */
    public static final String P_PROPERTY_VALUE = "value";

    /** JSON property constant defaultValue. */
    public static final String P_PROPERTY_DEFAULT_VALUE = "defaultValue";

    /** JSON property constant niceName. */
    public static final String P_PROPERTY_NICE_NAME = "niceName";

    /** JSON property constant description. */
    public static final String P_PROPERTY_DESCRIPTION = "description";

    /** JSON property constant error. */
    public static final String P_PROPERTY_ERROR = "error";

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(CmsElementUtil.class);

    /** The cms context. */
    private CmsObject m_cms;

    /** The ADE manager. */
    private CmsADEManager m_manager;

    /** The http request. */
    private HttpServletRequest m_req;

    /** The http response. */
    private HttpServletResponse m_res;

    /** The actual container page uri. */
    private String m_cntPageUri;

    /**
     * Creates a new instance.<p>
     * 
     * @param cms the cms context
     * @param cntPageUri the container page uri
     * @param req the http request
     * @param res the http response
     */
    public CmsElementUtil(CmsObject cms, String cntPageUri, HttpServletRequest req, HttpServletResponse res) {

        m_cms = cms;
        m_req = req;
        m_res = res;
        m_cntPageUri = cntPageUri;
        m_manager = OpenCms.getADEManager(cms, cntPageUri, req);
    }

    /**
     * Returns the content of an element when rendered with the given formatter.<p> 
     * 
     * @param element the element bean
     * @param formatter the formatter uri
     * 
     * @return generated html code
     * 
     * @throws CmsException if an cms related error occurs
     * @throws ServletException if a jsp related error occurs
     * @throws IOException if a jsp related error occurs
     */
    public String getElementContent(I_CmsContainerElementBean element, CmsResource formatter)
    throws CmsException, ServletException, IOException {

        CmsTemplateLoaderFacade loaderFacade = new CmsTemplateLoaderFacade(OpenCms.getResourceManager().getLoader(
            formatter), element.getElement(), formatter);

        CmsResource loaderRes = loaderFacade.getLoaderStartResource();

        // get the current Flex controller
        CmsObject cms = CmsFlexController.getController(m_req).getCmsObject();
        String oldUri = cms.getRequestContext().getUri();
        try {
            cms.getRequestContext().setUri(m_cntPageUri);

            // to enable 'old' direct edit features for content-collector-elements, set the direct-edit-provider-attribute in the request
            I_CmsDirectEditProvider eb = new CmsAdvancedDirectEditProvider();
            eb.init(cms, CmsDirectEditMode.TRUE, m_cms.getSitePath(element.getElement()));
            m_req.setAttribute(I_CmsDirectEditProvider.ATTRIBUTE_DIRECT_EDIT_PROVIDER, eb);
            Object currentElement = m_req.getAttribute(CmsJspTagContainer.ATTR_CURRENT_ELEMENT);
            m_req.setAttribute(CmsJspTagContainer.ATTR_CURRENT_ELEMENT, element);
            // TODO: is this going to be cached? most likely not! any alternative?
            try {
                return new String(loaderFacade.getLoader().dump(
                    m_cms,
                    loaderRes,
                    null,
                    m_cms.getRequestContext().getLocale(),
                    m_req,
                    m_res), CmsLocaleManager.getResourceEncoding(cms, element.getElement()));
            } finally {
                m_req.setAttribute(CmsJspTagContainer.ATTR_CURRENT_ELEMENT, currentElement);
            }
        } finally {
            cms.getRequestContext().setUri(oldUri);
        }
    }

    /**
     * Returns the data for an element.<p>
     * 
     * @param element the resource
     * @param types the types supported by the container page
     * 
     * @return the data for an element
     * 
     * @throws CmsException if something goes wrong
     * @throws JSONException if something goes wrong in the json manipulation
     */
    public JSONObject getElementData(I_CmsContainerElementBean element, Collection<String> types)
    throws CmsException, JSONException {

        // create new json object for the element
        JSONObject resElement = new JSONObject();
        CmsResource resource = element.getElement();
        resElement.put(CmsADEServer.P_OBJTYPE, CmsADEServer.ELEMENT_TYPE);
        resElement.put(P_ELEMENT_ID, element.getClientId());
        resElement.put(P_ELEMENT_FILE, m_cms.getSitePath(resource));
        resElement.put(P_ELEMENT_DATE, resource.getDateLastModified());
        resElement.put(P_ELEMENT_USER, m_cms.readUser(resource.getUserLastModified()).getName());
        resElement.put(P_ELEMENT_NAVTEXT, m_cms.readPropertyObject(
            resource,
            CmsPropertyDefinition.PROPERTY_NAVTEXT,
            false).getValue(""));
        resElement.put(
            P_ELEMENT_TITLE,
            m_cms.readPropertyObject(resource, CmsPropertyDefinition.PROPERTY_TITLE, false).getValue(""));
        CmsResourceUtil resUtil = new CmsResourceUtil(m_cms, resource);
        resElement.put(P_ELEMENT_ALLOWEDIT, resUtil.getLock().isLockableBy(m_cms.getRequestContext().currentUser())
            && resUtil.isEditable());
        resElement.put(P_ELEMENT_LOCKED, resUtil.getLockedByName());
        resElement.put(P_ELEMENT_STATUS, "" + resUtil.getStateAbbreviation());
        // add formatted elements
        JSONObject resContents = new JSONObject();
        resElement.put(P_ELEMENT_CONTENTS, resContents);
        // add formatter uris
        JSONObject formatters = new JSONObject();
        resElement.put(P_ELEMENT_FORMATTERS, formatters);
        if (resource.getTypeId() == CmsResourceTypeContainerPage.getStaticTypeId()) {
            // set empty entries to prevent client side problems
            Iterator<String> itTypes = types.iterator();
            while (itTypes.hasNext()) {
                String type = itTypes.next();
                formatters.put(type, ""); // empty formatters
                resContents.put(type, ""); // empty contents
            }
            // this container page should contain exactly one container
            I_CmsContainerPageBean cntPage = m_manager.getCache(m_cms, resource, m_cms.getRequestContext().getLocale());
            I_CmsContainerBean container = cntPage.getContainers().values().iterator().next();

            // add subitems
            JSONArray subitems = new JSONArray();
            resElement.put(P_ELEMENT_SUBITEMS, subitems);
            // iterate the elements
            for (I_CmsContainerElementBean subElement : container.getElements()) {
                // collect ids
                subitems.put(subElement.getClientId());
            }
        } else {
            Iterator<Map.Entry<String, String>> it = m_manager.getXmlContentFormatters(resource).entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                String type = entry.getKey();
                if (!types.contains(type) && !type.equals(CmsDefaultXmlContentHandler.DEFAULT_FORMATTER_TYPE)) {
                    // skip not supported types
                    continue;
                }
                String formatterUri = entry.getValue();
                formatters.put(type, formatterUri);
                // execute the formatter jsp for the given element
                try {
                    String jspResult = getElementContent(element, m_cms.readResource(formatterUri));
                    // set the results
                    resContents.put(type, jspResult);
                } catch (Exception e) {
                    LOG.error(Messages.get().getBundle().key(
                        Messages.ERR_GENERATE_FORMATTED_ELEMENT_3,
                        m_cms.getSitePath(resource),
                        formatterUri,
                        type), e);
                }
            }
        }

        return resElement;
    }

    /**
     * Returns the data for an element.<p>
     * 
     * @param resource the resource
     * @param types the types supported by the container page
     * 
     * @return the data for an element
     * 
     * @throws CmsException if something goes wrong
     * @throws JSONException if something goes wrong in the json manipulation
     */
    public JSONObject getElementData(CmsResource resource, Collection<String> types) throws CmsException, JSONException {

        return getElementData(getElementBeanForResource(resource), types);
    }

    /**
     * Returns the data for an element.<p>
     * 
     * @param structureId the element structure id
     * @param types the types supported by the container page
     * 
     * @return the data for an element
     * 
     * @throws CmsException if something goes wrong
     * @throws JSONException if something goes wrong in the json manipulation
     */
    public JSONObject getElementData(CmsUUID structureId, Collection<String> types) throws CmsException, JSONException {

        return getElementData(m_cms.readResource(structureId), types);
    }

    /**
     * Returns the data for an element.<p>
     * 
     * @param elementUri the element uri
     * @param types the types supported by the container page
     * 
     * @return the data for an element
     * 
     * @throws CmsException if something goes wrong
     * @throws JSONException if something goes wrong in the json manipulation
     */
    public JSONObject getElementData(String elementUri, Collection<String> types) throws CmsException, JSONException {

        return getElementData(m_cms.readResource(elementUri), types);
    }

    /**
     * Creates an element-bean for the given resource.<p>
     * 
     * @param resource the resource
     * 
     * @return the element-bean
     */
    public I_CmsContainerElementBean getElementBeanForResource(CmsResource resource) {

        return new CmsContainerElementBean(m_cms, resource);
    }

    /**
     * Returns the property information for the given element as a JSON object.<p>
     * 
     * @param element the element
     * @return the property information
     * @throws CmsException - if something goes wrong
     * @throws JSONException - if something goes wrong generating the JSON
     */
    public JSONObject getElementPropertyInfo(I_CmsContainerElementBean element) throws CmsException, JSONException {

        CmsUserSettings settings = new CmsUserSettings(m_cms.getRequestContext().currentUser());
        CmsMessages messages = CmsXmlContentDefinition.getContentHandlerForResource(m_cms, element.getElement()).getMessages(
            settings.getLocale());
        JSONObject result = new JSONObject();
        Map<String, CmsXmlContentProperty> propertiesConf = CmsContainerElementBean.getPropertyConfiguration(
            m_cms,
            element.getElement());
        Map<String, CmsProperty> properties = element.getProperties();
        Iterator<String> itProperties = propertiesConf.keySet().iterator();
        while (itProperties.hasNext()) {
            JSONObject jSONProperty = new JSONObject();
            String propertyName = itProperties.next();
            CmsXmlContentProperty conf = propertiesConf.get(propertyName);
            CmsMacroResolver.resolveMacros(conf.getWidgetConfiguration(), m_cms, Messages.get().getBundle());
            jSONProperty.put(P_PROPERTY_VALUE, properties.get(propertyName).getStructureValue());
            jSONProperty.put(P_PROPERTY_DEFAULT_VALUE, conf.getDefault());
            jSONProperty.put(P_PROPERTY_TYPE, conf.getPropertyType());
            jSONProperty.put(P_PROPERTY_WIDGET, conf.getWidget());
            jSONProperty.put(P_PROPERTY_WIDGET_CONF, CmsMacroResolver.resolveMacros(
                conf.getWidgetConfiguration(),
                m_cms,
                messages));
            jSONProperty.put(P_PROPERTY_RULE_TYPE, conf.getRuleType());
            jSONProperty.put(P_PROPERTY_RULE_REGEX, conf.getRuleRegex());
            jSONProperty.put(P_PROPERTY_NICE_NAME, CmsMacroResolver.resolveMacros(conf.getNiceName(), m_cms, messages));
            jSONProperty.put(P_PROPERTY_DESCRIPTION, CmsMacroResolver.resolveMacros(
                conf.getDescription(),
                m_cms,
                messages));
            jSONProperty.put(P_PROPERTY_ERROR, CmsMacroResolver.resolveMacros(conf.getError(), m_cms, messages));
            result.put(propertyName, jSONProperty);
        }
        return result;
    }
}
