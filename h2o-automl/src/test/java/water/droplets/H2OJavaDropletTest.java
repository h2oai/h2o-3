/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package water.droplets;

import org.junit.Before;
import org.junit.Test;
import water.DKV;
import water.H2O;
import water.Key;

import static org.junit.Assert.*;

/**
 * The test verify implementation of
 * {@link water.droplets.H2OJavaDroplet} class.
 */
public class H2OJavaDropletTest {

  @Before public void initCloud() {
    // Setup cloud name
    String[] args = new String[] { "-name", "h2o_test_cloud"};
    // Build a cloud of 1
    H2O.main(args);
    H2O.waitForCloudSize(1, 10*1000 /* ms */);
  }

  @Test public void testHello() {
    // Generate hello message and store it in K/V
    Key vkey = H2OJavaDroplet.hello();

    // Get POJO holding hello message
    H2OJavaDroplet.StringHolder helloHolder = DKV.get(vkey).get();

    // Verify the message
    assertEquals("Hello message does not match!", "Hello H2O!", helloHolder.hello("H2O"));
  }
}
