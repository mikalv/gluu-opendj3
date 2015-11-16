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
 *      Copyright 2015 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.BcryptPasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.BcryptPasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * A set of test cases for the Bcrypt password storage scheme.
 */
@SuppressWarnings("javadoc")
public class BcryptPasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /** Creates a new instance of this storage scheme test case.   */
  public BcryptPasswordStorageSchemeTestCase()
  {
    super("cn=Bcrypt,cn=Password Storage Schemes,cn=config");
  }

  /**
   * Retrieves an initialized instance of this password storage scheme.
   *
   * @return  An initialized instance of this password storage scheme.
   */
  @Override
  protected PasswordStorageScheme<?> getScheme() throws Exception
  {
    BcryptPasswordStorageScheme scheme =
         new BcryptPasswordStorageScheme();

    BcryptPasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
              BcryptPasswordStorageSchemeCfgDefn.getInstance(),
          configEntry.getEntry()
          );

    scheme.initializePasswordStorageScheme(configuration);
    return scheme;
  }

  /**
   * Retrieves a set of passwords (plain and bcrypt encrypted) that may
   * be used to test the compatibility of bcrypt passwords.
   * The encrypted versions have been provided by external tools or
   * users
   *
   * @return  A set of couple (cleartext, encrypted) passwords that
   *          may be used to test the bcrypt password storage scheme
   */

  @DataProvider(name = "testBcryptPasswords")
  public Object[][] getTestBcryptPasswords()
      throws Exception
  {
    return new Object[][]
        {
            new Object[] { "secret", "{BCRYPT}$2a$08$sxnezK9Dp9cQvU56LHRwIeI0RvfNn//fFzGnOgQ2l7TOZcZ1wbOVO" },
            new Object[] { "5[g&f:\"U;#99]!_T", "{BCRYPT}$2a$08$Ttmg4fCbAcq2636pT83d1eM8weXLHbn8OFyVRanP2Tjej5hiZBnyu" },
            new Object[] { "password", "{BCRYPT}$2a$05$bvIG6Nmid91Mu9RcmmWZfO5HJIMCT8riNW0hEp8f6/FuA2/mHZFpe"},
            new Object[] { "Secret12!", "{BCRYPT}$2a$10$UOYhwLcHwGYdwWCYq1Xd2.66aPGYq8Q7HDzm8jzTRkdJyAjt/gfhO" },
            new Object[] { "correctbatteryhorsestapler", "{BCRYPT}$2a$12$mACnM5lzNigHMaf7O1py1O3vlf6.BA8k8x3IoJ.Tq3IB/2e7g61Km"},
            new Object[] { "TestingWith12%", "{BCRYPT}$2a$12$2nTgfUEOupc7Eb5PyGCnIOzoDG/VMEhIOTKTjIjY3UPjtTI..NoLO" }
        };
  }

  @Test(dataProvider = "testBcryptPasswords")
  public void testAuthBcryptPasswords(
      String plaintextPassword,
      String encodedPassword) throws Exception
  {
    testAuthPasswords("TestBCrypt", plaintextPassword, encodedPassword);
  }

}