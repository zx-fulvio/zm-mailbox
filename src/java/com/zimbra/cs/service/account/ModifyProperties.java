/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimbra Collaboration Suite Server.
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.service.account;

import java.util.Iterator;
import java.util.Map;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.soap.Element;
import com.zimbra.soap.SoapFaultException;
import com.zimbra.soap.WriteOpDocumentHandler;
import com.zimbra.soap.ZimbraSoapContext;
import com.zimbra.cs.zimlet.ZimletUserProperties;

/**
 * 
 * @author jylee
 *
 */
public class ModifyProperties extends WriteOpDocumentHandler {

	public Element handle(Element request, Map context)
			throws ServiceException, SoapFaultException {
		ZimbraSoapContext lc = getZimbraContext(context);
        Account acct = getRequestedAccount(lc);

        ZimletUserProperties props = ZimletUserProperties.getProperties(acct);
        
        for (Iterator it = request.elementIterator(AccountService.E_PROPERTY); it.hasNext(); ) {
            Element e = (Element) it.next();
            props.setProperty(e.getAttribute(AccountService.A_ZIMLET),
            					e.getAttribute(AccountService.A_NAME),
            					e.getText());
        }
        props.saveProperties(acct);
        Element response = lc.createElement(AccountService.MODIFY_PROPERTIES_RESPONSE);
        return response;
	}
}
