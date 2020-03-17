package hex.tfidf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.parser.BufferedString;

/**
 * Map-Reduce task for preprocessing data before computing TF-IDF.
 */
public class TfIdfPreprocessor extends MRTask<TfIdfPreprocessor> {

    /**
     * Words delimiter in documents.
     */
    // TODO
    private static final String WORDS_DELIMITER = " ";

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
        Chunk inputDocs = cs[0];
        Chunk inputDocumentIds = cs[1];

        NewChunk outputDocs = ncs[0];
        NewChunk outputDocumentIds = ncs[1];
        NewChunk outputTokens = ncs[2];

        for (int row = 0; row < inputDocs._len; row++) {
            if (inputDocs.isNA(row))
                continue; // Ignore NAs

            String document = inputDocs.atStr(new BufferedString(), row).toString();
            long documentId = inputDocumentIds.at8(row);
            String[] words = document.split(WORDS_DELIMITER);

            for (String word : words) {
                outputDocs.addStr(document);
                outputDocumentIds.addNum(documentId);
                outputTokens.addStr(word);
            }
        }
    }
}
