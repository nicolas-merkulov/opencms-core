package com.opencms.workplace;

import org.w3c.dom.*;
import org.xml.sax.*;

import com.opencms.core.*;
import com.opencms.template.*;
import com.opencms.file.*;

import java.util.*;


/**
 * Class for building workplace error pages. <BR>
 * Called by CmsXmlTemplateFile for handling the special XML tag <code>&lt;ERRORPAGE&gt;</code>.
 * 
 * @author Michael Emmerich
 * @version $Revision: 1.3 $ $Date: 2000/02/02 10:30:29 $
 */
public class CmsErrorpage extends A_CmsWpElement implements I_CmsWpElement, I_CmsWpConstants  {    
    
    /**
     * Handling of the <CODE>&lt;ERRORPAGEBOX&gt;</CODE> tags.
     * <P>
     * Reads the code of a error page box from the errors definition file
     * and returns the processed code with the actual elements.
     * <P>
     * Error page boxes can be referenced in any workplace template by <br>
     * // TODO: insert correct syntax here!
     * <CODE>&lt;ERRORPAGEBOX name="..." action="..." alt="..."/&gt;</CODE>
     * 
     * @param cms A_CmsObject Object for accessing resources.
     * @param n XML element containing the <code>&lt;ERRORPAGEBOX&gt;</code> tag.
     * @param doc Reference to the A_CmsXmlContent object of the initiating XLM document.  
     * @param callingObject reference to the calling object.
     * @param parameters Hashtable containing all user parameters.
     * @param lang CmsXmlLanguageFile conataining the currently valid language file.
     * @return Processed button.
     * @exception CmsException
     */
    public Object handleSpecialWorkplaceTag(A_CmsObject cms, Element n, A_CmsXmlContent doc, Object callingObject, Hashtable parameters, CmsXmlLanguageFile lang) throws CmsException {
        
        // collect all required data
        String errorTitle = n.getAttribute(C_ERROR_TITLE);
        String errorMessage = n.getAttribute(C_ERROR_MESSAGE);
        String errorReason = n.getAttribute(C_ERROR_REASON);
        String errorSuggestion = n.getAttribute(C_ERROR_SUGGESTION);
        String errorLink = n.getAttribute(C_ERROR_LINK);
        
        String reason;
        String button;
        
        CmsXmlWpErrorDefFile errordef = getErrorDefinitions(cms);
        
        // get the data from the language file
        errorTitle = lang.getLanguageValue(errorTitle);
        errorMessage = lang.getLanguageValue(errorMessage);
        errorReason = lang.getLanguageValue(errorReason);
        errorSuggestion = lang.getLanguageValue(errorSuggestion);
        reason=lang.getLanguageValue("message.reason");
        button=lang.getLanguageValue("button.ok");
        
        // build errorbox
        String result = errordef.getErrorpage(errorTitle,errorMessage,errorReason,
                                             errorSuggestion,errorLink,reason,button);
        return result; 

    }                    
}
