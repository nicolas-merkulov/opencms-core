/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH (http://www.alkacon.com)
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
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.editors.usergenerated;

import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProject;
import org.opencms.file.CmsResource;
import org.opencms.file.types.I_CmsResourceType;
import org.opencms.lock.CmsLock;
import org.opencms.main.CmsException;
import org.opencms.main.CmsIllegalStateException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.report.CmsLogReport;
import org.opencms.security.CmsPermissionViolationException;
import org.opencms.util.CmsMacroResolver;
import org.opencms.util.CmsStringUtil;
import org.opencms.util.CmsUUID;
import org.opencms.xml.CmsXmlException;
import org.opencms.xml.CmsXmlUtils;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.CmsXmlContentErrorHandler;
import org.opencms.xml.content.CmsXmlContentFactory;
import org.opencms.xml.types.I_CmsXmlContentValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.logging.Log;

import com.google.common.collect.Maps;

/**
 * A form editing session is required to create and edit contents from the web front-end.<p>
 */
public class CmsFormSession {

    /**
     * Compares XPaths.<p>
     */
    protected static class PathComparator implements Comparator<String> {

        /**
         * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
         */
        public int compare(String o1, String o2) {

            int result = -1;
            if (o1 == null) {
                result = 1;
            } else if (o2 == null) {
                result = -1;
            } else {
                String[] o1Elements = o1.split("/");
                String[] o2Elements = o2.split("/");
                // compare the path elements
                for (int i = 0; i < o1Elements.length; i++) {
                    String o1Name = CmsXmlUtils.removeXpathIndex(o1Elements[i]);
                    String o2Name = CmsXmlUtils.removeXpathIndex(o2Elements[i]);
                    if (o1Name.equals(o2Name)) {
                        // in case of the same path name, check the indices
                        int o1Index = CmsXmlUtils.getXpathIndexInt(o1Elements[i]);
                        int o2Index = CmsXmlUtils.getXpathIndexInt(o2Elements[i]);
                        if (o1Index != o2Index) {
                            result = o2Index - o1Index;
                            break;
                        } else if (i == (o2Elements.length - 1)) {
                            // in case this is the last element of o2's path
                            result = 1;
                            break;
                        }
                    } else {
                        result = o1Name.compareTo(o2Name);
                        break;
                    }
                }
            }

            return result;
        }
    }

    /** The form upload helper. */
    private CmsFormUploadHelper m_uploadHelper = new CmsFormUploadHelper();

    /** The log instance for this class. */
    private static final Log LOG = CmsLog.getLog(CmsFormSession.class);

    /** The edit context. */
    private CmsObject m_cms;

    /** The form configuration. */
    private CmsFormConfiguration m_configuration;

    /** The resource being edited. */
    private CmsResource m_editResource;

    /** The Admin-privileged CMS context. */
    private CmsObject m_adminCms;

    /** True if the session is finished. */
    private boolean m_finished;

    /** Previously uploaded resources, indexed by field names. */
    private Map<String, CmsResource> m_uploadResourcesByField = Maps.newHashMap();

    /** Flag which indicates whether the project and its resources should be deleted when the session is destroyed. */
    private boolean m_requiresCleanup = true;

    /** 
     * Constructor.<p>
     * 
     * @param cms the cms context
     * @param configuration the form configuration
     * 
     * @throws CmsException if creating the session project fails
     */
    public CmsFormSession(CmsObject cms, CmsFormConfiguration configuration)
    throws CmsException {

        this(cms, cms, configuration);
    }

    /** 
     * Constructor.<p>
     * 
     * @param adminCms the cms context with admin privileges 
     * @param cms the cms context
     * @param configuration the form configuration
     * 
     * @throws CmsException if creating the session project fails
     */
    public CmsFormSession(CmsObject adminCms, CmsObject cms, CmsFormConfiguration configuration)
    throws CmsException {

        m_adminCms = adminCms;
        m_configuration = configuration;
        if (cms.getRequestContext().getCurrentUser().isGuestUser() && m_configuration.getUserForGuests().isPresent()) {
            m_cms = OpenCms.initCmsObject(m_configuration.getUserForGuests().get().getName());
            m_cms.getRequestContext().setSiteRoot(cms.getRequestContext().getSiteRoot());
        } else {
            m_cms = OpenCms.initCmsObject(cms);
        }
        CmsProject project = m_adminCms.createProject(
            generateProjectName(),
            "User generated content project",
            m_configuration.getProjectGroup().getName(),
            m_configuration.getProjectGroup().getName());
        project.setDeleteAfterPublishing(true);
        m_adminCms.writeProject(project);
        m_cms.getRequestContext().setCurrentProject(project);
    }

    /**
     * Constructor. For test purposes only.<p>
     * 
     * @param cms the cms context
     */
    protected CmsFormSession(CmsObject cms) {

        m_cms = cms;
    }

    /**
     * Creates a new resource from upload data.<p>
     * 
     * @param fieldName the name of the form field for the upload
     * @param rawFileName the file name
     * @param content the file content
     * 
     * @return the newly created resource
     * 
     * @throws CmsException if creating the resource fails
     */
    public CmsResource createUploadResource(String fieldName, String rawFileName, byte[] content) throws CmsException {

        CmsResource result = null;
        CmsFormSessionSecurityUtil.checkCreateUpload(m_cms, m_configuration, rawFileName, content.length);
        String baseName = rawFileName;

        // if the given name is a path, make sure we only get the last segment

        int lastSlashPos = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
        if (lastSlashPos != -1) {
            baseName = baseName.substring(1 + lastSlashPos);
        }

        // translate it so it doesn't contain illegal characters 

        baseName = OpenCms.getResourceManager().getFileTranslator().translateResource(baseName);

        // add a macro before the file extension (if there is a file extension, otherwise just append it)

        int dotPos = baseName.lastIndexOf('.');
        if (dotPos == -1) {
            baseName = baseName + "_%(random)";
        } else {
            baseName = baseName.substring(0, dotPos) + "_%(random)" + baseName.substring(dotPos);
        }

        // now prepend the upload folder's path 

        String uploadRootPath = m_configuration.getUploadParentFolder().get().getRootPath();
        String sitePath = CmsStringUtil.joinPaths(m_cms.getRequestContext().removeSiteRoot(uploadRootPath), baseName);

        // ... and replace the macro with random strings until we find a path that isn't already used 

        String realSitePath;
        do {
            CmsMacroResolver resolver = new CmsMacroResolver();
            resolver.addMacro("random", RandomStringUtils.random(8, "0123456789abcdefghijklmnopqrstuvwxyz"));
            realSitePath = resolver.resolveMacros(sitePath);
        } while (m_cms.existsResource(realSitePath));
        int resTypeId = OpenCms.getResourceManager().getDefaultTypeForName(realSitePath).getTypeId();
        result = m_cms.createResource(realSitePath, resTypeId, content, null);
        updateUploadResource(fieldName, result);

        return result;
    }

    /**
     * Creates a new edit resource.<p>
     * 
     * @return the newly created resource
     * 
     * @throws CmsException if creating the resource fails
     */
    public CmsResource createXmlContent() throws CmsException {

        checkNotFinished();
        checkEditResourceNotSet();

        CmsFormSessionSecurityUtil.checkCreateContent(m_cms, m_configuration);
        I_CmsResourceType type = OpenCms.getResourceManager().getResourceType(m_configuration.getResourceType());
        m_editResource = m_cms.createResource(getNewContentName(), type.getTypeId());
        return m_editResource;
    }

    /**
     * Finishes the session and publishes the changed resources if necessary.<p>
     * 
     * @throws CmsException if something goes wrong 
     */
    public void finish() throws CmsException {

        m_finished = true;
        m_requiresCleanup = false;
        if (m_configuration.isAutoPublish()) {
            CmsProject project = getProject();
            CmsObject projectCms = OpenCms.initCmsObject(m_adminCms);
            projectCms.getRequestContext().setCurrentProject(project);

            // we don't necessarily publish with the user who has the locks on the resources, so we need to steal the locks  
            List<CmsResource> projectResources = projectCms.readProjectView(project.getUuid(), CmsResource.STATE_KEEP);
            for (CmsResource projectResource : projectResources) {
                CmsLock lock = projectCms.getLock(projectResource);
                if (!lock.isUnlocked() && !lock.isLockableBy(projectCms.getRequestContext().getCurrentUser())) {
                    projectCms.changeLock(projectResource);
                }
            }
            OpenCms.getPublishManager().publishProject(
                projectCms,
                new CmsLogReport(Locale.ENGLISH, CmsFormSession.class));
        }

    }

    /**
     * Gets the CMS context used by this session.<p>
     * 
     * @return the CMS context used by this session 
     */
    public CmsObject getCmsObject() {

        return m_cms;
    }

    /**
     * Gets the form upload helper belonging to this session.<p>
     * 
     * @return the form upload helper belonging to this session
     */
    public CmsFormUploadHelper getFormUploadHelper() {

        return m_uploadHelper;
    }

    /** 
     * Returns the session id.<p>
     * 
     * @return the session id 
     */
    public CmsUUID getId() {

        return getProject().getUuid();
    }

    /**
     * Returns the edit project.<p>
     *  
     * @return the edit project
     */
    public CmsProject getProject() {

        return m_cms.getRequestContext().getCurrentProject();
    }

    /**
     * Returns the edit resource.<p>
     * 
     * @return the edit resource
     */
    public CmsResource getResource() {

        return m_editResource;

    }

    /**
     * Returns the content values.<p>
     * 
     * @return the content values
     * 
     * @throws CmsException if reading the content fails
     */
    public Map<String, String> getValues() throws CmsException {

        CmsFile file = m_cms.readFile(m_editResource);
        CmsXmlContent content = CmsXmlContentFactory.unmarshal(m_cms, file);
        Locale locale = m_cms.getRequestContext().getLocale();
        if (!content.hasLocale(locale)) {
            content.addLocale(m_cms, locale);
        }
        return getContentValues(content, locale);
    }

    /**
     * Returns true if the session is finished.<p>
     * 
     * @return true if the session is finished 
     */
    public boolean isFinished() {

        return m_finished;
    }

    /**
     * Loads the existing edit resource.<p>
     * 
     * @param fileName the resource file name
     * 
     * @return the edit resource
     * 
     * @throws CmsException if reading the resource fails
     */
    public CmsResource loadXmlContent(String fileName) throws CmsException {

        checkNotFinished();
        checkEditResourceNotSet();
        if (fileName.contains("/")) {
            throw new CmsPermissionViolationException(Messages.get().container(
                Messages.ERR_INVALID_FILE_NAME_TO_LOAD_1,
                fileName));
        }
        String contentSitePath = m_cms.getRequestContext().removeSiteRoot(
            m_configuration.getContentParentFolder().getRootPath());
        String path = CmsStringUtil.joinPaths(contentSitePath, fileName);
        m_editResource = m_cms.readResource(path);
        CmsLock lock = m_cms.getLock(m_editResource);
        if (!lock.isOwnedBy(m_cms.getRequestContext().getCurrentUser())) {
            m_cms.lockResourceTemporary(m_editResource);
        }
        return m_editResource;
    }

    /**
     * Is called when the parent session times out.<p>
     */
    public void onSessionDestroyed() {

        if (m_requiresCleanup) {
            cleanupProject();
        }
    }

    /**
     * Saves the content values to the sessions edit resource.<p>
     * 
     * @param contentValues the content values by XPath
     * 
     * @return the validation handler
     * 
     * @throws CmsException if writing the content fails
     */
    public CmsXmlContentErrorHandler saveContent(Map<String, String> contentValues) throws CmsException {

        checkNotFinished();
        CmsFile file = m_cms.readFile(m_editResource);
        CmsXmlContent content = addContentValues(file, contentValues);
        CmsXmlContentErrorHandler errorHandler = content.validate(m_cms);

        file.setContents(content.marshal());

        // the file content might have been modified during the write operation
        file = m_cms.writeFile(file);

        return errorHandler;

    }

    /**
     * Validates the content values.<p>
     * 
     * @param contentValues the content values to validate
     * 
     * @return the validation handler
     * 
     * @throws CmsException if reading the content file fails
     */
    public CmsXmlContentErrorHandler validateContent(Map<String, String> contentValues) throws CmsException {

        checkNotFinished();
        CmsFile file = m_cms.readFile(m_editResource);
        CmsXmlContent content = addContentValues(file, contentValues);
        return content.validate(m_cms);
    }

    /**
     * Adds the given value to the content document.<p>
     * 
     * @param content the content document
     * @param locale the content locale
     * @param path the value XPath
     * @param value the value
     */
    protected void addContentValue(CmsXmlContent content, Locale locale, String path, String value) {

        if (!content.hasValue(path, locale)) {
            String[] pathElements = path.split("/");
            String currentPath = pathElements[0];
            for (int i = 0; i < pathElements.length; i++) {
                if (i > 0) {
                    currentPath = CmsStringUtil.joinPaths(currentPath, pathElements[i]);
                }
                while (!content.hasValue(currentPath, locale)) {
                    content.addValue(m_cms, currentPath, locale, CmsXmlUtils.getXpathIndexInt(currentPath) - 1);
                }
            }
        }
        content.getValue(path, locale).setStringValue(m_cms, value);
    }

    /**
     * Adds the given values to the content document.<p>
     * 
     * @param file the content file
     * @param contentValues the values to add
     * 
     * @return the content document
     *  
     * @throws CmsException if writing the XML fails 
     */
    protected CmsXmlContent addContentValues(CmsFile file, Map<String, String> contentValues) throws CmsException {

        CmsXmlContent content = CmsXmlContentFactory.unmarshal(m_cms, file);
        Locale locale = m_cms.getRequestContext().getLocale();

        addContentValues(content, locale, contentValues);
        return content;
    }

    /**
     * Adds the given values to the content document.<p>
     * 
     * @param content the content document
     * @param locale the content locale
     * @param contentValues the values
     * 
     * @throws CmsXmlException if writing the XML fails
     */
    protected void addContentValues(CmsXmlContent content, Locale locale, Map<String, String> contentValues)
    throws CmsXmlException {

        if (!content.hasLocale(locale)) {
            content.addLocale(m_cms, locale);
        }
        List<String> paths = new ArrayList<String>(contentValues.keySet());
        Collections.sort(paths, new PathComparator());
        for (String path : paths) {
            addContentValue(content, locale, path, contentValues.get(path));
        }
    }

    /**
     * Returns the content values of the requested locale.<p>
     * 
     * @param content the content document
     * @param locale the content locale
     * 
     * @return the values
     */
    protected Map<String, String> getContentValues(CmsXmlContent content, Locale locale) {

        Map<String, String> result = new HashMap<String, String>();
        List<I_CmsXmlContentValue> values = content.getValues(locale);
        for (I_CmsXmlContentValue value : values) {
            if (value.isSimpleType()) {
                result.put(value.getPath(), value.getStringValue(m_cms));
            }
        }
        return result;
    }

    /**
     * Throws an error if the edit resource is already set.<p>
     * 
     * @throws CmsIllegalStateException if the edit resource is already set 
     */
    private void checkEditResourceNotSet() throws CmsIllegalStateException {

        if (m_editResource != null) {
            throw new CmsIllegalStateException(Messages.get().container(
                Messages.ERR_CANT_EDIT_MULTIPLE_CONTENTS_IN_SESSION_0));
        }
    }

    /**
     * Checks that the session is not finished, and throws an exception otherwise.<p>
     * 
     * @throws CmsIllegalStateException if the session is finished 
     */
    private void checkNotFinished() throws CmsIllegalStateException {

        if (m_finished) {
            throw new CmsIllegalStateException(Messages.get().container(Messages.ERR_FORM_SESSION_ALREADY_FINISHED_0

            ));
        }
    }

    /**
     * Cleans up the project.<p>
     */
    private void cleanupProject() {

        m_requiresCleanup = false;

        try {
            CmsObject cms = OpenCms.initCmsObject(m_adminCms);
            cms.getRequestContext().setCurrentProject(getProject());
            CmsUUID projectId = getProject().getUuid();
            List<CmsResource> projectResources = cms.readProjectView(projectId, CmsResource.STATE_KEEP);
            if (hasOnlyNewResources(projectResources)) {
                for (CmsResource res : projectResources) {
                    LOG.info("Deleting resource for timed out form session: " + res.getRootPath());
                    deleteResourceFromProject(cms, res);
                }
                LOG.info("Deleting project for timed out form session: "
                    + getProject().getName()
                    + " ["
                    + getProject().getUuid()
                    + "]");
                cms.deleteProject(projectId);

            }
        } catch (CmsException e) {
            LOG.error(e.getLocalizedMessage(), e);
        }
    }

    /**
     * Deletes the given resource which is part of  a form session project.<p>
     * 
     * @param cms the CMS context to use 
     * @param res the resource to delete
     *  
     * @throws CmsException if something goes wrong 
     */
    private void deleteResourceFromProject(CmsObject cms, CmsResource res) throws CmsException {

        CmsLock lock = cms.getLock(res);
        if (lock.isUnlocked() || lock.isLockableBy(cms.getRequestContext().getCurrentUser())) {
            cms.lockResourceTemporary(res);
        } else {
            cms.changeLock(res);
        }
        cms.deleteResource(cms.getSitePath(res), CmsResource.DELETE_PRESERVE_SIBLINGS);
    }

    /**
     * Returns the edit project name.<p>
     * 
     * @return the project name
     */
    private String generateProjectName() {

        return "Edit project " + new Date();
    }

    /**
     * Returns the new resource site path.<p>
     * 
     * @return the new resource site path
     * @throws CmsException if something goes wrong 
     */
    private String getNewContentName() throws CmsException {

        String sitePath = OpenCms.getResourceManager().getNameGenerator().getNewFileName(
            m_cms,
            CmsStringUtil.joinPaths(
                m_cms.getRequestContext().removeSiteRoot(m_configuration.getContentParentFolder().getRootPath()),
                m_configuration.getNamePattern()),
            5);
        return sitePath;
    }

    /**
     * Checks if all the resource states from a list of resources are 'new'.<p>
     * 
     * @param projectResources the resources to check 
     * @return true if all the resources from the input list have the state 'new' 
     */
    private boolean hasOnlyNewResources(List<CmsResource> projectResources) {

        boolean hasOnlyNewResources = true;
        for (CmsResource projectRes : projectResources) {
            if (!projectRes.getState().isNew()) {
                hasOnlyNewResources = false;
                break;
            }
        }
        return hasOnlyNewResources;
    }

    /**
     * Stores the upload resource and deletes previously uploaded resources for the same form field.<p>
     * 
     * @param fieldName the field name 
     * @param upload the uploaded resource 
     */
    private void updateUploadResource(String fieldName, CmsResource upload) {

        CmsResource prevUploadResource = m_uploadResourcesByField.get(fieldName);
        if (prevUploadResource != null) {
            try {
                m_cms.deleteResource(m_cms.getSitePath(prevUploadResource), CmsResource.DELETE_PRESERVE_SIBLINGS);
            } catch (Exception e) {
                LOG.error("Couldn't delete previous upload resource: " + e.getLocalizedMessage(), e);
            }
        }
        m_uploadResourcesByField.put(fieldName, upload);
    }
}