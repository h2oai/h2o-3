/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.lookup;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;

/**
 * Dummy replacement for Log4j's original JndiLookup class
 * It is intended to replace the class affected by CVE-2021-44228 and CVE-2021-45046
 */
@Plugin(name = "jndi", category = StrLookup.CATEGORY)
public class JndiLookup extends AbstractLookup {

    static {
        if (isSafe4j()) {
            System.out.println("This build is patched against vulnerabilities CVE-2021-44228 and CVE-2021-45046");
        }
    }

    @Override
    public String lookup(final LogEvent event, final String key) {
        return null;
    }

    /**
     * Marker method - this allows us to detect that JndiLookup class is H2O's placeholder
     * and not the actual implementation.
     *
     * We can use it to develop extension that verifies that H2O is not vulnerable.
     *
     * @return always true
     */
    public static boolean isSafe4j() {
        return true;
    }

}
