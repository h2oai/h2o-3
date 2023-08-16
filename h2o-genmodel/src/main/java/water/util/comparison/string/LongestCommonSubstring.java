package water.util.comparison.string;

/*
Copyright 2023 Lars Marius Garshol

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

// Original code: https://github.com/larsga/Duke/blob/duke-1.2/src/main/java/no/priv/garshol/duke/comparators/LongestCommonSubstring.java
public class LongestCommonSubstring implements StringComparator {
    private int minlen = 2;
    private Formula formula = Formula.OVERLAP;

    public double compare(String s1, String s2) {
        // a couple of quick cutoffs
        if (s1.equals(s2))
            return 1.0;
        if (Math.min(s1.length(), s2.length()) == 0)
            return 0.0;

        // the results of the algorithm depends on the order of the input
        // strings.  therefore need a sub-method for this computation
        return (compare_(s1, s2) + compare_(s2, s1)) / 2.0;
    }

    // FIXME: speed this up by using a one-dimensional array
    private double compare_(String s1, String s2) {
        // before we begin, note the length of the strings
        int shortlen = Math.min(s1.length(), s2.length());
        int longlen = Math.max(s1.length(), s2.length());

        int removed = 0; // total length of common substrings
        while (true) {
            // first, we identify the longest common substring
            int longest = 0;
            int longesti = 0;
            int longestj = 0;

            int[][] matrix = new int[s1.length()][s2.length()];
            for (int i = 0; i < s1.length(); i++) {
                for (int j = 0; j < s2.length(); j++) {
                    if (s1.charAt(i) == s2.charAt(j)) {
                        if (i == 0 || j == 0)
                            matrix[i][j] = 1;
                        else
                            matrix[i][j] = matrix[i - 1][j - 1] + 1;

                        if (matrix[i][j] > longest) {
                            longest = matrix[i][j];
                            longesti = i;
                            longestj = j;
                        }
                    } else
                        matrix[i][j] = 0;
                }
            }

            longesti++; // this solves an off-by-one problem
            longestj++; // this solves an off-by-one problem

            // at this point we know the length of the longest common
            // substring, and also its location, since it ends at indexes
            // longesti and longestj.

            if (longest < minlen)
                break; // all remaining common substrings are too short, so we stop

            // now we slice away the common substrings
            s1 = s1.substring(0, longesti - longest) + s1.substring(longesti);
            s2 = s2.substring(0, longestj - longest) + s2.substring(longestj);
            removed += longest;
        }

        return formula.compute(removed, shortlen, longlen);
    }

    public boolean isTokenized() {
        return true;
    }

    public void setMinimumLength(int minlen) {
        this.minlen = minlen;
    }

    public int getMinimumLength() {
        return this.minlen;
    }

    public void setFormula(Formula formula) {
        this.formula = formula;
    }

    public Formula getFormula() {
        return formula;
    }

    /**
     * Represents the different formulas we can use to compute similarity.
     */
    public enum Formula {
        OVERLAP {
            public double compute(int removed, int shortlen, int longlen) {
                return removed / (double) shortlen;
            }
        }, DICE {
            public double compute(int removed, int shortlen, int longlen) {
                return 2*removed / (double) (shortlen + longlen);
            }
        }, JACCARD {
            public double compute(int removed, int shortlen, int longlen) {
                return removed / (double) (shortlen + longlen - removed);
            }
        };

        public double compute(int removed, int shortlen, int longlen) {
            throw new IllegalStateException("Unknown formula: " + this);
        }
    }
}
