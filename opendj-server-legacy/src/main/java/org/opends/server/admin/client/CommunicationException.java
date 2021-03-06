/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */

package org.opends.server.admin.client;



import static org.opends.messages.AdminMessages.*;

import org.forgerock.i18n.LocalizableMessage;



/**
 * This exception is thrown when a communications related problem
 * occurs whilst interacting with the Directory Server. This may be
 * caused by problems such as network partitioning, the unavailability
 * of the Directory Server, or other failures on the client or server
 * side.
 */
public class CommunicationException extends AdminClientException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 9093195928501281027L;



  /**
   * Create a communication exception with a default message.
   */
  public CommunicationException() {
    super(ERR_COMMUNICATION_EXCEPTION_DEFAULT.get());
  }



  /**
   * Create a communication exception with a cause and a default
   * message.
   *
   * @param cause
   *          The cause.
   */
  public CommunicationException(Throwable cause) {
    super(ERR_COMMUNICATION_EXCEPTION_DEFAULT_CAUSE.get(cause.getMessage()),
        cause);
  }



  /**
   * Create a communication exception with a message and cause.
   *
   * @param message
   *          The message.
   * @param cause
   *          The cause.
   */
  public CommunicationException(LocalizableMessage message, Throwable cause) {
    super(message, cause);
  }



  /**
   * Create a communication exception with a message.
   *
   * @param message
   *          The message.
   */
  public CommunicationException(LocalizableMessage message) {
    super(message);
  }
}
