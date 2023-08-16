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

// Original code: https://github.com/larsga/Duke/blob/duke-1.2/src/main/java/no/priv/garshol/duke/utils/StringUtils.java
public class StringUtils {

    public static String[] split(String str) {
        String[] tokens = new String[(int) (str.length() / 2) + 1];
        int start = 0;
        int tcount = 0;
        boolean prevws = false;
        int ix;
        for (ix = 0; ix < str.length(); ix++) {
            if (str.charAt(ix) == ' ') {
                if (!prevws && ix > 0)
                    tokens[tcount++] = str.substring(start, ix);
                prevws = true;
                start = ix + 1;
            } else
                prevws = false;
        }

        if (!prevws && start != ix)
            tokens[tcount++] = str.substring(start);

        String[] tmp = new String[tcount];
        for (ix = 0; ix < tcount; ix++)
            tmp[ix] = tokens[ix];
        return tmp;
    }

    public static String join(String[] pieces) {
        StringBuilder tmp = new StringBuilder();
        for (int ix = 0; ix < pieces.length; ix++) {
            if (ix != 0)
                tmp.append(" ");
            tmp.append(pieces[ix]);
        }
        return tmp.toString();
    }
}
