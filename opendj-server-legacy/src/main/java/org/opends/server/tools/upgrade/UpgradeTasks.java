/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.tools.upgrade;

import static javax.security.auth.callback.ConfirmationCallback.NO;
import static javax.security.auth.callback.ConfirmationCallback.YES;
import static javax.security.auth.callback.TextOutputCallback.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.upgrade.FileManager.copy;
import static org.opends.server.tools.upgrade.Installation.CURRENT_CONFIG_FILE_NAME;
import static org.opends.server.tools.upgrade.UpgradeUtils.*;
import static org.opends.server.util.StaticUtils.isClassAvailable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.security.auth.callback.TextOutputCallback;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldif.EntryReader;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.tools.JavaPropertiesTool;
import org.opends.server.tools.RebuildIndex;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.ChangeOperationType;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ReturnCode;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

/**
 * Factory methods for create new upgrade tasks.
 */
public final class UpgradeTasks
{
  /** Logger for the upgrade. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** An errors counter in case of ignore errors mode. */
  static int countErrors;

  /** Contains all the indexes to rebuild. */
  static Set<String> indexesToRebuild = new HashSet<>();

  /** A flag to avoid rebuild single indexes if 'rebuild all' is selected. */
  static boolean isRebuildAllIndexesIsPresent;

  /** A flag for marking 'rebuild all' task accepted by user. */
  static boolean isRebuildAllIndexesTaskAccepted;

  /**
   * Returns a new upgrade task which adds a config entry to the underlying
   * config file.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @param ldif
   *          The LDIF record which will be applied to matching entries.
   * @return A new upgrade task which applies an LDIF record to all
   *         configuration entries matching the provided filter.
   */
  public static UpgradeTask addConfigEntry(final LocalizableMessage summary,
      final String... ldif)
  {
    return updateConfigEntry(summary, null, ChangeOperationType.ADD, ldif);
  }

