package hex.tfidf;

import water.Scope;
import water.fvec.Frame;
import water.rapids.ast.prims.mungers.AstGroup;

/**
 * Task class using map-reduce to compute document frequency values for words in given documents.
 */
public class DocumentFrequencyTask {

    /**
     * Column names to be used for output frame.
     */
    private static final String[] OUTPUT_FRAME_COL_NAMES = new String[] { "Word", "DF" };

    /**
     * Computes document frequency values for given words in documents.
     *
     * @param wordFrame input frame of words for which document frequency
     *                  values should be computed. For exact format
     *                  see {@link TermFrequencyTask}.
     *
     * @return frame containing document frequency values for given words.
     */
    public static Frame compute(final Frame wordFrame) {
        AstGroup.AGG[] preprAggs = new AstGroup.AGG[]{};

        int[] preprGroupByColumns = new int[]{ 0, 1 };

        Scope.enter();
        Frame dfFrame;
        try {
            // Preprocess frame to get single token per document
            Frame uniqueWordsPerDocFrame = new AstGroup().performGroupingWithAggregations(wordFrame,
                                                                                          preprGroupByColumns,
                                                                                          preprAggs).getFrame();
            Scope.track(uniqueWordsPerDocFrame);

            AstGroup.AGG[] aggs = new AstGroup.AGG[1];
            aggs[0] = new AstGroup.AGG(AstGroup.FCN.nrow, 0, AstGroup.NAHandling.ALL, -1);

            int[] groupByColumns = new int[]{1};

            // Construct final frame with DF values
            dfFrame = new AstGroup().performGroupingWithAggregations(uniqueWordsPerDocFrame,
                                                                     groupByColumns,
                                                                     aggs).getFrame();
            Scope.untrack(dfFrame.keys());
            dfFrame.setNames(OUTPUT_FRAME_COL_NAMES);
        } finally {
            Scope.exit();
        }
        
        return dfFrame;
    }
}
