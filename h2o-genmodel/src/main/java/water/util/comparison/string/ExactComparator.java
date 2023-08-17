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

// Original code: https://github.com/larsga/Duke/blob/duke-1.2/src/main/java/no/priv/garshol/duke/comparators/ExactComparator.java
public class ExactComparator implements StringComparator {

    public boolean isTokenized() {
        return false;
    }

    public double compare(String v1, String v2) {
        return v1.equals(v2) ? 1.0 : 0.0;
    }

}