  /**
   * This task copies the file placed in parameter within the config / schema
   * folder. If the file already exists, it's overwritten.
   *
   * @param fileName
   *          The name of the file which need to be copied.
   * @return A task which copy the the file placed in parameter within the
   *         config / schema folder. If the file already exists, it's
   *         overwritten.
   */
  public static UpgradeTask copySchemaFile(final String fileName)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        final LocalizableMessage msg = INFO_UPGRADE_TASK_REPLACE_SCHEMA_FILE.get(fileName);
        logger.debug(msg);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, msg, 0);

        final File schemaFileTemplate =
            new File(templateConfigSchemaDirectory, fileName);

        try
        {
          context.notifyProgress(pnc.setProgress(20));
          if (!schemaFileTemplate.exists() || schemaFileTemplate.length() == 0)
          {
            throw new IOException(ERR_UPGRADE_CORRUPTED_TEMPLATE
                .get(schemaFileTemplate.getPath()).toString());
          }
          copy(schemaFileTemplate, configSchemaDirectory, true);
          context.notifyProgress(pnc.setProgress(100));
        }
        catch (final IOException e)
        {
          manageTaskException(context, ERR_UPGRADE_COPYSCHEMA_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()), pnc);
        }
      }
    };
  }

  /**
   * This task copies the file placed in parameter within the config folder. If
   * the file already exists, it's overwritten.
   *
   * @param fileName
   *          The name of the file which need to be copied.
   * @return A task which copy the the file placed in parameter within the
   *         config folder. If the file already exists, it's overwritten.
   */
  public static UpgradeTask addConfigFile(final String fileName)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        final LocalizableMessage msg = INFO_UPGRADE_TASK_ADD_CONFIG_FILE.get(fileName);
        logger.debug(msg);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, msg, 0);

        final File configFile = new File(templateConfigDirectory, fileName);

        try
        {
          context.notifyProgress(pnc.setProgress(20));

          copy(configFile, configDirectory, true);
          context.notifyProgress(pnc.setProgress(100));
        }
        catch (final IOException e)
        {
          manageTaskException(context, ERR_UPGRADE_ADD_CONFIG_FILE_FAILS.get(
              configFile.getName(), e.getMessage()), pnc);
        }
      }
    };
  }

  /**
   * Returns a new upgrade task which deletes a config entry from the underlying
   * config file.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @param dnInLDIF
   *          The dn to delete in the form of LDIF.
   * @return A new upgrade task which applies an LDIF record to all
   *         configuration entries matching the provided filter.
   */
  public static UpgradeTask deleteConfigEntry(final LocalizableMessage summary,
      final String dnInLDIF)
  {
    return updateConfigEntry(summary, null, ChangeOperationType.DELETE, dnInLDIF);
  }

  /**
   * Returns a new upgrade task which applies an LDIF record to all
   * configuration entries matching the provided filter.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @param filter
   *          The LDAP filter which configuration entries must match.
   * @param ldif
   *          The LDIF record which will be applied to matching entries.
   * @return A new upgrade task which applies an LDIF record to all
   *         configuration entries matching the provided filter.
   */
  public static UpgradeTask modifyConfigEntry(final LocalizableMessage summary,
      final String filter, final String... ldif)
  {
    return updateConfigEntry(summary, filter, ChangeOperationType.MODIFY, ldif);
  }

  /**
   * This task adds a new attribute type (must exists in the original file) to
   * the specified file placed in parameter. The destination must be a file
   * contained in the config/schema folder. E.g : This example adds a new
   * attribute type named 'etag' in the 00.core.ldif. The 'etag' attribute
   * already exists in the 00-core.ldif template schema file.
   *
   * <pre>
   * register(&quot;2.5.0.7192&quot;,
   *   newAttributeTypes(LocalizableMessage.raw(&quot;New attribute etag&quot;),
   *   false, &quot;00-core.ldif&quot;,
   *   &quot;1.3.6.1.4.1.36733.2.1.1.59&quot;));
   * </pre>
   *
   * @param summary
   *          The summary of the task.
   * @param fileName
   *          The file where to add the new attribute types. This file must be
   *          contained in the configuration/schema folder.
   * @param attributeOids
   *          The OIDs of the new attributes to add to.
   * @return An upgrade task which adds new attribute types, defined previously
   *         in the configuration template files, reads the definition
   *         and adds it onto the specified file in parameter.
   */
  public static UpgradeTask newAttributeTypes(final LocalizableMessage summary,
      final String fileName, final String... attributeOids)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        logger.debug(summary);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, summary, 20);
        context.notifyProgress(pnc);

        final File schemaFileTemplate =
            new File(templateConfigSchemaDirectory, fileName);

        final File pathDestination = new File(configSchemaDirectory, fileName);
        try
        {
          final int changeCount =
              updateSchemaFile(schemaFileTemplate, pathDestination,
                  attributeOids, null);

          displayChangeCount(pathDestination.getPath(), changeCount);

          context.notifyProgress(pnc.setProgress(100));
        }
        catch (final IOException | IllegalStateException e)
        {
          manageTaskException(context, ERR_UPGRADE_ADDATTRIBUTE_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()), pnc);
        }
      }
    };
  }

  /**
   * This task adds a new object class (must exists in the original file) to the
   * specified file placed in parameter. The destination must be a file
   * contained in the config/schema folder.
   *
   * @param summary
   *          The summary of the task.
   * @param fileName
   *          The file where to add the new object classes. This file must be
   *          contained in the configuration/schema folder.
   * @param objectClassesOids
   *          The OIDs of the new object classes to add to.
   * @return An upgrade task which adds new object classes, defined previously
   *         in the configuration template files,
   *         reads the definition and adds it onto the specified file in
   *         parameter.
   */
  public static UpgradeTask newObjectClasses(final LocalizableMessage summary,
      final String fileName, final String... objectClassesOids)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        logger.debug(summary);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, summary, 20);
        context.notifyProgress(pnc);

        final File schemaFileTemplate =
            new File(templateConfigSchemaDirectory, fileName);

        final File pathDestination = new File(configSchemaDirectory, fileName);

        context.notifyProgress(pnc.setProgress(20));

        try
        {
          final int changeCount =
              updateSchemaFile(schemaFileTemplate, pathDestination,
                  null, objectClassesOids);

          displayChangeCount(pathDestination.getPath(), changeCount);

          context.notifyProgress(pnc.setProgress(100));
        }
        catch (final IOException e)
        {
          manageTaskException(context, ERR_UPGRADE_ADDOBJECTCLASS_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()), pnc);
        }
        catch (final IllegalStateException e)
        {
          manageTaskException(context, ERR_UPGRADE_ADDATTRIBUTE_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()), pnc);
        }
      }
    };
  }

  /**
   * Re-run the dsjavaproperties tool to rewrite the set-java-home script/batch file.
   *
   * @param summary
   *          The summary of the task.
   * @return An upgrade task which runs dsjavaproperties.
   */
  public static UpgradeTask rerunJavaPropertiesTool(final LocalizableMessage summary)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(UpgradeContext context) throws ClientException
      {
        logger.debug(summary);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, summary, 50);
        context.notifyProgress(pnc);

        int returnValue = JavaPropertiesTool.mainCLI("--quiet");
        context.notifyProgress(pnc.setProgress(100));

        if (JavaPropertiesTool.ErrorReturnCode.SUCCESSFUL.getReturnCode() != returnValue &&
                JavaPropertiesTool.ErrorReturnCode.SUCCESSFUL_NOP.getReturnCode() != returnValue) {
          throw new ClientException(ReturnCode.ERROR_UNEXPECTED, ERR_UPGRADE_DSJAVAPROPERTIES_FAILED.get());
        }
      }
    };
  }

  /**
   * Creates a group of tasks which will only be invoked if the current version
   * is more recent than the provided version. This may be useful in cases where
   * a regression was introduced in version X and resolved in a later version Y.
   * In this case, the provided upgrade tasks will only be invoked if the
   * current version is between X (inclusive) and Y (exclusive).
   *
   * @param versionString
   *          The lower bound version. The upgrade tasks will not be applied if
   *          the current version is older than this version.
   * @param tasks
   *          The group of tasks to invoke if the current version is equal to or
   *          more recent than {@code versionString}.
   * @return An upgrade task which will only be invoked if the current version
   *         is more recent than the provided version.
   */
  public static UpgradeTask regressionInVersion(final String versionString, final UpgradeTask... tasks)
  {
    final BuildVersion version = BuildVersion.valueOf(versionString);
    return conditionalUpgradeTasks(new UpgradeCondition()
    {
      @Override
      public boolean shouldPerformUpgradeTasks(final UpgradeContext context) throws ClientException
      {
        return context.getFromVersion().compareTo(version) >= 0;
      }
    }, tasks);
  }

  /**
   * Creates a group of tasks which will only be invoked if the user confirms agreement. This may be
   * useful in cases where a feature is deprecated and the upgrade is capable of migrating the
   * configuration to the new replacement feature.
   *
   * @param message
   *          The confirmation message.
   * @param tasks
   *          The group of tasks to invoke if the user agrees.
   * @return An upgrade task which will only be invoked if the user confirms agreement.
   */
  static UpgradeTask requireConfirmation(
          final LocalizableMessage message, final int defaultResponse, final UpgradeTask... tasks)
  {
    return conditionalUpgradeTasks(new UpgradeCondition()
    {
      @Override
      public boolean shouldPerformUpgradeTasks(final UpgradeContext context) throws ClientException
      {
        return context.confirmYN(INFO_UPGRADE_TASK_NEEDS_USER_CONFIRM.get(message), defaultResponse) == YES;
      }
    }, tasks);
  }

  /** Determines whether conditional tasks should be performed. */
  interface UpgradeCondition
  {
    boolean shouldPerformUpgradeTasks(final UpgradeContext context) throws ClientException;
  }

  static UpgradeTask conditionalUpgradeTasks(final UpgradeCondition condition, final UpgradeTask... tasks)
  {
    return new AbstractUpgradeTask()
    {
      private boolean shouldPerformUpgradeTasks = true;

      @Override
      public void prepare(final UpgradeContext context) throws ClientException
      {
        shouldPerformUpgradeTasks = condition.shouldPerformUpgradeTasks(context);
        if (shouldPerformUpgradeTasks)
        {
          for (UpgradeTask task : tasks)
          {
            task.prepare(context);
          }
        }
      }

      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        if (shouldPerformUpgradeTasks)
        {
          for (UpgradeTask task : tasks)
          {
            task.perform(context);
          }
        }
      }

      @Override
      public void postUpgrade(UpgradeContext context) throws ClientException
      {
        if (shouldPerformUpgradeTasks)
        {
          boolean isOk = true;
          for (final UpgradeTask task : tasks)
          {
            if (isOk)
            {
              try
              {
                task.postUpgrade(context);
              }
              catch (ClientException e)
              {
                logger.error(LocalizableMessage.raw(e.getMessage()));
                isOk = false;
              }
            }
            else
            {
              task.postponePostUpgrade(context);
            }
          }
        }
      }

    };
  }

  /**
   * Creates a rebuild all indexes task.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @return An Upgrade task which rebuild all the indexes.
   */
  public static UpgradeTask rebuildAllIndexes(final LocalizableMessage summary)
  {
    return new AbstractUpgradeTask()
    {
      private boolean isATaskToPerform = false;

      @Override
      public void prepare(UpgradeContext context) throws ClientException
      {
        Upgrade.setHasPostUpgradeTask(true);
        // Requires answer from the user.
        isATaskToPerform = context.confirmYN(summary, NO) == YES;
        isRebuildAllIndexesIsPresent = true;
        isRebuildAllIndexesTaskAccepted = isATaskToPerform;
      }

      @Override
      public void postUpgrade(final UpgradeContext context) throws ClientException
      {
        if (!isATaskToPerform)
        {
          postponePostUpgrade(context);
        }
      }

      @Override
      public void postponePostUpgrade(UpgradeContext context) throws ClientException
      {
        context.notify(INFO_UPGRADE_ALL_REBUILD_INDEX_DECLINED.get(), TextOutputCallback.WARNING);
      }
    };
  }



  /**
   * Creates a rebuild index task for a given single index. As this task is
   * possibly lengthy, it's considered as a post upgrade task. This task is not
   * mandatory; e.g not require user interaction, but could be required to get a
   * fully functional server. <br />
   * The post upgrade task just register the task. The rebuild indexes tasks are
   * completed at the end of the upgrade process.
   *
   * @param summary
   *          A message describing why the index needs to be rebuilt and asking
   *          them whether or not they wish to perform this task after the
   *          upgrade.
   * @param index
   *          The index to rebuild.
   * @return The rebuild index task.
   */
  public static UpgradeTask rebuildSingleIndex(final LocalizableMessage summary,
      final String index)
  {
    return new AbstractUpgradeTask()
    {
      private boolean isATaskToPerform = false;

      @Override
      public void prepare(UpgradeContext context) throws ClientException
      {
        Upgrade.setHasPostUpgradeTask(true);
        // Requires answer from the user.
        isATaskToPerform = context.confirmYN(summary, NO) == YES;
      }

      @Override
      public void postUpgrade(final UpgradeContext context) throws ClientException
      {
        if (isATaskToPerform)
        {
          indexesToRebuild.add(index);
        }
        else
        {
          postponePostUpgrade(context);
        }
      }

      @Override
      public void postponePostUpgrade(UpgradeContext context) throws ClientException
      {
        if (!isRebuildAllIndexesIsPresent)
        {
          context.notify(INFO_UPGRADE_REBUILD_INDEX_DECLINED.get(index), TextOutputCallback.WARNING);
        }
      }
    };
  }

  /**
   * This task is processed at the end of the upgrade, rebuilding indexes. If a
   * rebuild all indexes has been registered before, it takes the flag
   * relatively to single rebuild index.
   *
   * @return The post upgrade rebuild indexes task.
   */
  public static UpgradeTask postUpgradeRebuildIndexes()
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void postUpgrade(final UpgradeContext context) throws ClientException
      {
        LocalizableMessage message = null;
        final List<String> args = new LinkedList<>();

        if (isRebuildAllIndexesIsPresent && isRebuildAllIndexesTaskAccepted)
        {
          args.add("--rebuildAll");
          message = INFO_UPGRADE_REBUILD_ALL.get();
        }
        else if (!indexesToRebuild.isEmpty()
            && !isRebuildAllIndexesTaskAccepted)
        {
          message = INFO_UPGRADE_REBUILD_INDEX_STARTS.get(indexesToRebuild);

          // Adding all requested indexes.
          for (final String indexToRebuild : indexesToRebuild)
          {
            args.add("-i");
            args.add(indexToRebuild);
          }
        }
        else
        {
          return;
        }
        // Startup message.
        ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, message, 25);
        logger.debug(message);
        context.notifyProgress(pnc);

        // Sets the arguments like the rebuild index command line.
        args.addAll(Arrays.asList(
            "-f",
            new File(configDirectory, CURRENT_CONFIG_FILE_NAME).getAbsolutePath()));

        /*
         * Index(es) could be contained in several backends or none, If none,
         * the post upgrade tasks succeed and a message is printed in the
         * upgrade log file.
         */
        final List<String> backends = UpgradeUtils.getIndexedBackendsFromConfig();
        if (!backends.isEmpty())
        {
          for (final String be : backends)
          {
            args.add("-b");
            args.add(be);
          }

          // Displays info about command line args for log only.
          logger.debug(INFO_UPGRADE_REBUILD_INDEX_ARGUMENTS, args);

          /*
           * The rebuild-index process just display a status ok / fails. The
           * logger stream contains all the log linked to this process. The
           * complete process is not displayed in the upgrade console.
           */
          final String[] commandLineArgs = args.toArray(new String[args.size()]);
          final int result = new RebuildIndex().rebuildIndexesWithinMultipleBackends(
              true, UpgradeLog.getPrintStream(), commandLineArgs);

          if (result == 0)
          {
            logger.debug(INFO_UPGRADE_REBUILD_INDEX_ENDS);
            context.notifyProgress(pnc.setProgress(100));
          }
          else
          {
            final LocalizableMessage msg = ERR_UPGRADE_PERFORMING_POST_TASKS_FAIL.get();
            context.notifyProgress(pnc.setProgress(-100));
            throw new ClientException(ReturnCode.ERROR_UNEXPECTED, msg);
          }
        }
        else
        {
          logger.debug(INFO_UPGRADE_REBUILD_INDEX_NO_BACKEND_FOUND);
          logger.debug(INFO_UPGRADE_REBUILD_INDEX_DECLINED, indexesToRebuild);
          context.notifyProgress(pnc.setProgress(100));
        }
      }
    };
  }

  /**
   * Creates a file object representing config/upgrade/schema.ldif.current which
   * the server creates the first time it starts if there are schema
   * customizations.
   *
   * @return An upgrade task which upgrade the config/upgrade folder, creating a
   *         new schema.ldif.rev which is needed after schema customization for
   *         starting correctly the server.
   */
  public static UpgradeTask updateConfigUpgradeFolder()
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        final LocalizableMessage msg = INFO_UPGRADE_TASK_REFRESH_UPGRADE_DIRECTORY.get();
        logger.debug(msg);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, msg, 20);
        context.notifyProgress(pnc);

        try
        {
          String toRevision = String.valueOf(context.getToVersion().getRevision());
          updateConfigUpgradeSchemaFile(configSchemaDirectory, toRevision);

          context.notifyProgress(pnc.setProgress(100));
        }
        catch (final Exception ex)
        {
          manageTaskException(context, ERR_UPGRADE_CONFIG_ERROR_UPGRADE_FOLDER.get(ex.getMessage()), pnc);
        }
      }
    };
  }

  /**
   * Renames the SNMP security config file if it exists. Since 2.5.0.7466 this
   * file has been renamed.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @return An upgrade task which renames the old SNMP security config file if
   *         it exists.
   */
  public static UpgradeTask renameSnmpSecurityConfig(final LocalizableMessage summary)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        /*
         * Snmp config file contains old name in old version(like 2.4.5), in
         * order to make sure the process will still work after upgrade, we need
         * to rename it - only if it exists.
         */
        final File snmpDir = UpgradeUtils.configSnmpSecurityDirectory;
        if (snmpDir.exists())
        {
          ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, summary, 0);
          try
          {
            final File oldSnmpConfig = new File(snmpDir, "opends-snmp.security");
            if (oldSnmpConfig.exists())
            {
              context.notifyProgress(pnc.setProgress(20));
              logger.debug(summary);

              final File snmpConfig = new File(snmpDir, "opendj-snmp.security");
              FileManager.rename(oldSnmpConfig, snmpConfig);

              context.notifyProgress(pnc.setProgress(100));
            }
          }
          catch (final Exception ex)
          {
            LocalizableMessage msg = ERR_UPGRADE_RENAME_SNMP_SECURITY_CONFIG_FILE.get(ex.getMessage());
            manageTaskException(context, msg, pnc);
          }
        }
      }
    };
  }

  /**
   * Removes the specified file from the file-system.
   *
   * @param file
   *          The file to be removed.
   * @return An upgrade task which removes the specified file from the file-system.
   */
  public static UpgradeTask deleteFile(final File file)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(UpgradeContext context) throws ClientException
      {
        LocalizableMessage msg = INFO_UPGRADE_TASK_DELETE_FILE.get(file);
        ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, msg, 0);
        context.notifyProgress(pnc);
        try
        {
          FileManager.deleteRecursively(file);
          context.notifyProgress(pnc.setProgress(100));
        }
        catch (Exception e)
        {
          manageTaskException(context, LocalizableMessage.raw(e.getMessage()), pnc);
        }
      }
    };
  }

  /**
   * Creates an upgrade task which is responsible for preparing local-db backend JE databases for a full rebuild once
   * they have been converted to pluggable JE backends.
   *
   * @return An upgrade task which is responsible for preparing local-db backend JE databases.
   */
  public static UpgradeTask migrateLocalDBBackendsToJEBackends() {
    return new AbstractUpgradeTask() {
      /** Properties of JE backends to be migrated. */
      class Backend {
        final String id;
        final boolean isEnabled;
        final Set<DN> baseDNs;
        final File envDir;
        final Map<String, String> renamedDbs = new HashMap<>();

        private Backend(Entry config) {
          id = config.parseAttribute("ds-cfg-backend-id").asString();
          isEnabled = config.parseAttribute("ds-cfg-enabled").asBoolean(false);
          baseDNs = config.parseAttribute("ds-cfg-base-dn").asSetOfDN();
          String dbDirectory = config.parseAttribute("ds-cfg-db-directory").asString();
          File backendParentDirectory = new File(dbDirectory);
          if (!backendParentDirectory.isAbsolute()) {
            backendParentDirectory = new File(getInstancePath(), dbDirectory);
          }
          envDir = new File(backendParentDirectory, id);
          for (String db : Arrays.asList("compressed_attributes", "compressed_object_classes")) {
            renamedDbs.put(db, new TreeName("compressed_schema", db).toString());
          }
          for (DN baseDN : baseDNs) {
            renamedDbs.put(oldName(baseDN), newName(baseDN));
          }
        }
      }

      private final List<Backend> backends = new LinkedList<>();

      /**
       * Finds all the existing JE backends and determines if they can be migrated or not. It will not be possible to
       * migrate a JE backend if the id2entry database name cannot easily be determined, which may happen because
       * matching rules have changed significantly in 3.0.0.
       */
      @Override
      public void prepare(final UpgradeContext context) throws ClientException {
        // Requires answer from the user.
        if (context.confirmYN(INFO_UPGRADE_TASK_MIGRATE_JE_DESCRIPTION.get(), NO) != YES) {
          throw new ClientException(ReturnCode.ERROR_USER_CANCELLED,
                                    INFO_UPGRADE_TASK_MIGRATE_JE_CANCELLED.get());
        }

        final SearchRequest sr = Requests.newSearchRequest("", SearchScope.WHOLE_SUBTREE,
                                                           "(objectclass=ds-cfg-local-db-backend)");
        try (final EntryReader entryReader = searchConfigFile(sr)) {
          // Abort the upgrade if there are JE backends but no JE library.
          if (entryReader.hasNext() && !isJeLibraryAvailable()) {
            throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, INFO_UPGRADE_TASK_MIGRATE_JE_NO_JE_LIB.get());
          }
          while (entryReader.hasNext()) {
            Backend backend = new Backend(entryReader.readEntry());
            if (backend.isEnabled) {
              abortIfBackendCannotBeMigrated(backend);
            }
            backends.add(backend);
          }
        } catch (IOException e) {
          throw new ClientException(ReturnCode.APPLICATION_ERROR, INFO_UPGRADE_TASK_MIGRATE_CONFIG_READ_FAIL.get(), e);
        }
      }

      private void abortIfBackendCannotBeMigrated(final Backend backend) throws ClientException {
        Set<String> existingDatabases = JEHelper.listDatabases(backend.envDir);
        for (DN baseDN : backend.baseDNs) {
          final String oldName = oldName(baseDN);
          if (!existingDatabases.contains(oldName)) {
            LocalizableMessage msg = INFO_UPGRADE_TASK_MIGRATE_JE_UGLY_DN.get(backend.id, baseDN);
            throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
          }
        }
      }

      /**
       * Renames the compressed schema indexes and id2entry in a 2.x environment to
       * the naming scheme used in 3.0.0. Before 3.0.0 JE databases were named as follows:
       *
       * 1) normalize the base DN
       * 2) replace all non-alphanumeric characters with '_'
       * 3) append '_'
       * 4) append the index name.
       *
       * For example, id2entry in the base DN dc=white space,dc=com would be named
       * dc_white_space_dc_com_id2entry. In 3.0.0 JE databases are named as follows:
       *
       * 1) normalize the base DN and URL encode it (' '  are converted to %20)
       * 2) format as '/' + URL encoded base DN + '/' + index name.
       *
       * The matching rules in 3.0.0 are not compatible with previous versions, so we need
       * to do a best effort attempt to figure out the old database name from a given base DN.
       */
      @Override
      public void perform(final UpgradeContext context) throws ClientException {
        if (!isJeLibraryAvailable()) {
          return;
        }

        for (Backend backend : backends) {
          if (backend.isEnabled) {
            ProgressNotificationCallback pnc = new ProgressNotificationCallback(
                    INFORMATION, INFO_UPGRADE_TASK_MIGRATE_JE_SUMMARY_1.get(backend.id), 0);
            context.notifyProgress(pnc);
            try {
              JEHelper.migrateDatabases(backend.envDir, backend.renamedDbs);
              context.notifyProgress(pnc.setProgress(100));
            } catch (ClientException e) {
              manageTaskException(context, e.getMessageObject(), pnc);
            }
          } else {
            // Skip backends which have been disabled.
            final ProgressNotificationCallback pnc = new ProgressNotificationCallback(
                    INFORMATION, INFO_UPGRADE_TASK_MIGRATE_JE_SUMMARY_5.get(backend.id), 0);
            context.notifyProgress(pnc);
            context.notifyProgress(pnc.setProgress(100));
          }
        }
      }

      private boolean isJeLibraryAvailable() {
        return isClassAvailable("com.sleepycat.je.Environment");
      }

      private String newName(final DN baseDN) {
        return new TreeName(baseDN.toNormalizedUrlSafeString(), "id2entry").toString();
      }

      private String oldName(final DN baseDN) {
        String s = baseDN.toString();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
          char c = s.charAt(i);
          builder.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        builder.append("_id2entry");
        return builder.toString();
      }
    };
  }

  /**
   * Creates backups of the local DB backends directories by renaming adding them a ".bak" suffix.
   *  e.g "userRoot" would become "userRoot.bak"
   */
  static UpgradeTask renameLocalDBBackendDirectories()
  {
    return new AbstractUpgradeTask()
    {
      private boolean reimportRequired = false;

      @Override
      public void perform(UpgradeContext context) throws ClientException
      {
        try
        {
          Filter filter = Filter.equality("objectclass", "ds-cfg-local-db-backend");
          SearchRequest findLocalDBBackends = Requests.newSearchRequest(DN.rootDN(), SearchScope.WHOLE_SUBTREE, filter);
          try (final EntryReader jeBackends = searchConfigFile(findLocalDBBackends))
          {
            while (jeBackends.hasNext())
            {
              Upgrade.setHasPostUpgradeTask(true);
              reimportRequired = true;

              Entry jeBackend = jeBackends.readEntry();
              File dbParent = UpgradeUtils.getFileForPath(jeBackend.parseAttribute("ds-cfg-db-directory").asString());
              String id = jeBackend.parseAttribute("ds-cfg-backend-id").asString();

              // Use canonical paths so that the progress message is more readable.
              File dbDirectory = new File(dbParent, id).getCanonicalFile();
              File dbDirectoryBackup = new File(dbParent, id + ".bak").getCanonicalFile();
              if (dbDirectory.exists() && !dbDirectoryBackup.exists())
              {
                LocalizableMessage msg = INFO_UPGRADE_TASK_RENAME_JE_DB_DIR.get(dbDirectory, dbDirectoryBackup);
                ProgressNotificationCallback pnc = new ProgressNotificationCallback(0, msg, 0);
                context.notifyProgress(pnc);
                boolean renameSucceeded = dbDirectory.renameTo(dbDirectoryBackup);
                context.notifyProgress(pnc.setProgress(renameSucceeded ? 100 : -1));
              }
            }
          }
        }
        catch (Exception e)
        {
          logger.error(LocalizableMessage.raw(e.getMessage()));
        }
      }

      @Override
      public void postUpgrade(UpgradeContext context) throws ClientException
      {
        postponePostUpgrade(context);
      }

      @Override
      public void postponePostUpgrade(UpgradeContext context) throws ClientException
      {
        if (reimportRequired)
        {
          context.notify(INFO_UPGRADE_TASK_RENAME_JE_DB_DIR_WARNING.get(), TextOutputCallback.WARNING);
        }
      }
    };
  }

  /** This inner classes causes JE to be lazily linked and prevents runtime errors if JE is not in the classpath. */
  static final class JEHelper {
    private static ClientException clientException(final File backendDirectory, final DatabaseException e) {
      logger.error(LocalizableMessage.raw(StaticUtils.stackTraceToString(e)));
      return new ClientException(ReturnCode.CONSTRAINT_VIOLATION,
                                 INFO_UPGRADE_TASK_MIGRATE_JE_ENV_UNREADABLE.get(backendDirectory), e);
    }

    static Set<String> listDatabases(final File backendDirectory) throws ClientException {
      try (Environment je = new Environment(backendDirectory, null)) {
        Set<String> databases = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        databases.addAll(je.getDatabaseNames());
        return databases;
      } catch (DatabaseException e) {
        throw clientException(backendDirectory, e);
      }
    }

    static void migrateDatabases(final File envDir, final Map<String, String> renamedDbs) throws ClientException {
      EnvironmentConfig config = new EnvironmentConfig().setTransactional(true);
      try (Environment je = new Environment(envDir, config)) {
        final Transaction txn = je.beginTransaction(null, new TransactionConfig());
        try {
          for (String dbName : je.getDatabaseNames()) {
            String newDbName = renamedDbs.get(dbName);
            if (newDbName != null) {
              // id2entry or compressed schema should be kept
              je.renameDatabase(txn, dbName, newDbName);
            } else {
              // This index will need rebuilding
              je.removeDatabase(txn, dbName);
            }
          }
          txn.commit();
        } finally {
          txn.abort();
        }
      } catch (DatabaseException e) {
        throw JEHelper.clientException(envDir, e);
      }
    }
  }

  private static void displayChangeCount(final String fileName,
      final int changeCount)
  {
    if (changeCount != 0)
    {
      logger.debug(INFO_UPGRADE_CHANGE_DONE_IN_SPECIFIC_FILE, fileName, changeCount);
    }
    else
    {
      logger.debug(INFO_UPGRADE_NO_CHANGE_DONE_IN_SPECIFIC_FILE, fileName);
    }
  }

  private static void displayTaskLogInformation(final String summary,
      final String filter, final String... ldif)
  {
    logger.debug(LocalizableMessage.raw(summary));
    if (filter != null)
    {
      logger.debug(LocalizableMessage.raw(filter));
    }
    if (ldif != null)
    {
      logger.debug(LocalizableMessage.raw(Arrays.toString(ldif)));
    }
  }

  private static void manageTaskException(final UpgradeContext context,
      final LocalizableMessage message, final ProgressNotificationCallback pnc)
      throws ClientException
  {
    countErrors++;
    context.notifyProgress(pnc.setProgress(-100));
    logger.error(message);
    if (!context.isIgnoreErrorsMode())
    {
      throw new ClientException(ReturnCode.ERROR_UNEXPECTED, message);
    }
  }

  private static UpgradeTask updateConfigEntry(final LocalizableMessage summary, final String filter,
      final ChangeOperationType changeOperationType, final String... ldif)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        performConfigFileUpdate(summary, filter, changeOperationType, context, ldif);
      }
    };
  }

  private static void performConfigFileUpdate(final LocalizableMessage summary, final String filter,
      final ChangeOperationType changeOperationType,
      final UpgradeContext context, final String... ldif)
      throws ClientException
  {
    displayTaskLogInformation(summary.toString(), filter, ldif);

    final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, summary, 20);
    context.notifyProgress(pnc);

    try
    {
      final File configFile =
          new File(configDirectory, Installation.CURRENT_CONFIG_FILE_NAME);

      final Filter filterVal = filter != null ? Filter.valueOf(filter) : null;
      final int changeCount = updateConfigFile(
          configFile.getPath(), filterVal, changeOperationType, ldif);

      displayChangeCount(configFile.getPath(), changeCount);

      context.notifyProgress(pnc.setProgress(100));
    }
    catch (final Exception e)
    {
      manageTaskException(context, LocalizableMessage.raw(e.getMessage()), pnc);
    }
  }

  static UpgradeTask clearReplicationDbDirectory()
  {
    return new AbstractUpgradeTask()
    {
      private File replicationDbDir;

      @Override
      public void prepare(final UpgradeContext context) throws ClientException
      {
        String replDbDir = readReplicationDbDirFromConfig();
        if (replDbDir != null
            && context.confirmYN(INFO_UPGRADE_TASK_MIGRATE_CHANGELOG_DESCRIPTION.get(), NO) == YES)
        {
          replicationDbDir = new File(getInstancePath(), replDbDir).getAbsoluteFile();
        }
        // if replDbDir == null, then this is not an RS, there is no changelog DB to clear
      }

      private String readReplicationDbDirFromConfig() throws ClientException
      {
        final SearchRequest sr = Requests.newSearchRequest(
            DN.valueOf("cn=replication server,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config"),
            SearchScope.BASE_OBJECT, Filter.alwaysTrue());
        try (final EntryReader entryReader = searchConfigFile(sr))
        {
          if (entryReader.hasNext())
          {
            final Entry replServerCfg = entryReader.readEntry();
            return replServerCfg.parseAttribute("ds-cfg-replication-db-directory").asString();
          }
          return null;
        }
        catch (IOException e)
        {
          LocalizableMessage msg = INFO_UPGRADE_TASK_MIGRATE_CONFIG_READ_FAIL.get();
          throw new ClientException(ReturnCode.APPLICATION_ERROR, msg, e);
        }
      }

      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        if (replicationDbDir == null)
        {
          // there is no changelog DB to clear
          return;
        }

        LocalizableMessage msg = INFO_UPGRADE_TASK_DELETE_CHANGELOG_SUMMARY.get(replicationDbDir);
        ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, msg, 0);
        context.notifyProgress(pnc);
        try
        {
          FileManager.deleteRecursively(replicationDbDir);
          context.notifyProgress(pnc.setProgress(100));
        }
        catch (ClientException e)
        {
          manageTaskException(context, e.getMessageObject(), pnc);
        }
        catch (Exception e)
        {
          manageTaskException(context, LocalizableMessage.raw(e.getLocalizedMessage()), pnc);
        }
      }
    };
  }

  /** Prevent instantiation. */
  private UpgradeTasks()
  {
    // Do nothing.
  }
}
