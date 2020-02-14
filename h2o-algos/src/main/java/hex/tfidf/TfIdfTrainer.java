package hex.tfidf;

import water.MRTask;
import water.fvec.Chunk;
import water.parser.BufferedString;
import water.util.IcedHashMap;
import water.util.IcedLong;

public class TfIdfTrainer extends MRTask<TfIdfTrainer> {

    /**
     * Words delimiter in documents.
     */
    // TODO
    private static String WORDS_DELIMITER = " ";

    // OUT
    /**
     * Mapping assigning each word its number of occurrences in all documents.
     */
    public IcedHashMap<BufferedString, IcedLong[]> _wordsCounts;
    /**
     * Total words count for each document.
     */
    public IcedLong[] _totalWordsCounts;

    public TfIdfTrainer() {}

    @Override
    public void map(Chunk c) {
        _wordsCounts = new IcedHashMap<>();
        _totalWordsCounts = new IcedLong[c._len];
        for (int row = 0; row < c._len; row++) {
            if (c.isNA(row))
                continue; // Ignore NAs

            String[] words = c.atStr(new BufferedString(), row).toString().split(WORDS_DELIMITER);
            _totalWordsCounts[row] = new IcedLong(words.length);

            for (String word : words) {
                BufferedString buffWord = new BufferedString(word);
                IcedLong[] counts = _wordsCounts.get(buffWord);
                if (counts != null)
                    counts[row]._val++;
                else {
                    IcedLong[] wordCounts = new IcedLong[c._len];
                    for (int wordIdx = 0; wordIdx < c._len; wordIdx++)
                        wordCounts[wordIdx] = new IcedLong(0);

                    wordCounts[row]._val++;
                    _wordsCounts.put(buffWord, wordCounts);
                }
            }
        }
    }
}
