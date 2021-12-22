// Copyright 1999-2020 Alibaba Group Holding Ltd.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at the following link.
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package water.fvec;

import org.junit.Test;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class RyuDoubleTest {

    @Test
    public void testRandom() {
        Random random = new Random(42);
        for (int i = 0; i < 1000 * 1000 * 100; ++i) {
            double value = random.nextDouble();

            String str1 = Double.toString(value);
            String str2 = RyuDouble.doubleToString(value);

            if (! str1.equals(str2)) {
                assertEquals(Double.parseDouble(str1), Double.parseDouble(str2), 0);
            }
        }
    }

    @Test
    public void testGolden() {
        double[] values = new double[] {
                Double.NaN,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.MIN_VALUE,
                Double.MAX_VALUE,

                0,
                0.0d,
                -0.0d,
                Double.longBitsToDouble(0x8000000000000000L),
                Double.NaN,

                Long.MAX_VALUE,
                Long.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                Double.longBitsToDouble(0x0010000000000000L),

                9999999.999999998d,
                0.0009999999999999998d,
                1.0E7d,
                0.001d,
                Double.longBitsToDouble(0x7fefffffffffffffL),

                Double.longBitsToDouble(1),
                -2.109808898695963E16,
                4.940656E-318d,
                1.18575755E-316d,
                2.989102097996E-312d,
                9.0608011534336E15d,
                4.708356024711512E18,
                9.409340012568248E18,
                1.8531501765868567E21,
                -3.347727380279489E33,
                1.9430376160308388E16,
                -6.9741824662760956E19,
                4.3816050601147837E18,
        };

        for (double value : values) {
            String str1 = Double.toString(value);
            String str2 = RyuDouble.doubleToString(value);

            if (!str1.equals(str2)) {
                assertEquals(Double.parseDouble(str1), Double.parseDouble(str2), 0);
            }
        }
    }
}
