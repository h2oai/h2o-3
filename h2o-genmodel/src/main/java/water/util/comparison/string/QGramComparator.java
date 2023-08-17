package water.util.comparison.string;

import java.util.Set;
import java.util.HashSet;

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

// Original code: https://github.com/larsga/Duke/blob/duke-1.2/src/main/java/no/priv/garshol/duke/comparators/QGramComparator.java
public class QGramComparator implements StringComparator {
    private Formula formula;
    private Tokenizer tokenizer;
    private int q;

    public QGramComparator() {
        this.formula = Formula.OVERLAP;
        this.tokenizer = Tokenizer.BASIC;
        this.q = 2;
    }

    public boolean isTokenized() {
        return true;
    }

    public double compare(String s1, String s2) {
        if (s1.equals(s2))
            return 1.0;

        Set<String> q1 = tokenizer.qgrams(s1, q);
        Set<String> q2 = tokenizer.qgrams(s2, q);

        if (q1.isEmpty() || q2.isEmpty())
            return 0.0; // division will fail

        int common = 0;
        for (String gram : q1)
            if (q2.contains(gram))
                common++;

        return formula.compute(common, q1, q2);
    }

    /**
     * Sets the value of q, that is, the size of the q-grams.
     */
    public void setQ(int q) {
        this.q = q;
    }

    /**
     * Tells the comparator what formula to use to compute the actual
     * similarity.
     */
    public void setFormula(Formula formula) {
        this.formula = formula;
    }

    /**
     * Tells the comparator what tokenizer to use to produce q-grams.
     */
    public void setTokenizer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    private static String pad(String s, int q, boolean front) {
        StringBuffer buf = new StringBuffer(q);
        if (!front)
            buf.append(s);
        for (int ix = 0; ix < q - s.length(); ix++)
            buf.append('.');
        if (front)
            buf.append(s);
        return buf.toString();
    }

    /**
     * Represents the different formulas we can use to compute similarity.
     */
    public enum Formula {
        OVERLAP {
            public double compute(int common, Set<String> q1, Set<String> q2) {
                return (double) common / Math.min((double) q1.size(), (double) q2.size());
            }
        }, JACCARD {
            public double compute(int common, Set<String> q1, Set<String> q2) {
                return (double) common / (double) (q1.size() + q2.size() - common);
            }
        }, DICE {
            public double compute(int common, Set<String> q1, Set<String> q2) {
                return (double) (2.0 * common) / (double) (q1.size() + q2.size());
            }
        };

        public double compute(int common, Set<String> q1, Set<String> q2) {
            throw new IllegalStateException("Unknown formula: " + this);
        }
    }

    /**
     * Represents the different ways we can tokenize a string into a set
     * of q-grams for a given q.
     */
    public enum Tokenizer {
        /**
         * Produces basic q-grams, so that 'gail' -> 'ga', 'ai', 'il'.
         */
        BASIC {
            public Set<String> qgrams(String s, int q) {
                Set<String> grams = new HashSet();
                for (int ix = 0; ix < s.length() - q + 1; ix++)
                    grams.add(s.substring(ix, ix + q));

                return grams;
            }
        },

        /**
         * Produces positional q-grams, so that 'gail' -> 'ga1', 'ai2', 'il3'.
         */
        POSITIONAL {
            public Set<String> qgrams(String s, int q) {
                Set<String> grams = new HashSet();
                for (int ix = 0; ix < s.length() - q + 1; ix++)
                    grams.add(s.substring(ix, ix + q) + ix);

                return grams;
            }
        },

        /**
         * Produces q-grams with padding, so that 'gail' -> '.g', 'ga', 'ai',
         * 'il', 'l.'.
         */
        ENDS {
            public Set<String> qgrams(String s, int q) {
                Set<String> grams = new HashSet();
                for (int ix = 1; ix < q; ix++)
                    grams.add(pad(s.substring(0, ix), q, true));
                for (int ix = 0; ix < s.length() - q + 1; ix++)
                    grams.add(s.substring(ix, ix + q));
                for (int ix = 1; ix < q; ix++)
                    grams.add(pad(s.substring(s.length() - ix), q, false));

                return grams;
            }
        };

        public Set<String> qgrams(String s, int q) {
            throw new IllegalStateException("Uknown tokenizer: " + this);
        }
    }
}
