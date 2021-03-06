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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.quicksetup.util;

import javax.swing.SwingUtilities;

/**
 * This class defines a thread that will be used to actually perform the
 * processing for a background task.
 * @param <T> type of object returned by the background task fed to this object
 */
class BackgroundTaskThread<T>
      extends Thread
{
  /** The background task that is to be processed. */
  private final BackgroundTask<T> backgroundTask;



  /**
   * Creates a new background task thread that will be used to process the
   * provided task.
   *
   * @param  backgroundTask  The task to be processed.
   */
  public BackgroundTaskThread(BackgroundTask<T> backgroundTask)
  {
    this.backgroundTask = backgroundTask;
  }



  /**
   * Performs the processing associated with the background task.
   */
  public void run()
  {
    try
    {
      final T returnValue = backgroundTask.processBackgroundTask();
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          backgroundTask.backgroundTaskCompleted(returnValue, null);
        }
      });
    }
    catch (final Throwable t)
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          backgroundTask.backgroundTaskCompleted(null, t);
        }
      });
    }
  }
}
