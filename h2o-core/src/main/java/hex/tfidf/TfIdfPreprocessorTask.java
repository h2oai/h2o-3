package hex.tfidf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.parser.BufferedString;

/**
 * Map-Reduce task for pre-processing data before computing TF-IDF.<br>
 * <br>
 * 
 * <p>
 * Input row format: <code>documentID, documentString</code>
 * </p>
 *
 * <p>
 * Output row format: <code>documentID, word</code>
 * </p>
 */
public class TfIdfPreprocessorTask extends MRTask<TfIdfPreprocessorTask> {

    /**
     * Words delimiter regex in documents.
     */
    private static final String WORDS_DELIMITER_REGEX = "\\s+";

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
        Chunk inputDocumentIds = cs[0];
        Chunk inputDocs = cs[1];

        NewChunk outputDocumentIds = ncs[0];
        NewChunk outputTokens = ncs[1];

        for (int row = 0; row < inputDocs._len; row++) {
            if (inputDocs.isNA(row))
                continue; // Ignore NAs

            String document = inputDocs.atStr(new BufferedString(), row).toString();
            long documentId = inputDocumentIds.at8(row);
            String[] words = document.split(WORDS_DELIMITER_REGEX);

            for (String word : words) {
                outputDocumentIds.addNum(documentId);
                outputTokens.addStr(word);
            }
        }
    }
}
