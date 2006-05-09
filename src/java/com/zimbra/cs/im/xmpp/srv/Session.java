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

import com.zimbra.cs.im.xmpp.srv.auth.AuthToken;
import org.xmpp.packet.JID;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

/**
 * The session represents a connection between the server and a client (c2s) or
 * another server (s2s) as well as a connection with a component. Authentication and
 * user accounts are associated with c2s connections while s2s has an optional authentication
 * association but no single user user.<p>
 *
 * Obtain object managers from the session in order to access server resources.
 *
 * @author Gaston Dombiak
 */
public abstract class Session implements RoutableChannelHandler {

    /**
     * Version of the XMPP spec supported as MAJOR_VERSION.MINOR_VERSION (e.g. 1.0).
     */
	public static final int MAJOR_VERSION = 1;
    public static final int MINOR_VERSION = 0;

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    protected static String CHARSET = "UTF-8";

    public static final int STATUS_CLOSED = -1;
    public static final int STATUS_CONNECTED = 1;
    public static final int STATUS_STREAMING = 2;
    public static final int STATUS_AUTHENTICATED = 3;

    /**
     * The Address this session is authenticated as.
     */
    private JID address;

    /**
     * The stream id for this session (random and unique).
     */
    private StreamID streamID;

    /**
     * The current session status.
     */
    protected int status = STATUS_CONNECTED;

    /**
     * The connection that this session represents.
     */
    protected Connection conn;

    /**
     * The authentication token for this session.
     */
    protected AuthToken authToken;

    protected SessionManager sessionManager;

    private String serverName;

    private Date startDate = new Date();

    private long lastActiveDate;
    private long clientPacketCount = 0;
    private long serverPacketCount = 0;

    /**
	 * Session temporary data. All data stored in this <code>Map</code> disapear when session
	 * finishes.
	 */
	private Map<String, Object> sessionData = null;
	
    /**
     * Creates a session with an underlying connection and permission protection.
     *
     * @param connection The connection we are proxying
     */
    public Session(String serverName, Connection connection, StreamID streamID) {
        conn = connection;
        this.streamID = streamID;
        this.serverName = serverName;
        String id = streamID.getID();
        this.address = new JID(null, serverName, id);
        this.sessionManager = SessionManager.getInstance();
        sessionData = new TreeMap<String, Object>();
    }

    /**
      * Obtain the address of the user. The address is used by services like the core
      * server packet router to determine if a packet should be sent to the handler.
      * Handlers that are working on behalf of the server should use the generic server
      * hostname address (e.g. server.com).
      *
      * @return the address of the packet handler.
      */
    public JID getAddress() {
        return address;
    }

    /**
     * Sets the new address of this session. The address is used by services like the core
     * server packet router to determine if a packet should be sent to the handler.
     * Handlers that are working on behalf of the server should use the generic server
     * hostname address (e.g. server.com).
     */
    public void setAddress(JID address){
        this.address = address;
    }

    /**
     * Returns the connection associated with this Session.
     *
     * @return The connection for this session
     */
    public Connection getConnection() {
        return conn;
    }

    /**
     * Obtain the current status of this session.
     *
     * @return The status code for this session
     */
    public int getStatus() {
        return status;
    }

    /**
     * Set the new status of this session. Setting a status may trigger
     * certain events to occur (setting a closed status will close this
     * session).
     *
     * @param status The new status code for this session
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Obtain the stream ID associated with this sesison. Stream ID's are generated by the server
     * and should be unique and random.
     *
     * @return This session's assigned stream ID
     */
    public StreamID getStreamID() {
        return streamID;
    }

    /**
     * Obtain the name of the server this session belongs to.
     *
     * @return the server name.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Obtain the date the session was created.
     *
     * @return the session's creation date.
     */
    public Date getCreationDate() {
        return startDate;
    }

    /**
     * Obtain the time the session last had activity.
     *
     * @return The last time the session received activity.
     */
    public Date getLastActiveDate() {
        return new Date(lastActiveDate);
    }

    /**
     * Obtain the number of packets sent from the client to the server.
     */
    public void incrementClientPacketCount() {
        clientPacketCount++;
        lastActiveDate = System.currentTimeMillis();
    }

    /**
     * Obtain the number of packets sent from the server to the client.
     */
    public void incrementServerPacketCount() {
        serverPacketCount++;
        lastActiveDate = System.currentTimeMillis();
    }

    /**
     * Obtain the number of packets sent from the client to the server.
     *
     * @return The number of packets sent from the client to the server.
     */
    public long getNumClientPackets() {
        return clientPacketCount;
    }

    /**
     * Obtain the number of packets sent from the server to the client.
     *
     * @return The number of packets sent from the server to the client.
     */
    public long getNumServerPackets() {
        return serverPacketCount;
    }
    
    /**
	 * Saves given session data. Data are saved to temporary storage only and are accessible during
	 * this session life only and only from this session instance.
	 * 
	 * @param key a <code>String</code> value of stored data key ID.
	 * @param value a <code>Object</code> value of data stored in session.
	 * @see #getSessionData(String)
	 */
	public void setSessionData(String key, Object value) {
		sessionData.put(key, value);
	}

	/**
	 * Retrieves session data. This method gives access to temporary session data only. You can
	 * retrieve earlier saved data giving key ID to receive needed value. Please see
	 * {@link #setSessionData(String, Object)}  description for more details.
	 * 
	 * @param key a <code>String</code> value of stored data ID.
	 * @return a <code>Object</code> value of data for given key.
	 * @see #setSessionData(String, Object)
	 */
	public Object getSessionData(String key) {
		return sessionData.get(key);
	}

    /**
     * Removes session data. Please see {@link #setSessionData(String, Object)} description
     * for more details.
     *
     * @param key a <code>String</code> value of stored data ID.
     * @see #setSessionData(String, Object)
     */
    public void removeSessionData(String key) {
        sessionData.remove(key);
    }

    /**
     * Returns a text with the available stream features. Each subclass may return different
     * values depending whether the session has been authenticated or not.
     *
     * @return a text with the available stream features or <tt>null</tt> to add nothing.
     */
    public abstract String getAvailableStreamFeatures();

    public String toString() {
        return super.toString() + " status: " + status + " address: " + address + " id: " + streamID;
    }

    protected static int[] decodeVersion(String version) {
        int[] answer = new int[] {0 , 0};
        String [] versionString = version.split("\\.");
        answer[0] = Integer.parseInt(versionString[0]);
        answer[1] = Integer.parseInt(versionString[1]);
        return answer;
    }
}