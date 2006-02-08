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

package com.zimbra.cs.ozserver;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface OzConnectionHandler {
    void handleConnect() throws IOException;

    void handleInput(ByteBuffer buffer, boolean matched) throws IOException;

    /* The contract of handleDisconnect is that you must call OzConnection.closeNow(), and not close(). */
    void handleDisconnect();
    
    void handleIdle() throws IOException;

	/**
     * Write an error message depending on the state you are in and then either close the connection 
     * or go back to a state that is safe - like reading commands
     * 
	 * @throws IOException
	 */
	void handleOverflow() throws IOException;
}
