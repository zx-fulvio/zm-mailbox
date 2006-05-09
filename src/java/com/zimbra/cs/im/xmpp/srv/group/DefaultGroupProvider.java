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

package com.zimbra.cs.im.xmpp.srv.group;

import com.zimbra.cs.im.xmpp.database.DbConnectionManager;
import com.zimbra.cs.im.xmpp.util.Log;
import com.zimbra.cs.im.xmpp.srv.XMPPServer;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Database implementation of the GroupManager interface.
 *
 * @author Matt Tucker
 */
public class DefaultGroupProvider implements GroupProvider {

    private static final String INSERT_GROUP =
        "INSERT INTO jiveGroup (groupName, description) VALUES (?, ?)";
    private static final String SAVE_GROUP =
        "UPDATE jiveGroup SET description=? WHERE groupName=?";
    private static final String SET_GROUP_NAME_1 =
        "UPDATE jiveGroup SET groupName=? WHERE groupName=?";
    private static final String SET_GROUP_NAME_2 =
        "UPDATE jiveGroupProp SET groupName=? WHERE groupName=?";
    private static final String SET_GROUP_NAME_3 =
        "UPDATE jiveGroupUser SET groupName=? WHERE groupName=?";
    private static final String DELETE_GROUP_USERS =
        "DELETE FROM jiveGroupUser WHERE groupName=?";
    private static final String DELETE_PROPERTIES =
        "DELETE FROM jiveGroupProp WHERE groupName=?";
    private static final String DELETE_GROUP =
        "DELETE FROM jiveGroup WHERE groupName=?";
    private static final String GROUP_COUNT = "SELECT count(*) FROM jiveGroup";
     private static final String LOAD_ADMINS =
        "SELECT username FROM jiveGroupUser WHERE administrator=1 AND groupName=? ORDER BY username";
    private static final String LOAD_MEMBERS =
        "SELECT username FROM jiveGroupUser WHERE administrator=0 AND groupName=? ORDER BY username";
    private static final String LOAD_GROUP =
        "SELECT description FROM jiveGroup WHERE groupName=?";
    private static final String REMOVE_USER =
        "DELETE FROM jiveGroupUser WHERE groupName=? AND username=?";
    private static final String ADD_USER =
        "INSERT INTO jiveGroupUser (groupName, username, administrator) VALUES (?, ?, ?)";
    private static final String UPDATE_USER =
        "UPDATE jiveGroupUser SET administrator=? WHERE groupName=? AND username=?";
    private static final String USER_GROUPS =
        "SELECT groupName FROM jiveGroupUser WHERE username=?";
    private static final String ALL_GROUPS = "SELECT groupName FROM jiveGroup ORDER BY groupName";

    private XMPPServer server = XMPPServer.getInstance();

    public Group createGroup(String name) throws GroupAlreadyExistsException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_GROUP);
            pstmt.setString(1, name);
            pstmt.setString(2, "");
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        Collection<JID> members = getMembers(name, false);
        Collection<JID> administrators = getMembers(name, true);
        return new Group(name, "", members, administrators);
    }

    public Group getGroup(String name) throws GroupNotFoundException {
        String description = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_GROUP);
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new GroupNotFoundException("Group with name "
                    + name + " not found.");
            }
            description = rs.getString(1);
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        Collection<JID> members = getMembers(name, false);
        Collection<JID> administrators = getMembers(name, true);
        return new Group(name, description, members, administrators);
    }

    public void setDescription(String name, String description)
            throws GroupNotFoundException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SAVE_GROUP);
            pstmt.setString(1, description);
            pstmt.setString(2, name);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
            throw new GroupNotFoundException();
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void setName(String oldName, String newName) throws UnsupportedOperationException,
            GroupAlreadyExistsException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(SET_GROUP_NAME_1);
            pstmt.setString(1, newName);
            pstmt.setString(2, oldName);
            pstmt.executeUpdate();
            pstmt.close();
            pstmt = con.prepareStatement(SET_GROUP_NAME_2);
            pstmt.setString(1, newName);
            pstmt.setString(2, oldName);
            pstmt.executeUpdate();
            pstmt.close();
            pstmt = con.prepareStatement(SET_GROUP_NAME_3);
            pstmt.setString(1, newName);
            pstmt.setString(2, oldName);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
            abortTransaction = true;
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    public void deleteGroup(String groupName) {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            // Remove all users in the group.
            pstmt = con.prepareStatement(DELETE_GROUP_USERS);
            pstmt.setString(1, groupName);
            pstmt.executeUpdate();
            pstmt.close();
            // Remove all properties of the group.
            pstmt = con.prepareStatement(DELETE_PROPERTIES);
            pstmt.setString(1, groupName);
            pstmt.executeUpdate();
            pstmt.close();
            // Remove the group entry.
            pstmt = con.prepareStatement(DELETE_GROUP);
            pstmt.setString(1, groupName);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
            abortTransaction = true;
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    public int getGroupCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GROUP_COUNT);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return count;
    }

    public Collection<Group> getGroups() {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_GROUPS);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return new GroupCollection(groupNames.toArray(new String[groupNames.size()]));
    }

    public Collection<Group> getGroups(Set<String> groupNames) {
        return new GroupCollection(groupNames.toArray(new String[groupNames.size()]));
    }

    public Collection<Group> getGroups(int startIndex, int numResults) {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = DbConnectionManager.createScrollablePreparedStatement(con, ALL_GROUPS);
            ResultSet rs = pstmt.executeQuery();
            DbConnectionManager.scrollResultSet(rs, startIndex);
            int count = 0;
            while (rs.next() && count < numResults) {
                groupNames.add(rs.getString(1));
                count++;
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return new GroupCollection(groupNames.toArray(new String[groupNames.size()]));
    }

    public Collection<Group> getGroups(JID user) {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_GROUPS);
            pstmt.setString(1, server.isLocal(user) ? user.getNode() : user.toString());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return new GroupCollection(groupNames.toArray(new String[groupNames.size()]));
    }

    public void addMember(String groupName, JID user, boolean administrator) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_USER);
            pstmt.setString(1, groupName);
            pstmt.setString(2, server.isLocal(user) ? user.getNode() : user.toString());
            pstmt.setInt(3, administrator ? 1 : 0);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void updateMember(String groupName, JID user, boolean administrator) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_USER);
            pstmt.setInt(1, administrator ? 1 : 0);
            pstmt.setString(2, groupName);
            pstmt.setString(3, server.isLocal(user) ? user.getNode() : user.toString());
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void deleteMember(String groupName, JID user) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(REMOVE_USER);
            pstmt.setString(1, groupName);
            pstmt.setString(2, server.isLocal(user) ? user.getNode() : user.toString());
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public boolean isReadOnly() {
        return false;
    }

    private Collection<JID> getMembers(String groupName, boolean adminsOnly) {
        List<JID> members = new ArrayList<JID>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (adminsOnly) {
                pstmt = con.prepareStatement(LOAD_ADMINS);
            }
            else {
                pstmt = con.prepareStatement(LOAD_MEMBERS);
            }
            pstmt.setString(1, groupName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String user = rs.getString(1);
                JID userJID = null;
                if (user.indexOf('@') == -1) {
                    // Create JID of local user if JID does not match a component's JID
                    if (!server.matchesComponent(userJID)) {
                        userJID = server.createJID(user, null);
                    }
                }
                else {
                    userJID = new JID(user);
                }
                members.add(userJID);
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return members;
    }
}