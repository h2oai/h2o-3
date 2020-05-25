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
     * @param wordFrame input frame of <b>unique</b> words (unique per document) for which document frequency values 
     *                  should be computed. Expected row format: <code>docID, word</code>
     *
     * @return frame containing document frequency values for given words.
     */
    public static Frame compute(final Frame wordFrame) {
        Scope.enter();
        Frame dfFrame;
        try {
            AstGroup.AGG[] aggs = new AstGroup.AGG[1];
            aggs[0] = new AstGroup.AGG(AstGroup.FCN.nrow, 0, AstGroup.NAHandling.ALL, -1);

            int[] groupByColumns = new int[]{ 1 };

            // Construct final frame with DF values
            dfFrame = new AstGroup().performGroupingWithAggregations(wordFrame,
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
