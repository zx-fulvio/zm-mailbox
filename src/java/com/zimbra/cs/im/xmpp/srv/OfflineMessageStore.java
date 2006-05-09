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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.im.xmpp.srv;

import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import com.zimbra.cs.im.xmpp.database.DbConnectionManager;
import com.zimbra.cs.im.xmpp.database.SequenceManager;
import com.zimbra.cs.im.xmpp.util.*;
import com.zimbra.cs.im.xmpp.srv.container.BasicModule;
import com.zimbra.cs.im.xmpp.srv.event.UserEventDispatcher;
import com.zimbra.cs.im.xmpp.srv.event.UserEventListener;
import com.zimbra.cs.im.xmpp.srv.user.User;
import com.zimbra.cs.im.xmpp.srv.user.UserManager;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Represents the user's offline message storage. A message store holds messages that were
 * sent to the user while they were unavailable. The user can retrieve their messages by
 * setting their presence to "available". The messages will then be delivered normally.
 * Offline message storage is optional, in which case a null implementation is returned that
 * always throws UnauthorizedException when adding messages to the store.
 *
 * @author Iain Shigeoka
 */
public class OfflineMessageStore extends BasicModule implements UserEventListener {

    private static final String INSERT_OFFLINE =
        "INSERT INTO jiveOffline (username, messageID, creationDate, messageSize, message) " +
        "VALUES (?, ?, ?, ?, ?)";
    private static final String LOAD_OFFLINE =
        "SELECT message, creationDate FROM jiveOffline WHERE username=?";
    private static final String LOAD_OFFLINE_MESSAGE =
        "SELECT message FROM jiveOffline WHERE username=? AND creationDate=?";
    private static final String SELECT_SIZE_OFFLINE =
        "SELECT SUM(messageSize) FROM jiveOffline WHERE username=?";
    private static final String SELECT_SIZE_ALL_OFFLINE =
        "SELECT SUM(messageSize) FROM jiveOffline";
    private static final String DELETE_OFFLINE =
        "DELETE FROM jiveOffline WHERE username=?";
    private static final String DELETE_OFFLINE_MESSAGE =
        "DELETE FROM jiveOffline WHERE username=? AND creationDate=?";

    private Cache<String, Integer> sizeCache;
    private FastDateFormat dateFormat;

    /**
     * Returns the instance of <tt>OfflineMessageStore</tt> being used by the XMPPServer.
     *
     * @return the instance of <tt>OfflineMessageStore</tt> being used by the XMPPServer.
     */
    public static OfflineMessageStore getInstance() {
        return XMPPServer.getInstance().getOfflineMessageStore();
    }

    /**
     * Pool of SAX Readers. SAXReader is not thread safe so we need to have a pool of readers.
     */
    private BlockingQueue<SAXReader> xmlReaders = new LinkedBlockingQueue<SAXReader>();

    /**
     * Constructs a new offline message store.
     */
    public OfflineMessageStore() {
        super("Offline Message Store");
        dateFormat = FastDateFormat.getInstance("yyyyMMdd'T'HH:mm:ss", TimeZone.getTimeZone("UTC"));
        sizeCache = CacheManager.initializeCache("Offline Message Size", "offlinemessage",
                1024 * 100, JiveConstants.HOUR * 12);
    }

