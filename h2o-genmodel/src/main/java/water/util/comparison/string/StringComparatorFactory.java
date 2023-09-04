package water.util.comparison.string;

public class StringComparatorFactory {
    public static StringComparator makeComparator(String measure) {
        switch (measure) {
            case "jaccard":
            case "JaccardIndex":
                return new JaccardIndexComparator();
            case "jw":
            case "JaroWinkler":
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
