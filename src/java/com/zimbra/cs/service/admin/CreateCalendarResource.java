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

package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.service.account.ToXML;
import com.zimbra.cs.util.ZimbraLog;
import com.zimbra.cs.account.CalendarResource;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author jhahm
 */
public class CreateCalendarResource extends AdminDocumentHandler {

    /**
     * must be careful and only create calendar recources
     * for the domain admin!
     */
    public boolean domainAuthSufficient(Map context) {
        return true;
    }

    public Element handle(Element request, Map context) throws ServiceException {

        ZimbraSoapContext lc = getZimbraContext(context);
        Provisioning prov = Provisioning.getInstance();

        String name = request.getAttribute(AdminService.E_NAME).toLowerCase();
        Map attrs = AdminService.getAttrs(request, true);

        if (!canAccessEmail(lc, name))
            throw ServiceException.PERM_DENIED(
                    "cannot access calendar resource account:" + name);

        CalendarResource resource = prov.createCalendarResource(name, attrs);

        ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                new String[] {"cmd", "CreateCalendarResource","name", name},
                attrs));

        Element response = lc.createElement(
                AdminService.CREATE_CALENDAR_RESOURCE_RESPONSE);

        ToXML.encodeCalendarResource(response, resource, true);

        return response;
    }
}
