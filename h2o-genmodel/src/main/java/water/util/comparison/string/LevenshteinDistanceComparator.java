package water.util.comparison.string;

import static org.apache.commons.lang.math.IEEE754rUtils.min;

/**
 * Non-weighted, case-sensitive Levenshtein distance implementation inspired by Simmetrics library.
 * This class is a modified version of Leventshein metric from Simmetrics library.
 * Double precision instead of 32-bit float is used. Dependence on Guava has been removed.
 *
 * Copyright H20.ai Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 **/
public class LevenshteinDistanceComparator implements StringComparator {

  @Override
  public boolean isTokenized() {
    return true;
  }

  @Override
  public double compare(String a, String b) {
    if (a.isEmpty() && b.isEmpty()) {
      return 1D;
    }

    return 1D - (distance(a, b) / Math.max(a.length(), b.length()));
  }


  /**
   * Computes a case-sensitive Levenshtein distance of two strings
   *
   * @param a First compared instance of {@link String}
   * @param b Second compared instance of {@link String}
   * @return Computed distance between two given strings
   */
  private double distance(final String a, final String b) {
    if (a.isEmpty())
      return b.length();
    if (b.isEmpty())
      return a.length();
    if (a.equals(b))
      return 0;

    final int aLength = b.length();
    final int bLength = a.length();

    double[] v0 = new double[aLength + 1];
    double[] v1 = new double[aLength + 1];

    // initialize v0 (the previous row of distances)
    // this row is A[0][i]: edit distance for an empty a
    // the distance is just the number of characters to delete from b
    for (int i = 0; i < v0.length; i++) {
      v0[i] = i * 1D;
    }

    for (int i = 0; i < bLength; i++) {

      // first element of v1 is A[i+1][0]
      // edit distance is delete (i+1) chars from s to match empty b
      v1[0] = (i + 1) * 1D;

      for (int j = 0; j < aLength; j++) {
        v1[j + 1] = min(v1[j] + 1D,
            v0[j + 1] + 1D,
            v0[j]
                + (a.charAt(i) == b.charAt(j) ? 0D
                : 1D));
      }

      final double[] swap = v0;
      v0 = v1;
      v1 = swap;
    }

    // latest results was in v1 which was swapped with v0
    return v0[aLength];
  }


}
