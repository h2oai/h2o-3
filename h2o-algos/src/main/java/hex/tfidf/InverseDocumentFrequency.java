package hex.tfidf;

import water.parser.BufferedString;
import water.util.IcedDouble;
import water.util.IcedHashMap;
import water.util.IcedLong;

import java.util.Map;

/**
 * Class for computing Inverse Document Frequency values for words in given documents.
 */
public class InverseDocumentFrequency {
    
    // OUT
    public IcedHashMap<BufferedString, IcedLong> _documentFrequencies;
    public IcedHashMap<BufferedString, IcedDouble> _inverseDocumentFrequencies;

    // IN
    /**
     * Number of documents in given corpus.
     */
    private final int _documentsCnt;
    
    public InverseDocumentFrequency(int documentsCnt) {
        _documentsCnt = documentsCnt;
        _documentFrequencies = new IcedHashMap<>();
        _inverseDocumentFrequencies = new IcedHashMap<>();
    }
    
    /**
     * Computes Inverse Document Frequency and save results in 
     * corresponding output instance attributes of this class.
     * 
     * @param termFrequencies   Term Frequency values of words in each document whose 
     *                          IDF values should be computed.
     */
    public void computeIDF(Map<BufferedString, IcedLong>[] termFrequencies) {
        // Document frequency
        for (Map<BufferedString, IcedLong> docTermFrequencies : termFrequencies) {
            docTermFrequencies.forEach((word, tfValue) ->
                _documentFrequencies.merge(word, 
                                           new IcedLong(1), 
                                           (df1, df2) -> new IcedLong(df1._val + df2._val)));
        }
        
        // Inverse document frequency
        _documentFrequencies.forEach((word, dfValue) ->
                _inverseDocumentFrequencies.put(word, new IcedDouble(idf(dfValue._val))));
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
}
