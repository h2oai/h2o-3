package hex.tfidf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

/**
 * Map-reduce task computing inverse document frequency values for words in given documents.
 */
public class InverseDocumentFrequencyTask extends MRTask<InverseDocumentFrequencyTask> {

    // IN
    /**
     * Number of documents in given corpus.
     */
    private final long _documentsCnt;
    
    public InverseDocumentFrequencyTask(long documentsCnt) {
        _documentsCnt = documentsCnt;
    }

    /**
     * Computes Inverse Document Frequency value for a word with given Document Frequency.
     *
     * @param documentFrequency DF value of a word for which IDF should be computed.
     *
     * @return  Inverse Document Frequency value for a word with given Document Frequency.
     */
    private double idf(long documentFrequency) {
        return Math.log(((double)(_documentsCnt + 1)) / (documentFrequency + 1));
    }

    @Override
    public void map(Chunk cs, NewChunk nc) {
        for (int row = 0; row < cs._len; row++) {
            if (cs.isNA(row))
                continue; // Ignore NAs

            nc.addNum(idf(cs.at8(row)));
        }
    }
}
