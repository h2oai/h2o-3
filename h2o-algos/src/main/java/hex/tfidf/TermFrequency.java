package hex.tfidf;

import water.MRTask;
import water.fvec.Chunk;
import water.parser.BufferedString;
import water.util.IcedHashMap;
import water.util.IcedLong;

/**
 * Map-Reduce task for computing Term Frequency values for words in given documents.
 */
public class TermFrequency extends MRTask<TermFrequency> {

    // OUT
    /**
     * Mappings for each document assigning each word its Term Frequency values.
     */
    public IcedHashMap<BufferedString, IcedLong>[] _termFrequencies;

    public TermFrequency(int documentsCnt) {
        _termFrequencies = new IcedHashMap[documentsCnt];
        for (int i = 0; i < documentsCnt; i++) {
            _termFrequencies[i] = new IcedHashMap<>();
        }
    }

    @Override
    public void map(Chunk[] cs) {
        Chunk docIds = cs[1];
        Chunk tokens = cs[2];

        for (int row = 0; row < tokens._len; row++) {
            if (tokens.isNA(row))
                continue; // Ignore NAs

            int docId = (int) docIds.at8(row);
            BufferedString token = tokens.atStr(new BufferedString(), row);

            _termFrequencies[docId].merge(token, new IcedLong(1), (tf1, tf2) -> new IcedLong(tf1._val + tf2._val));
        }
    }
}
