package water.util.comparison.string;

import no.priv.garshol.duke.comparators.*;
import no.priv.garshol.duke.Comparator;

public class StringComparatorFactory {
    public static Comparator makeComparator(String measure) {
        switch (measure) {
            case "jaccard":
            case "JaccardIndex":
                return new JaccardIndexComparator();
            case "jw":
            case "JaroWinkler":
                // JaroWinkler Comparator contains bug which will be fixed in Duke 1.3 release.
                // Before that happens, we duplicate the JaroWinkler comparator class with the bug fixed
                return new H2OJaroWinklerComparator();
            case "lv":
            case "Levenshtein":
                return new LevenshteinDistanceComparator();
            case "lcs":
            case "LongestCommonSubstring":
                return new LongestCommonSubstring();
            case "qgram":
            case "QGram":
                return new QGramComparator();
            case "soundex":
            case "Soundex":
                return new SoundexComparator();
            default:
                throw new IllegalArgumentException("Unknown comparator: " + measure);
        }
    }
}
