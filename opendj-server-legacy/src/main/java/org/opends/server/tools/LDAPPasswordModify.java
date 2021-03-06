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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.controls.PasswordPolicyWarningType;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.EmbeddedUtils;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This program provides a utility that uses the LDAP password modify extended
 * operation to change the password for a user.  It exposes the three primary
 * options available for this operation, which are:
 *
 * <UL>
 *   <LI>The user identity whose password should be changed.</LI>
 *   <LI>The current password for the user.</LI>
 *   <LI>The new password for the user.</LI>
 * </UL>
 *
 * All of these are optional components that may be included or omitted from the
 * request.
 */
public class LDAPPasswordModify
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tools.LDAPPasswordModify";




  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int returnCode = mainPasswordModify(args, true, System.out, System.err);
    if (returnCode != 0)
    {
      System.exit(filterExitCode(returnCode));
    }
  }



  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return  An integer value of zero if everything completed successfully, or
   *          a nonzero value if an error occurred.
   */
  public static int mainPasswordModify(String[] args)
  {
    return mainPasswordModify(args, true, System.out, System.err);
  }



  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output.
   * @param  errStream         The output stream to use for standard error.
   *
   * @return  An integer value of zero if everything completed successfully, or
   *          a nonzero value if an error occurred.
   */
  public static int mainPasswordModify(String[] args, boolean initializeServer,
                                       OutputStream outStream,
                                       OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);


    // Create the arguments that will be used by this program.
    BooleanArgument   provideDNForAuthzID;
    BooleanArgument   showUsage;
    BooleanArgument   trustAll;
    BooleanArgument   useSSL;
    BooleanArgument   useStartTLS;
    FileBasedArgument bindPWFile;
    StringArgument    certNickname;
    FileBasedArgument currentPWFile;
    FileBasedArgument newPWFile;
    FileBasedArgument sslKeyStorePINFile;
    FileBasedArgument sslTrustStorePINFile;
    IntegerArgument   ldapPort;
    StringArgument    authzID;
    StringArgument    bindDN;
    StringArgument    bindPW;
    StringArgument    controlStr;
    StringArgument    currentPW;
    StringArgument    ldapHost;
    StringArgument    newPW;
    StringArgument    sslKeyStore;
    StringArgument    sslKeyStorePIN;
    StringArgument    sslTrustStore;
    StringArgument    sslTrustStorePIN;
    IntegerArgument   connectTimeout;
    StringArgument    propertiesFileArgument;
    BooleanArgument   noPropertiesFileArgument;


    // Initialize the argument parser.
    LocalizableMessage toolDescription = INFO_LDAPPWMOD_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);
    argParser.setShortToolDescription(REF_SHORT_DESC_LDAPPASSWORDMODIFY.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());

    try
    {
      propertiesFileArgument =
              StringArgument.builder(OPTION_LONG_PROP_FILE_PATH)
                      .description(INFO_DESCRIPTION_PROP_FILE_PATH.get())
                      .valuePlaceholder(INFO_PROP_FILE_PATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      noPropertiesFileArgument =
              BooleanArgument.builder(OPTION_LONG_NO_PROP_FILE)
                      .description(INFO_DESCRIPTION_NO_PROP_FILE.get())
                      .buildAndAddToParser(argParser);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      ldapHost =
              StringArgument.builder(OPTION_LONG_HOST)
                      .shortIdentifier(OPTION_SHORT_HOST)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_HOST.get())
                      .defaultValue("127.0.0.1")
                      .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      ldapPort =
              IntegerArgument.builder(OPTION_LONG_PORT)
                      .shortIdentifier(OPTION_SHORT_PORT)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_PORT.get())
                      .range(1, 65535)
                      .defaultValue(389)
                      .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      useSSL =
              BooleanArgument.builder(OPTION_LONG_USE_SSL)
                      .shortIdentifier(OPTION_SHORT_USE_SSL)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_USE_SSL.get())
                      .buildAndAddToParser(argParser);
      useStartTLS =
              BooleanArgument.builder(OPTION_LONG_START_TLS)
                      .shortIdentifier(OPTION_SHORT_START_TLS)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_USE_STARTTLS.get())
                      .buildAndAddToParser(argParser);
      bindDN =
              StringArgument.builder(OPTION_LONG_BINDDN)
                      .shortIdentifier(OPTION_SHORT_BINDDN)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_BIND_DN.get())
                      .valuePlaceholder(INFO_BINDDN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      bindPW =
              StringArgument.builder(OPTION_LONG_BINDPWD)
                      .shortIdentifier(OPTION_SHORT_BINDPWD)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_BIND_PW.get())
                      .valuePlaceholder(INFO_BINDPWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      bindPWFile =
              FileBasedArgument.builder(OPTION_LONG_BINDPWD_FILE)
                      .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_BIND_PW_FILE.get())
                      .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      authzID =
              StringArgument.builder("authzID")
                      .shortIdentifier('a')
                      .description(INFO_LDAPPWMOD_DESCRIPTION_AUTHZID.get())
                      .valuePlaceholder(INFO_PROXYAUTHID_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      provideDNForAuthzID =
              BooleanArgument.builder("provideDNForAuthzID")
                      .shortIdentifier('A')
                      .description(INFO_LDAPPWMOD_DESCRIPTION_PROVIDE_DN_FOR_AUTHZID.get())
                      .buildAndAddToParser(argParser);
      newPW =
              StringArgument.builder("newPassword")
                      .shortIdentifier('n')
                      .description(INFO_LDAPPWMOD_DESCRIPTION_NEWPW.get())
                      .valuePlaceholder(INFO_NEW_PASSWORD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      newPWFile =
              FileBasedArgument.builder("newPasswordFile")
                      .shortIdentifier('N')
                      .description(INFO_LDAPPWMOD_DESCRIPTION_NEWPWFILE.get())
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      currentPW =
              StringArgument.builder("currentPassword")
                      .shortIdentifier('c')
                      .description(INFO_LDAPPWMOD_DESCRIPTION_CURRENTPW.get())
                      .valuePlaceholder(INFO_CURRENT_PASSWORD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      currentPWFile =
              FileBasedArgument.builder("currentPasswordFile")
                      .shortIdentifier('C')
                      .description(INFO_LDAPPWMOD_DESCRIPTION_CURRENTPWFILE.get())
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      trustAll = trustAllArgument();
      argParser.addArgument(trustAll);

      sslKeyStore =
              StringArgument.builder(OPTION_LONG_KEYSTOREPATH)
                      .shortIdentifier(OPTION_SHORT_KEYSTOREPATH)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_KEYSTORE.get())
                      .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      sslKeyStorePIN =
              StringArgument.builder(OPTION_LONG_KEYSTORE_PWD)
                      .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_KEYSTORE_PIN.get())
                      .valuePlaceholder(INFO_KEYSTORE_PWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      sslKeyStorePINFile =
              FileBasedArgument.builder(OPTION_LONG_KEYSTORE_PWD_FILE)
                      .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD_FILE)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_KEYSTORE_PINFILE.get())
                      .valuePlaceholder(INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      certNickname =
              StringArgument.builder("certNickname")
                      .description(INFO_DESCRIPTION_CERT_NICKNAME.get())
                      .valuePlaceholder(INFO_NICKNAME_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      sslTrustStore =
              StringArgument.builder(OPTION_LONG_TRUSTSTOREPATH)
                      .shortIdentifier(OPTION_SHORT_TRUSTSTOREPATH)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_TRUSTSTORE.get())
                      .valuePlaceholder(INFO_TRUSTSTOREPATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      sslTrustStorePIN =
              StringArgument.builder(OPTION_LONG_TRUSTSTORE_PWD)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_TRUSTSTORE_PIN.get())
                      .valuePlaceholder(INFO_TRUSTSTORE_PWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      sslTrustStorePINFile =
              FileBasedArgument.builder(OPTION_LONG_TRUSTSTORE_PWD_FILE)
                      .shortIdentifier(OPTION_SHORT_TRUSTSTORE_PWD_FILE)
                      .description(INFO_LDAPPWMOD_DESCRIPTION_TRUSTSTORE_PINFILE.get())
                      .valuePlaceholder(INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      controlStr =
              StringArgument.builder("control")
                      .shortIdentifier('J')
                      .description(INFO_DESCRIPTION_CONTROLS.get())
                      .multiValued()
                      .valuePlaceholder(INFO_LDAP_CONTROL_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      connectTimeout =
              IntegerArgument.builder(OPTION_LONG_CONNECT_TIMEOUT)
                      .description(INFO_DESCRIPTION_CONNECTION_TIMEOUT.get())
                      .lowerBound(0)
                      .defaultValue(CliConstants.DEFAULT_LDAP_CONNECT_TIMEOUT)
                      .valuePlaceholder(INFO_TIMEOUT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      showUsage = showUsageArgument();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // If the usage or version argument was provided,
    // then we don't need to do anything else.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }


    // Make sure that the user didn't specify any conflicting arguments.
    try
    {
      throwIfArgumentsConflict(bindPW, bindPWFile);
      throwIfArgumentsConflict(newPW, newPWFile);
      throwIfArgumentsConflict(currentPW, currentPWFile);
      throwIfArgumentsConflict(useSSL, useStartTLS);
      throwIfArgumentsConflict(sslKeyStorePIN, sslKeyStorePINFile);
      throwIfArgumentsConflict(sslTrustStorePIN, sslTrustStorePINFile);
    }
    catch(final ArgumentException conflict)
    {
      printWrappedText(err, conflict.getMessageObject());
      return CLIENT_SIDE_PARAM_ERROR;
    }

    // If a bind DN was provided, make sure that a password was given.  If a
    // password was given, make sure a bind DN was provided.  If neither were
    // given, then make sure that an authorization ID and the current password
    // were provided.
    if (bindDN.isPresent())
    {
      if (!bindPW.isPresent() && !bindPWFile.isPresent())
      {
        argParser.displayMessageAndUsageReference(err, ERR_LDAPPWMOD_BIND_DN_AND_PW_MUST_BE_TOGETHER.get());
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }
    else if (bindPW.isPresent() || bindPWFile.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_LDAPPWMOD_BIND_DN_AND_PW_MUST_BE_TOGETHER.get());
      return CLIENT_SIDE_PARAM_ERROR;
    }
    else
    {
      if (provideDNForAuthzID.isPresent())
      {
        argParser.displayMessageAndUsageReference(err,
            ERR_LDAPPWMOD_DEPENDENT_ARGS.get(provideDNForAuthzID.getLongIdentifier(), bindDN.getLongIdentifier()));
        return CLIENT_SIDE_PARAM_ERROR;
      }

      if (!authzID.isPresent() || (!currentPW.isPresent() && !currentPWFile.isPresent()))
      {
        argParser.displayMessageAndUsageReference(err, ERR_LDAPPWMOD_ANON_REQUIRES_AUTHZID_AND_CURRENTPW.get());
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }


    // Get the host and port.
    String host = ldapHost.getValue();
    int    port;
    try
    {
      port = ldapPort.getIntValue();
    }
    catch (Exception e)
    {
      // This should never happen.
      printWrappedText(err, e.toString());
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // If a control string was provided, then decode the requested controls.
    ArrayList<Control> controls = new ArrayList<>();
    if(controlStr.isPresent())
    {
      for (String ctrlString : controlStr.getValues())
      {
        LDAPControl ctrl = LDAPToolUtils.getControl(ctrlString, err);
        if(ctrl == null)
        {
          printWrappedText(err, ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString));
          return CLIENT_SIDE_PARAM_ERROR;
        }
        controls.add(ctrl);
      }
    }


    // Perform a basic Directory Server bootstrap if appropriate.
    if (initializeServer)
    {
      EmbeddedUtils.initializeForClientUse();
    }


    // Establish a connection to the Directory Server.
    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    connectionOptions.setUseSSL(useSSL.isPresent());
    connectionOptions.setStartTLS(useStartTLS.isPresent());
    connectionOptions.setVersionNumber(3);
    if(connectionOptions.useSSL() || connectionOptions.useStartTLS())
    {
      String keyPIN = null;
      if (sslKeyStorePIN.isPresent())
      {
        keyPIN = sslKeyStorePIN.getValue();
      }
      else if (sslKeyStorePINFile.isPresent())
      {
        keyPIN = sslKeyStorePINFile.getValue();
      }

      String trustPIN = null;
      if (sslTrustStorePIN.isPresent())
      {
        trustPIN = sslTrustStorePIN.getValue();
      }
      else if (sslTrustStorePINFile.isPresent())
      {
        trustPIN = sslTrustStorePINFile.getValue();
      }

      try
      {
        String clientAlias;
        if (certNickname.isPresent())
        {
          clientAlias = certNickname.getValue();
        }
        else
        {
          clientAlias = null;
        }
        SSLConnectionFactory sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(trustAll.isPresent(),
                                  sslKeyStore.getValue(), keyPIN, clientAlias,
                                  sslTrustStore.getValue(), trustPIN);
        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_LDAPPWMOD_ERROR_INITIALIZING_SSL.get(e));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    LDAPConnection connection = new LDAPConnection(host, port,
                                                   connectionOptions, out, err);
    String dn;
    String pw;
    if (bindPW.isPresent())
    {
      dn = bindDN.getValue();
      pw = bindPW.getValue();
      if(pw != null && pw.equals("-"))
      {
        // read the password from the stdin.
        try
        {
          out.print(INFO_LDAPAUTH_PASSWORD_PROMPT.get(dn));
          char[] pwChars = ConsoleApplication.readPassword();
          //As per rfc 4513(section-5.1.2) a client should avoid sending
          //an empty password to the server.
          while(pwChars.length==0)
          {
            printWrappedText(err, INFO_LDAPAUTH_NON_EMPTY_PASSWORD.get());
            out.print(INFO_LDAPAUTH_PASSWORD_PROMPT.get(dn));
            pwChars = ConsoleApplication.readPassword();
          }
          pw = new String(pwChars);
        } catch(Exception ex)
        {
          printWrappedText(err, ex.getMessage());
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }
    }
    else if (bindPWFile.isPresent())
    {
      dn = bindDN.getValue();
      pw = bindPWFile.getValue();
    }
    else
    {
      dn = null;
      pw = null;
    }

    try
    {
      int timeout = connectTimeout.getIntValue();
      connection.connectToHost(dn, pw, nextMessageID, timeout);
    }
    catch (LDAPConnectionException lce)
    {
      printWrappedText(err, ERR_LDAPPWMOD_CANNOT_CONNECT.get(lce.getMessage()));
      return lce.getResultCode();
    }
    catch (ArgumentException e)
    {
      // This should not occur because the arguments are already parsed.
      // It is a bug
      e.printStackTrace();
      throw new IllegalStateException("Unexpected error: "+e, e);
    }

    LDAPReader reader = connection.getLDAPReader();
    LDAPWriter writer = connection.getLDAPWriter();


    // Construct the password modify request.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer asn1Writer = ASN1.getWriter(builder);

    try
    {
    asn1Writer.writeStartSequence();
    if (authzID.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_USER_ID,
          authzID.getValue());
    }
    else if (provideDNForAuthzID.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_USER_ID, "dn:" + dn);
    }

    if (currentPW.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD,
                                              currentPW.getValue());
    }
    else if (currentPWFile.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD,
                                              currentPWFile.getValue());
    }
    else if (provideDNForAuthzID.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD,
                                              pw);
    }

    if (newPW.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_NEW_PASSWORD,
                                              newPW.getValue());
    }
    else if (newPWFile.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_NEW_PASSWORD,
                                              newPWFile.getValue());
    }
    asn1Writer.writeEndSequence();
    }
    catch(Exception e)
    {
      err.println(e);
    }

    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_PASSWORD_MODIFY_REQUEST,
                                       builder.toByteString());
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), extendedRequest,
                         controls);


    // Send the request to the server and read the response.
    try
    {
      writer.writeMessage(requestMessage);
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_LDAPPWMOD_CANNOT_SEND_PWMOD_REQUEST.get(e));
      unbind(nextMessageID, writer);
      close(reader, writer);
      return 1;
    }


    // Read the response from the server.
    LDAPMessage responseMessage = null;
    try
    {
      responseMessage = reader.readMessage();
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_LDAPPWMOD_CANNOT_READ_PWMOD_RESPONSE.get(e));
      unbind(nextMessageID, writer);
      close(reader, writer);
      return 1;
    }


    // Make sure that the response was acceptable.
    ExtendedResponseProtocolOp extendedResponse =
         responseMessage.getExtendedResponseProtocolOp();
    int resultCode = extendedResponse.getResultCode();
    if (resultCode != LDAPResultCode.SUCCESS)
    {
      printWrappedText(err, ERR_LDAPPWMOD_FAILED.get(resultCode));

      LocalizableMessage errorMessage = extendedResponse.getErrorMessage();
      if (errorMessage != null && errorMessage.length() > 0)
      {
        printWrappedText(err, ERR_LDAPPWMOD_FAILURE_ERROR_MESSAGE.get(errorMessage));
      }

      DN matchedDN = extendedResponse.getMatchedDN();
      if (matchedDN != null)
      {
        printWrappedText(err, ERR_LDAPPWMOD_FAILURE_MATCHED_DN.get(matchedDN));
      }

      unbind(nextMessageID, writer);
      close(reader, writer);
      return resultCode;
    }
    else
    {
      printWrappedText(out, INFO_LDAPPWMOD_SUCCESSFUL.get());
      LocalizableMessage additionalInfo = extendedResponse.getErrorMessage();
      if (additionalInfo != null && additionalInfo.length() > 0)
      {
        printWrappedText(out, INFO_LDAPPWMOD_ADDITIONAL_INFO.get(additionalInfo));
      }
    }


    // See if the response included any controls that we recognize, and if so
    // then handle them.
    List<Control> responseControls = responseMessage.getControls();
    if (responseControls != null)
    {
      for (Control c : responseControls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          try
          {
            PasswordPolicyResponseControl pwPolicyControl =
              PasswordPolicyResponseControl.DECODER
                .decode(c.isCritical(), ((LDAPControl) c).getValue());

            PasswordPolicyWarningType pwPolicyWarningType =
                 pwPolicyControl.getWarningType();
            if (pwPolicyWarningType != null)
            {
              printWrappedText(
                      out, INFO_LDAPPWMOD_PWPOLICY_WARNING.get(pwPolicyWarningType, pwPolicyControl.getWarningValue()));
            }

            PasswordPolicyErrorType pwPolicyErrorType =
                 pwPolicyControl.getErrorType();
            if (pwPolicyErrorType != null)
            {
              printWrappedText(out, INFO_LDAPPWMOD_PWPOLICY_ERROR.get(pwPolicyErrorType));
            }
          }
          catch (Exception e)
          {
            printWrappedText(err, ERR_LDAPPWMOD_CANNOT_DECODE_PWPOLICY_CONTROL.get(e));
          }
        }
      }
    }


    // See if the response included a generated password.
    ByteString responseValue = extendedResponse.getValue();
    if (responseValue != null)
    {
      try
      {
        ASN1Reader asn1Reader = ASN1.getReader(responseValue);
        asn1Reader.readStartSequence();
        while(asn1Reader.hasNextElement())
        {
          if (asn1Reader.peekType() == TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD)
          {
            printWrappedText(out, INFO_LDAPPWMOD_GENERATED_PASSWORD.get(asn1Reader.readOctetStringAsString()));
          }
          else
          {
            printWrappedText(err, ERR_LDAPPWMOD_UNRECOGNIZED_VALUE_TYPE.get(asn1Reader.readOctetStringAsString()));
          }
        }
        asn1Reader.readEndSequence();
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_LDAPPWMOD_COULD_NOT_DECODE_RESPONSE_VALUE.get(e));
        unbind(nextMessageID, writer);
        close(reader, writer);
        return 1;
      }
    }


    // Unbind from the server and close the connection.
    unbind(nextMessageID, writer);
    close(reader, writer);
    return 0;
  }

  private static void unbind(AtomicInteger nextMessageID, LDAPWriter writer)
  {
    try
    {
      LDAPMessage requestMessage = new LDAPMessage(
          nextMessageID.getAndIncrement(), new UnbindRequestProtocolOp());
      writer.writeMessage(requestMessage);
    }
    catch (Exception e) {}
  }
}

