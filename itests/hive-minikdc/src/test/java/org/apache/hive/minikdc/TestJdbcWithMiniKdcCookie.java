/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hive.minikdc;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConf.ConfVars;
import org.apache.hive.jdbc.HiveConnection;
import org.apache.hive.jdbc.miniHS2.MiniHS2;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Testing JDBC with Mini KDC.
 */
@RunWith(Parameterized.class)
public class TestJdbcWithMiniKdcCookie {
  private static MiniHS2 miniHS2 = null;
  private MiniHiveKdc miniHiveKdc = null;
  private static Connection hs2Conn;
  File dataFile;
  protected static HiveConf hiveConf;
  private static String HIVE_NON_EXISTENT_USER = "hive_no_exist";

  @Parameterized.Parameter
  public String transportMode =  null;

  @Parameterized.Parameters(name = "{index}: tranportMode={0}")
  public static Collection<Object[]> transportModes() {
    return Arrays.asList(new Object[][]{{MiniHS2.HS2_ALL_MODE}, {MiniHS2.HS2_HTTP_MODE}});
  }

  @BeforeClass
  public static void beforeTest() throws Exception {
    Class.forName(MiniHS2.getJdbcDriverName());
  }

  @Before
  public void setUp() throws Exception {
    miniHiveKdc = new MiniHiveKdc();
    DriverManager.setLoginTimeout(0);
    hiveConf = new HiveConf();
    hiveConf.setVar(ConfVars.HIVE_SERVER2_TRANSPORT_MODE, transportMode);
    System.err.println("Testing using HS2 mode : "
        + hiveConf.getVar(ConfVars.HIVE_SERVER2_TRANSPORT_MODE));
    hiveConf.setBoolVar(ConfVars.HIVE_SERVER2_THRIFT_HTTP_COOKIE_AUTH_ENABLED,
        true);
    // set a small time unit as cookie max age so that the server sends a 401
    hiveConf.setTimeVar(ConfVars.HIVE_SERVER2_THRIFT_HTTP_COOKIE_MAX_AGE,
        1, TimeUnit.SECONDS);
    hiveConf.setBoolVar(ConfVars.HIVE_SUPPORT_CONCURRENCY, false);
    hiveConf.setBoolVar(ConfVars.HIVE_FETCH_TASK_CACHING, false);
    // query history adds no value to this test, it would just bring iceberg handler dependency, which isn't worth
    // this should be handled with HiveConfForTests when it's used here too
    hiveConf.setBoolVar(HiveConf.ConfVars.HIVE_QUERY_HISTORY_ENABLED, false);
    miniHS2 = MiniHiveKdc.getMiniHS2WithKerb(miniHiveKdc, hiveConf);
    miniHS2.start(new HashMap<String, String>());
  }

  @After
  public void tearDown() throws Exception {
    if (hs2Conn != null) {
      hs2Conn.close();
      hs2Conn = null;
    }
    if (miniHS2 != null && miniHS2.isStarted()) {
      miniHS2.stop();
      miniHS2.cleanup();
    }
  }

  @Test
  public void testCookie() throws Exception {
    String tableName = "test_cookie";
    dataFile = new File(hiveConf.get("test.data.files"), "kv1.txt");
    Connection hs2Conn = getConnection(MiniHiveKdc.HIVE_TEST_USER_1);

    Statement stmt = hs2Conn.createStatement();

    // create table
    stmt.execute("create table " + tableName + "(key int, value string) ");
    stmt.execute("load data local inpath '" + dataFile + "' into table " + tableName);

    // run a query in a loop so that we hit a 401 occasionally
    for (int i = 0; i < 10; i++) {
      stmt.execute("select * from " + tableName );
    }
    stmt.execute("drop table " + tableName);
    stmt.close();

    testCookieNegative();
  }

  public void testCookieNegative() throws Exception {
    try {
      // Trying to connect with a non-existent user should still fail with
      // login failure.
      getConnection(HIVE_NON_EXISTENT_USER);
    } catch (IOException e) {
      Assert.assertTrue(e.getMessage().contains("javax.security.auth.login.LoginException"));
    }
  }

  private Connection getConnection(String userName) throws Exception {
    miniHiveKdc.loginUser(userName);
    return new HiveConnection(miniHS2.getHttpJdbcURL(), new Properties());
  }
}
