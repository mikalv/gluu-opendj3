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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */
package org.opends.server.api;
import org.forgerock.i18n.LocalizableMessage;



import java.util.List;

import org.opends.server.admin.std.server.PasswordStorageSchemeCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteSequence;


/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a password
 * storage scheme.  Each subclass may only implement a single password
 * storage scheme type.
 *
 * @param  <T>  The type of configuration handled by this
 *              password storage scheme
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class
       PasswordStorageScheme <T extends PasswordStorageSchemeCfg>
{
  /**
   * Initializes this password storage scheme handler based on the
   * information in the provided configuration entry.  It should also
   * register itself with the Directory Server for the particular
   * storage scheme that it will manage.
   *
   * @param  configuration  The configuration entry that contains the
   *                        information to use to initialize this
   *                        password storage scheme handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializePasswordStorageScheme(
         T configuration)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this password storage scheme.  It should be possible to call this
   * method on an uninitialized password storage scheme instance in
   * order to determine whether the password storage scheme would be
   * able to use the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The password storage scheme
   *                              configuration for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this password storage scheme, or {@code false} if
   *          not.
   */
  public boolean isConfigurationAcceptable(
                      PasswordStorageSchemeCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by password storage scheme
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any necessary finalization that might be required when
   * this password storage scheme is no longer needed (e.g., the
   * scheme is disabled or the server is shutting down).
   */
  public void finalizePasswordStorageScheme()
  {
    // No implementation required by default.
  }



  /**
   * Retrieves the name of the password storage scheme provided by
   * this handler.
   *
   * @return  The name of the password storage scheme provided by this
   *          handler.
   */
  public abstract String getStorageSchemeName();



  /**
   * Encodes the provided plaintext password for this storage scheme,
   * without the name of the associated scheme.  Note that the
   * provided plaintext password should not be altered in any way.
   *
   * @param  plaintext  The plaintext version of the password.
   *
   * @return  The password that has been encoded using this storage
   *          scheme.
   *
   * @throws  DirectoryException  If a problem occurs while
   *                              processing.
   */
  public abstract ByteString encodePassword(ByteSequence plaintext)
         throws DirectoryException;



  /**
   * Encodes the provided plaintext password for this storage scheme,
   * prepending the name of the scheme in curly braces.  Note that the
   * provided plaintext password should not be altered in any way.
   *
   * @param  plaintext  The plaintext version of the password.
   *
   * @return  The encoded password, including the name of the storage
   *          scheme.
   *
   * @throws  DirectoryException  If a problem occurs while
   *                              processing.
   */
  public abstract ByteString encodePasswordWithScheme(
                                  ByteSequence plaintext)
         throws DirectoryException;




  /**
   * Indicates whether the provided plaintext password included in a
   * bind request matches the given stored value.  The provided stored
   * value should not include the scheme name in curly braces.
   *
   * @param  plaintextPassword  The plaintext password provided by the
   *                            user as part of a simple bind attempt.
   * @param  storedPassword     The stored password to compare against
   *                            the provided plaintext password.
   *
   * @return  {@code true} if the provided plaintext password matches
   *          the provided stored password, or {@code false} if not.
   */
  public abstract boolean passwordMatches(
                               ByteSequence plaintextPassword,
                               ByteSequence storedPassword);



  /**
   * Indicates whether this password storage scheme supports the
   * ability to interact with values using the authentication password
   * syntax defined in RFC 3112.
   *
   * @return  {@code true} if this password storage scheme supports
   *          the ability to interact with values using the
   *          authentication password syntax, or {@code false} if it
   *          does not.
   */
  public abstract boolean supportsAuthPasswordSyntax();



  /**
   * Retrieves the scheme name that should be used with this password
   * storage scheme when it is used in the context of the
   * authentication password syntax.  This default implementation will
   * return the same value as the {@code getStorageSchemeName} method.
   *
   * @return  The scheme name that should be used with this password
   *          storage scheme when it is used in the context of the
   *          authentication password syntax.
   */
  public String getAuthPasswordSchemeName()
  {
    return getStorageSchemeName();
  }



  /**
   * Encodes the provided plaintext password for this storage scheme
   * using the authentication password syntax defined in RFC 3112.
   * Note that the provided plaintext password should not be altered
   * in any way.
   *
   * @param  plaintext  The plaintext version of the password.
   *
   * @return  The password that has been encoded in the authentication
   *          password syntax.
   *
   * @throws  DirectoryException  If a problem occurs while processing
   *                              of if this storage scheme does not
   *                              support the authentication password
   *                              syntax.
   */
  public abstract ByteString encodeAuthPassword(
      ByteSequence plaintext) throws DirectoryException;



  /**
   * Indicates whether the provided plaintext password matches the
   * encoded password using the authentication password syntax with
   * the given authInfo and authValue components.
   *
   * @param  plaintextPassword  The plaintext password provided by the
   *                            user.
   * @param  authInfo           The authInfo component of the password
   *                            encoded in the authentication password
   *                            syntax.
   * @param  authValue          The authValue component of the
   *                            password encoded in the authentication
   *                            password syntax.
   *
   * @return  {@code true} if the provided plaintext password matches
   *          the encoded password according to the authentication
   *          password info syntax, or {@code false} if it does not or
   *          this storage scheme does not support the authentication
   *          password syntax.
   */
  public abstract boolean authPasswordMatches(
                               ByteSequence plaintextPassword,
                               String authInfo, String authValue);



  /**
   * Indicates whether this storage scheme is reversible (i.e., it is
   * possible to obtain the original plaintext value from the stored
   * password).
   *
   * @return  {@code true} if this is a reversible password storage
   *          scheme, or {@code false} if it is not.
   */
  public abstract boolean isReversible();



  /**
   * Retrieves the original plaintext value for the provided stored
   * password.  Note that this should only be called if
   * {@code isReversible} returns {@code true}.
   *
   * @param  storedPassword  The password for which to obtain the
   *                         plaintext value.  It should not include
   *                         the scheme name in curly braces.
   *
   * @return  The plaintext value for the provided stored password.
   *
   * @throws  DirectoryException  If it is not possible to obtain the
   *                              plaintext value for the provided
   *                              stored password.
   */
  public abstract ByteString getPlaintextValue(
                                  ByteSequence storedPassword)
         throws DirectoryException;



  /**
   * Retrieves the original plaintext value for the provided password
   * stored in the authPassword syntax.  Note that this should only be
   * called if {@code isReversible} returns {@code true}.
   *
   * @param  authInfo   The authInfo component of the password encoded
   *                    in the authentication password syntax.
   * @param  authValue  The authValue component of the password
   *                    encoded in the authentication password syntax.
   *
   * @return  The plaintext value for the provided stored password.
   *
   * @throws  DirectoryException  If it is not possible to obtain the
   *                              plaintext value for the provided
   *                              stored password, or if this storage
   *                              scheme does not support the
   *                              authPassword syntax..
   */
  public abstract ByteString getAuthPasswordPlaintextValue(
                                  String authInfo, String authValue)
         throws DirectoryException;



  /**
   * Indicates whether this password storage scheme should be
   * considered "secure".  If the encoding used for this scheme does
   * not obscure the value at all, or if it uses a method that is
   * trivial to reverse (e.g., base64), then it should not be
   * considered secure.
   * <BR><BR>
   * This may be used to determine whether a password may be included
   * in a set of search results, including the possibility of
   * overriding access controls in the case that access controls would
   * allow the password to be returned but the password is considered
   * too insecure to reveal.
   *
   * @return  {@code false} if it may be trivial to discover the
   *          original plain-text password from the encoded form, or
   *          {@code true} if the scheme offers sufficient protection
   *          that revealing the encoded password will not easily
   *          reveal the corresponding plain-text value.
   */
  public abstract boolean isStorageSchemeSecure();
}

