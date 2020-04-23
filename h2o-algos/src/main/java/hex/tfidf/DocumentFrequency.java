package hex.tfidf;

import water.fvec.Frame;
import water.rapids.ast.prims.mungers.AstGroup;

/**
 * Task class using map-reduce to compute document frequency values for words in given documents.
 */
public class DocumentFrequency {

    /**
     * Computes document frequency values for given words in documents.
     *
     * @param wordFrame input frame of words for which document frequency
     *                  values should be computed. For exact format
     *                  see {@link TermFrequency}.
     *
     * @return frame containing document frequency values for given words.
     */
    public Frame compute(Frame wordFrame) {
        AstGroup.AGG[] aggs = new AstGroup.AGG[1];
        aggs[0] = new AstGroup.AGG(AstGroup.FCN.nrow, 0, AstGroup.NAHandling.ALL, -1);

        int[] groupByColumnsNum = new int[]{};
        int[] groupByColumnsStr = new int[]{ 1 };

        return new AstGroup().performGroupingWithAggregations(wordFrame,
                                                              groupByColumnsNum,
                                                              groupByColumnsStr,
                                                              aggs).getFrame();
    }
}
