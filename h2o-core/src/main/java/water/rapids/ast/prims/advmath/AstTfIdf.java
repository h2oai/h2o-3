package water.rapids.ast.prims.advmath;

import hex.tfidf.DocumentFrequency;
import hex.tfidf.InverseDocumentFrequency;
import hex.tfidf.TermFrequency;
import hex.tfidf.TfIdfPreprocessor;
import water.Key;
import water.MRTask;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.Merge;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

/**
 * Primitive AST operation to compute TF-IDF values for given document corpus.
 */
public class AstTfIdf extends AstPrimitive<AstTfIdf> {

    /**
     * Name to be used for a column containing Inverse Document Frequency values in the output frame of this operation.
     */
    private static final String IDF_COL_NAME = "IDF";
    /**
     * Name to be used for a column containing TF-IDF values in the output frame of this operation.
     */
    private static final String TF_IDF_COL_NAME = "TF-IDF";
    /**
     * Column names to be used for preprocessed frame.
     */
    private static final String[] PREPROCESSED_FRAME_COL_NAMES = new String[] { "DocID", "Word" };

    @Override
    public int nargs() {
        return 1 + 1; // Frame ID and ID of an input frame
    }

    @Override
    public String[] args() {
        return new String[]{ "frame" };
    }

    @Override
    public Val apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
        Frame inputFrame = stk.track(asts[1].exec(env).getFrame());

        byte[] outputTypes = new byte[]{ Vec.T_NUM, Vec.T_STR };

        // Pre-processing
        TfIdfPreprocessor preprocessor = new TfIdfPreprocessor();
        Frame wordFrame = preprocessor.doAll(outputTypes, inputFrame).outputFrame(PREPROCESSED_FRAME_COL_NAMES, null);

        // DF
        DocumentFrequency dfTaks = new DocumentFrequency();
        Frame dfOutFrame = dfTaks.compute(wordFrame);

        // IDF
        long documentsCnt = inputFrame.numRows();
        InverseDocumentFrequency idf = new InverseDocumentFrequency(documentsCnt);
        Vec idfValues = idf.doAll(new byte[]{ Vec.T_NUM }, dfOutFrame.lastVec()).outputFrame().anyVec();
        // Replace DF column with IDF column
        dfOutFrame.remove(dfOutFrame.numCols() - 1);
        dfOutFrame.add(IDF_COL_NAME, idfValues);

        // TF
        TermFrequency tfTask = new TermFrequency();
        Frame tfOutFrame = tfTask.compute(wordFrame);
        
        // Intermediate frame containing both TF and IDF values
        // TODO: Avoid converting a string column to categorical
        tfOutFrame.replace(1, tfOutFrame.vecs()[1].toCategoricalVec());
        dfOutFrame.replace(0, dfOutFrame.vecs()[0].toCategoricalVec());
        int[][] levelMaps = {
                CategoricalWrappedVec.computeMap(tfOutFrame.vec(1).domain(), dfOutFrame.vec(0).domain())
        };
        Frame tfIdfIntermediate = Merge.merge(tfOutFrame, dfOutFrame, new int[]{ 1 }, new int[]{ 0 }, false, levelMaps);
        // TODO: Convert columnt back to string
        tfIdfIntermediate.replace(1, tfIdfIntermediate.vecs()[1].toStringVec());

        // TF-IDF
        int tfOutFrameColCnt = tfIdfIntermediate.numCols();
        TfIdf tfIdfTask = new TfIdf(tfOutFrameColCnt - 2, tfOutFrameColCnt - 1);
        Vec tfIdfValues = tfIdfTask.doAll(new byte[]{ Vec.T_NUM }, tfIdfIntermediate).outputFrame().anyVec();

        // Construct final frame containing TF, IDF and TF-IDF values
        tfIdfIntermediate.add(TF_IDF_COL_NAME, tfIdfValues);
        tfIdfIntermediate._key = Key.make();

        System.out.println(tfIdfIntermediate.toTwoDimTable().toString());
        
        return new ValFrame(tfIdfIntermediate);
    }

    @Override
    public String str() {
        return "tf-idf";
    }

    /**
     * Final TF-IDF Map-Reduce task used to combine TF and IDF values together.
     */
    private static class TfIdf extends MRTask<TfIdf> {
        
        // IN
        /**
         * Index of a column containing Term Frequency values in the input frame of this task.
         */
        private final int _tfColIndex;
        /**
         * Index of a column containing Inverse Document Frequency values in the input frame of this task.
         */
        private final int _idfColIndex;
        
        private TfIdf(int tfColIndex, int idfColIndex) {
            _tfColIndex = tfColIndex;
            _idfColIndex = idfColIndex;
        }

        @Override
        public void map(Chunk[] cs, NewChunk nc) {
            Chunk tfValues = cs[_tfColIndex];
            Chunk idfValues = cs[_idfColIndex];
            
            for (int row = 0; row < tfValues._len; row++) {
                nc.addNum(tfIdf(tfValues.at8(row), idfValues.atd(row)));
            }
        }

        /**
         * Computes TF-IDF value from given TF and IDF values.
         * 
         * @param tf    TF value to be used to compute TF-IDF.
         * @param idf   IDF value to be used to compute TF-IDF.
         *
         * @return TF-IDF value from given TF and IDF values.
         */
        private static double tfIdf(final long tf, final double idf) {
            return tf * idf;
        }
    }
}
