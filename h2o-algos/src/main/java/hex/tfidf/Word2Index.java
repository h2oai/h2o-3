package hex.tfidf;

import water.MRTask;
import water.fvec.Chunk;
import water.parser.BufferedString;
import water.util.IcedHashMap;
import water.util.IcedLong;

/**
 * Map-Reduce task which generates word-index mapping for given corpus.
 */
public class Word2Index extends MRTask<Word2Index> {

    // OUT
    /**
     * Mapping assigning each word its index.
     */
    public IcedHashMap<BufferedString, IcedLong> _wordsIndices;

    // IN
    /**
     * Regex words delimiter in documents.
     */
    private final String _wordsDelimiter;

    /**
     * Current index to be used for next word indexing.
     */
    private long _currentIndex = 0;


    public Word2Index(String wordsDelimiterRegex) {
        _wordsDelimiter = wordsDelimiterRegex;
    }

    @Override
    public void map(Chunk c) {
        _wordsIndices = new IcedHashMap<>();

        for (int row = 0; row < c._len; row++) {
            if (c.isNA(row))
                continue; // Ignore NAs

            String[] words = c.atStr(new BufferedString(), row).toString().split(_wordsDelimiter);

            for (String word : words) {
                BufferedString buffWord = new BufferedString(word);
                _wordsIndices.computeIfAbsent(buffWord, k -> new IcedLong(_currentIndex++));
            }
        }
    }

    @Override
    public void reduce(Word2Index other) {
        other._wordsIndices.forEach((key, value) ->
                _wordsIndices.computeIfAbsent(key, k -> new IcedLong(_currentIndex++)));
    }
}
