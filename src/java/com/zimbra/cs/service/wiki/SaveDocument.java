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
package com.zimbra.cs.service.wiki;

import java.io.IOException;
import java.util.Map;

import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePart;

import com.zimbra.cs.mailbox.Document;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.service.FileUploadServlet;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.service.FileUploadServlet.Upload;
import com.zimbra.cs.service.mail.MailService;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.util.ByteUtil;
import com.zimbra.cs.wiki.Wiki;
import com.zimbra.cs.wiki.WikiWord;
import com.zimbra.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

public class SaveDocument extends WikiDocumentHandler {
    private static final String[] TARGET_DOC_PATH = new String[] { MailService.E_DOC, MailService.A_ID };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_DOC_PATH; }

	private static class Doc {
		public byte[] contents;
		public String name;
		public String contentType;
		public Upload up = null;
		public void cleanup() {
			if (up != null) {
                FileUploadServlet.deleteUpload(up);
			}
		}
	}
	
	protected Doc getDocumentDataFromMimePart(OperationContext octxt, Mailbox mbox, String msgid, String part) throws ServiceException {
		Message item = mbox.getMessageById(octxt, Integer.parseInt(msgid));
        MimeMessage mm = item.getMimeMessage();
		Doc doc = new Doc();
		try {
	        MimePart mp = Mime.getMimePart(mm, part);
			doc.contents = ByteUtil.getContent(mp.getInputStream(), 0);
			doc.name = Mime.getFilename(mp);
			doc.contentType = mp.getContentType();
		} catch (Exception e) {
			throw ServiceException.FAILURE("cannot get part "+part+" from message "+msgid, e);
		}
		
		return doc;
	}
	
	protected Doc getDocumentDataFromUpload(ZimbraSoapContext lc, String aid) throws ServiceException {
        Upload up = FileUploadServlet.fetchUpload(lc.getAuthtokenAccountId(), aid, lc.getRawAuthToken());

        Doc doc = new Doc();
        try {
        	doc.up = up;
        	doc.contents = ByteUtil.getContent(up.getInputStream(), 0);
        	doc.name = up.getName();
        	doc.contentType = up.getContentType();
        } catch (IOException ioe) {
       		doc.cleanup();
        	throw ServiceException.FAILURE("cannot get uploaded file", ioe);
        }
        return doc;
	}
	
	@Override
    public boolean isReadOnly() {
        return false;
    }

	@Override
	public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext lc = getZimbraSoapContext(context);
        Wiki wiki = getRequestedWikiNotebook(request, lc);

        Element docElem = request.getElement(MailService.E_DOC);
        Mailbox mbox = Mailbox.getMailboxByAccountId(wiki.getWikiAccount());
        OperationContext octxt = lc.getOperationContext();

        Doc doc;
        Element attElem = docElem.getOptionalElement(MailService.E_UPLOAD);
        if (attElem != null) {
            String aid = attElem.getAttribute(MailService.A_ID, null);
            doc = getDocumentDataFromUpload(lc, aid);
        } else {
        	attElem = docElem.getElement(MailService.E_MSG);
            String msgid = attElem.getAttribute(MailService.A_ID, null);
            String part = attElem.getAttribute(MailService.A_PART, null);
        	doc = getDocumentDataFromMimePart(octxt, mbox, msgid, part);
        }

        
        String name = docElem.getAttribute(MailService.A_NAME, doc.name);
        String ctype = docElem.getAttribute(MailService.A_CONTENT_TYPE, doc.contentType);
        String id = docElem.getAttribute(MailService.A_ID, null);
        int itemId;
        if (id == null) {
        	itemId = 0;
        } else {
        	ItemId iid = new ItemId(id, lc);
        	itemId = iid.getId();
        }

        validateRequest(wiki,
        				itemId,
        				docElem.getAttributeLong(MailService.A_VERSION, 0),
        				name);
        
        WikiWord ww = wiki.createDocument(octxt, ctype, name, getAuthor(lc), doc.contents);
        Document docItem = ww.getWikiItem(octxt);

        Element response = lc.createElement(MailService.SAVE_DOCUMENT_RESPONSE);
        Element m = response.addElement(MailService.E_DOC);
        m.addAttribute(MailService.A_ID, lc.formatItemId(docItem));
        m.addAttribute(MailService.A_VERSION, docItem.getVersion());
        doc.cleanup();
        return response;
	}
}
