package com.opencms.workplace;

import org.w3c.dom.*;
import org.xml.sax.*;

import com.opencms.core.*;
import com.opencms.template.*;
import com.opencms.file.*;

import java.util.*;

/**
 * Class for building workplace button separators. <BR>
 * Called by CmsXmlTemplateFile for handling the special XML tag <code>&lt;BUTTONSEPARATOR&gt;</code>.
 * 
 * @author Alexander Lucas
 * @version $Revision: 1.7 $ $Date: 2000/02/02 10:30:44 $
 * @see com.opencms.workplace.CmsXmlWpTemplateFile
 */
public class CmsButtonSeparator extends A_CmsWpElement implements I_CmsWpElement {    
        
    /**
     * Handling of the special workplace <CODE>&lt;BUTTONSEPARATOR&gt;</CODE> tags.
     * <P>
     * Reads the code of a button separator from the buttons definition file
     * and returns the processed code with the actual elements.
     * <P>
     * Button separators can be referenced in any workplace template by <br>
     * <CODE>&lt;BUTTONSEPARATOR/&gt;</CODE>
     * 
     * @param cms A_CmsObject Object for accessing resources.
     * @param n XML element containing the <code>&lt;BUTTONSEPARATOR&gt;</code> tag <em>(not used here)</em>.
     * @param doc Reference to the A_CmsXmlContent object of the initiating XLM document.  
     * @param callingObject reference to the calling object.
     * @param parameters Hashtable containing all user parameters <em>(not used here)</em>.
     * @param lang CmsXmlLanguageFile conataining the currently valid language file <em>(not used here)</em>.
     * @return Processed button separator.
     * @exception CmsException
     */    
    public Object handleSpecialWorkplaceTag(A_CmsObject cms, Element n, A_CmsXmlContent doc, Object callingObject, Hashtable parameters, CmsXmlLanguageFile lang) throws CmsException {

        CmsXmlWpButtonsDefFile buttondef = getButtonDefinitions(cms);
        String result = buttondef.getButtonSeparator(callingObject);
        return result; 
    }                      
}
