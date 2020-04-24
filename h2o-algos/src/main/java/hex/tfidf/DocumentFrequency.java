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
        AstGroup.AGG[] preprAggs = new AstGroup.AGG[]{};

        int[] preprGroupByColumnsNum = new int[]{ 0 };
        int[] preprGroupByColumnsStr = new int[]{ 1 };
        
        // Preprocess frame to get single token per document
        Frame uniqueWordsPerDocFrame = new AstGroup().performGroupingWithAggregations(wordFrame,
                                                                                      preprGroupByColumnsNum,
                                                                                      preprGroupByColumnsStr,
                                                                                      preprAggs).getFrame();
        
        AstGroup.AGG[] aggs = new AstGroup.AGG[1];
        aggs[0] = new AstGroup.AGG(AstGroup.FCN.nrow, 0, AstGroup.NAHandling.ALL, -1);
        
        int[] groupByColumnsNum = new int[]{ };
        int[] groupByColumnsStr = new int[]{ 1 };
        
        // Construct final frame with DF values
        Frame dfFrame = new AstGroup().performGroupingWithAggregations(uniqueWordsPerDocFrame,
                                                                       groupByColumnsNum,
                                                                       groupByColumnsStr,
                                                                       aggs).getFrame();
        
        uniqueWordsPerDocFrame.remove();
        
        return dfFrame;
    }
}