    /**
     * Adds a message to this message store. Messages will be stored and made
     * available for later delivery.
     *
     * @param message the message to store.
     */
    public void addMessage(Message message) {
        if (message == null) {
            return;
        }
        JID recipient = message.getTo();
        String username = recipient.getNode();
        // If the username is null (such as when an anonymous user), don't store.
        if (username == null || !UserManager.getInstance().isRegisteredUser(recipient)) {
            return;
        }
        else
        if (!XMPPServer.getInstance().getServerInfo().getName().equals(recipient.getDomain())) {
            // Do not store messages sent to users of remote servers
            return;
        }

        long messageID = SequenceManager.nextID(JiveConstants.OFFLINE);

        // Get the message in XML format.
        String msgXML = message.getElement().asXML();

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_OFFLINE);
            pstmt.setString(1, username);
            pstmt.setLong(2, messageID);
            pstmt.setString(3, StringUtils.dateToMillis(new java.util.Date()));
            pstmt.setInt(4, msgXML.length());
            pstmt.setString(5, msgXML);
            pstmt.executeUpdate();
        }

        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }

        // Update the cached size if it exists.
        if (sizeCache.containsKey(username)) {
            int size = sizeCache.get(username);
            size += msgXML.length();
            sizeCache.put(username, size);
        }
    }

    /**
     * Returns a Collection of all messages in the store for a user.
     * Messages may be deleted after being selected from the database depending on
     * the delete param.
     *
     * @param username the username of the user who's messages you'd like to receive.
     * @param delete true if the offline messages should be deleted.
     * @return An iterator of packets containing all offline messages.
     */
    public Collection<OfflineMessage> getMessages(String username, boolean delete) {
        List<OfflineMessage> messages = new ArrayList<OfflineMessage>();
        Connection con = null;
        PreparedStatement pstmt = null;
        SAXReader xmlReader = null;
        try {
            // Get a sax reader from the pool
            xmlReader = xmlReaders.take();
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_OFFLINE);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String msgXML = rs.getString(1);
                Date creationDate = new Date(Long.parseLong(rs.getString(2).trim()));
                OfflineMessage message = new OfflineMessage(creationDate,
                        xmlReader.read(new StringReader(msgXML)).getRootElement());
                // Add a delayed delivery (JEP-0091) element to the message.
                Element delay = message.addChildElement("x", "jabber:x:delay");
                delay.addAttribute("from", XMPPServer.getInstance().getServerInfo().getName());
                delay.addAttribute("stamp", dateFormat.format(creationDate));
                messages.add(message);
            }
            rs.close();
            // Check if the offline messages loaded should be deleted
            if (delete) {
                pstmt.close();

                pstmt = con.prepareStatement(DELETE_OFFLINE);
                pstmt.setString(1, username);
                pstmt.executeUpdate();
                
                removeUsernameFromSizeCache(username);
            }
        }
        catch (Exception e) {
            Log.error("Error retrieving offline messages of username: " + username, e);
        }
        finally {
            // Return the sax reader to the pool
            if (xmlReader != null) xmlReaders.add(xmlReader);
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return messages;
    }

    /**
     * Returns the offline message of the specified user with the given creation date. The
     * returned message will NOT be deleted from the database.
     *
     * @param username the username of the user who's message you'd like to receive.
     * @param creationDate the date when the offline message was stored in the database.
     * @return the offline message of the specified user with the given creation stamp.
     */
    public OfflineMessage getMessage(String username, Date creationDate) {
        OfflineMessage message = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        SAXReader xmlReader = null;
        try {
            // Get a sax reader from the pool
            xmlReader = xmlReaders.take();
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_OFFLINE_MESSAGE);
            pstmt.setString(1, username);
            pstmt.setString(2, StringUtils.dateToMillis(creationDate));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String msgXML = rs.getString(1);
                message =
                        new OfflineMessage(creationDate,
                                xmlReader.read(new StringReader(msgXML)).getRootElement());
                // Add a delayed delivery (JEP-0091) element to the message.
                Element delay = message.addChildElement("x", "jabber:x:delay");
                delay.addAttribute("from", XMPPServer.getInstance().getServerInfo().getName());
                delay.addAttribute("stamp", dateFormat.format(creationDate));
            }
            rs.close();
        }
        catch (Exception e) {
            Log.error("Error retrieving offline messages of username: " + username +
                    " creationDate: " + creationDate, e);
        }
        finally {
            // Return the sax reader to the pool
            if (xmlReader != null) xmlReaders.add(xmlReader);
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return message;
    }

    /**
     * Deletes all offline messages in the store for a user.
     *
     * @param username the username of the user who's messages are going to be deleted.
     */
    public void deleteMessages(String username) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_OFFLINE);
            pstmt.setString(1, username);
            pstmt.executeUpdate();
            
            removeUsernameFromSizeCache(username);
        }
        catch (Exception e) {
            Log.error("Error deleting offline messages of username: " + username, e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }

    private void removeUsernameFromSizeCache(String username) {
        // Update the cached size if it exists.
        if (sizeCache.containsKey(username)) {
            sizeCache.remove(username);
        }
    }

    /**
     * Deletes the specified offline message in the store for a user. The way to identify the
     * message to delete is based on the creationDate and username.
     *
     * @param username the username of the user who's message is going to be deleted.
     * @param creationDate the date when the offline message was stored in the database.
     */
    public void deleteMessage(String username, Date creationDate) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_OFFLINE_MESSAGE);
            pstmt.setString(1, username);
            pstmt.setString(2, StringUtils.dateToMillis(creationDate));
            pstmt.executeUpdate();
            
            //force a refresh for next call to getSize(username)
            //its easier than loading the msg to be deleted just
            //to update the cache.
            removeUsernameFromSizeCache(username);
        }
        catch (Exception e) {
            Log.error("Error deleting offline messages of username: " + username +
                    " creationDate: " + creationDate, e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Returns the approximate size (in bytes) of the XML messages stored for
     * a particular user.
     *
     * @param username the username of the user.
     * @return the approximate size of stored messages (in bytes).
     */
    public int getSize(String username) {
        // See if the size is cached.
        if (sizeCache.containsKey(username)) {
            return sizeCache.get(username);
        }
        int size = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_SIZE_OFFLINE);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                size = rs.getInt(1);
            }
            rs.close();
            // Add the value to cache.
            sizeCache.put(username, size);
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return size;
    }

    /**
     * Returns the approximate size (in bytes) of the XML messages stored for all
     * users.
     *
     * @return the approximate size of all stored messages (in bytes).
     */
    public int getSize() {
        int size = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_SIZE_ALL_OFFLINE);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                size = rs.getInt(1);
            }
            rs.close();
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return size;
    }

    public void userCreated(User user, Map params) {
        //Do nothing
    }

    public void userDeleting(User user, Map params) {
        // Delete all offline messages of the user
        deleteMessages(user.getUsername());
    }

    public void userModified(User user, Map params) {
        //Do nothing
    }

    public void start() throws IllegalStateException {
        super.start();
        // Initialize the pool of sax readers
        for (int i=0; i<10; i++) {
            xmlReaders.add(new SAXReader());
        }
        // Add this module as a user event listener so we can delete
        // all offline messages when a user is deleted
        UserEventDispatcher.addListener(this);
    }

    public void stop() {
        super.stop();
        // Clean up the pool of sax readers
        xmlReaders.clear();
        // Remove this module as a user event listener
        UserEventDispatcher.removeListener(this);
    }
}