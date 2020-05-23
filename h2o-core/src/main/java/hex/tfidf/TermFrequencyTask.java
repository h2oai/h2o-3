package hex.tfidf;

import water.fvec.Frame;
import water.rapids.ast.prims.mungers.AstGroup;

/**
 * Task class using map-reduce to compute term frequency values for words in given documents.
 */
public class TermFrequencyTask {

    /**
     * Column names to be used for output frame.
     */
    private static final String[] OUTPUT_FRAME_COL_NAMES = new String[] { "DocID", "Word", "TF" };

    /**
     * Computes term frequency values for given words in documents.
     * 
     * @param wordFrame input frame of words for which term frequency
     *                  values should be computed. For exact format
     *                  see {@link TfIdfPreprocessorTask}.
     * 
     * @return frame containing term frequency values for given words.
     */
    public static Frame compute(final Frame wordFrame) {
        AstGroup.AGG[] aggs = new AstGroup.AGG[1];
        aggs[0] = new AstGroup.AGG(AstGroup.FCN.nrow, 0, AstGroup.NAHandling.ALL, -1);

        int[] groupByColumns = new int[]{ 0, 1 };

        Frame tfFrame = new AstGroup().performGroupingWithAggregations(wordFrame,
                                                                       groupByColumns,
                                                                       aggs).getFrame();
        tfFrame.setNames(OUTPUT_FRAME_COL_NAMES);

        return tfFrame;
    }
}
