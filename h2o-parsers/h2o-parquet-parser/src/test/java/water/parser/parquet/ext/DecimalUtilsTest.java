/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package water.parser.parquet.ext;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.math.BigDecimal;

import org.apache.parquet.io.api.Binary;

public class DecimalUtilsTest {

  private void testDecimalConversion(double value, int precision, int scale, String stringValue) {
    String originalString = Double.toString(value);
    BigDecimal originalValue = new BigDecimal(originalString);
    BigDecimal convertedValue = DecimalUtils.binaryToDecimal(Binary.fromByteArray(originalValue.unscaledValue().toByteArray()),
            precision,scale);
    assertEquals(stringValue, convertedValue.toString());
  }

  private void testDecimalConversion(int value, int precision, int scale, String stringValue) {
    String originalString = Integer.toString(value);
    BigDecimal originalValue = new BigDecimal(originalString);
    BigDecimal convertedValue = DecimalUtils.binaryToDecimal(Binary.fromByteArray(originalValue.unscaledValue().toByteArray()),
            precision,scale);
    assertEquals(stringValue, convertedValue.toString());
  }

  private void testDecimalConversion(long value, int precision, int scale, String stringValue) {
    String originalString = Long.toString(value);
    BigDecimal originalValue = new BigDecimal(originalString);
    BigDecimal convertedValue = DecimalUtils.binaryToDecimal(Binary.fromByteArray(originalValue.unscaledValue().toByteArray()),
            precision, scale);
    assertEquals(stringValue, convertedValue.toString());
  }

  @Test
  public void testBinaryToDecimal() throws Exception {
    // Known issue: testing Nx10^M doubles from BigDecimal.unscaledValue() always converts to Nx10 regardless of M
    // Known issue: any double with precision > 17 breaks in test but not in functional testing

    // Test LONG
    testDecimalConversion(Long.MAX_VALUE,19,0,"9223372036854775807");
    testDecimalConversion(Long.MIN_VALUE,19,0,"-9223372036854775808");
    testDecimalConversion(0L,0,0,"0.0");

    // Test INTEGER
    testDecimalConversion(Integer.MAX_VALUE,10,0,"2147483647");
    testDecimalConversion(Integer.MIN_VALUE,10,0,"-2147483648");
    testDecimalConversion(0,0,0,"0.0");

    // Test DOUBLE
    testDecimalConversion(12345678912345678d,17,0,"12345678912345678");
    testDecimalConversion(123456789123456.78,17,2,"123456789123456.78");
    testDecimalConversion(0.12345678912345678,17,17,"0.12345678912345678");
    testDecimalConversion(-0.000102,6,6,"-0.000102");
  }
}
