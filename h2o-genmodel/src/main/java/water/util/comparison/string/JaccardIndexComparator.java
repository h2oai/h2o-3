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

// Original code: https://github.com/larsga/Duke/blob/duke-1.2/src/main/java/no/priv/garshol/duke/comparators/JaccardIndexComparator.java
public class JaccardIndexComparator implements StringComparator {
    private StringComparator subcomp;

    public JaccardIndexComparator() {
        this.subcomp = new ExactComparator();
    }

    public void setComparator(StringComparator comp) {
        this.subcomp = comp;
    }

    public boolean isTokenized() {
        return true;
    }

    public double compare(String s1, String s2) {
        if (s1.equals(s2))
            return 1.0;

        // tokenize
        String[] t1 = StringUtils.split(s1);
        String[] t2 = StringUtils.split(s2);

        // FIXME: we assume t1 and t2 do not have internal duplicates

        // ensure that t1 is shorter than or same length as t2
        if (t1.length > t2.length) {
            String[] tmp = t2;
            t2 = t1;
            t1 = tmp;
        }

        // find best matches for each token in t1
        double intersection = 0;
        double union = t1.length + t2.length;
        for (int ix1 = 0; ix1 < t1.length; ix1++) {
            double highest = 0;
            for (int ix2 = 0; ix2 < t2.length; ix2++)
                highest = Math.max(highest, subcomp.compare(t1[ix1], t2[ix2]));

            // INV: the best match for t1[ix1] in t2 is has similarity highest
            intersection += highest;
            union -= highest; // we reduce the union by this similarity
        }

        return intersection / union;
    }
}

