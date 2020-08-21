package water.rapids.ast.prims.advmath;

import hex.tfidf.DocumentFrequencyTask;
import hex.tfidf.InverseDocumentFrequencyTask;
import hex.tfidf.TermFrequencyTask;
import hex.tfidf.TfIdfPreprocessorTask;
import org.apache.log4j.Logger;
import water.Key;
import water.MRTask;
import water.Scope;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.Merge;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.prims.string.AstToLower;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;

/**
 * Primitive AST operation to compute TF-IDF values for given document corpus.<br>
 * 
 * <br>
 * <b>Parameters:</b>
 * <p><ul>
 * <li><code>frame</code> - Input frame containing data for whose TF-IDF values should be computed
 * <li><code>doc_id_idx</code> - Index of a column containing document IDs
 * <li><code>text_idx</code> - Index of a column containing words/documents (depending on the <code>preprocess</code> parameter)
 * <li><code>preprocess</code> - Flag indicating whether input should be pre-processed or not
 * <li><code>case_sensitive</code> - Flag indicating whether input should be treated as a case sensitive data
 * </ul><p>
 * 
 * <br>
 * <b>Content of a column with index <code>content_idx</code>:</b>
 * <p><ul>
 * <li>See {@link TfIdfPreprocessorTask} - (default) when pre-processing is enabled
 * <li><code>word</code> - when pre-processing is disabled
 * </ul><p>
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
    private static final String[] PREPROCESSED_FRAME_COL_NAMES = new String[] { "DocID", "Words" };
    /**
     * Class logger.
     */
    private static Logger log = Logger.getLogger(AstTfIdf.class);

    @Override
    public int nargs() {
        return 1 + 5; // (tf-idf input_frame_name doc_id_col_idx text_col_idx preprocess case_sensitive)
    }

    @Override
    public String[] args() {
        return new String[]{ "frame", "doc_id_idx", "text_idx", "preprocess", "case_sensitive"};
    }

    @Override
    public Val apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
        Frame inputFrame = stk.track(asts[1].exec(env).getFrame());
        final int docIdIdx = (int) asts[2].exec(env).getNum();
        final int contentIdx = (int) asts[3].exec(env).getNum();
        final boolean preprocess = asts[4].exec(env).getBool();
        final boolean caseSensitive = asts[5].exec(env).getBool();
        
        if (inputFrame.anyVec().length() <= 0)
            throw new IllegalArgumentException("Empty input frame provided.");

        Scope.enter();
        Frame tfIdfFrame = null;
        try {
            // Input checks
            int inputFrameColsCnt = inputFrame.numCols();
            if (docIdIdx >= inputFrameColsCnt || contentIdx >= inputFrameColsCnt)
                throw new IllegalArgumentException("Provided column index is out of bounds. Number of columns in the input frame: "
                                                   + inputFrameColsCnt);
            Vec docIdVec = inputFrame.vec(docIdIdx);
            Vec contentVec = inputFrame.vec(contentIdx);
            
            if (!docIdVec.isNumeric() || !contentVec.isString())
                throw new IllegalArgumentException("Incorrect format of input frame." +
                                                   "Following row format is expected: (numeric) documentID, (string) "
                                                   + (preprocess ? "documentContent." : "words. " +
                                                   "Got "+docIdVec.get_type_str() + " and " + contentVec.get_type_str() 
                                                   +" instead."));

            // Case sensitivity
            if (!caseSensitive) {
                Scope.track(inputFrame.replace(contentIdx, AstToLower.toLowerStringCol(inputFrame.vec(contentIdx))));
            }

            // Pre-processing
            Frame wordFrame;
            long documentsCnt;
            if (preprocess) {
                byte[] outputTypes = new byte[]{ Vec.T_NUM, Vec.T_STR };
                
                wordFrame = new TfIdfPreprocessorTask(docIdIdx, contentIdx).doAll(outputTypes, inputFrame)
                                .outputFrame(PREPROCESSED_FRAME_COL_NAMES, null);
                documentsCnt = inputFrame.numRows();
            } else {
                String[] columnsNames = ArrayUtils.select(inputFrame.names(), new int[]{ docIdIdx, contentIdx });
                wordFrame = inputFrame.subframe(columnsNames);
                String countDocumentsRapid = "(unique (cols " + asts[1].toString() + " [" + docIdIdx + "]) false)";
                documentsCnt = Rapids.exec(countDocumentsRapid).getFrame().anyVec().length();
            }
            Scope.track(wordFrame);

            // TF
            Frame tfOutFrame = TermFrequencyTask.compute(wordFrame);
            Scope.track(tfOutFrame);
            
            // DF
            Frame dfOutFrame = DocumentFrequencyTask.compute(tfOutFrame);
            Scope.track(dfOutFrame);

            // IDF
            InverseDocumentFrequencyTask idf = new InverseDocumentFrequencyTask(documentsCnt);
            Vec idfValues = idf.doAll(new byte[]{ Vec.T_NUM }, dfOutFrame.lastVec()).outputFrame().anyVec();
            Scope.track(idfValues);
            // Replace DF column with IDF column
            Vec removedCol = dfOutFrame.remove(dfOutFrame.numCols() - 1);
            Scope.track(removedCol);
            dfOutFrame.add(IDF_COL_NAME, idfValues);

            // Intermediate frame containing both TF and IDF values
            Scope.track(tfOutFrame.replace(1, tfOutFrame.vecs()[1].toCategoricalVec()));
            Scope.track(dfOutFrame.replace(0, dfOutFrame.vecs()[0].toCategoricalVec()));
            int[][] levelMaps = {
                    CategoricalWrappedVec.computeMap(tfOutFrame.vec(1).domain(), dfOutFrame.vec(0).domain())
            };
            Frame tfIdfIntermediate = Merge.merge(tfOutFrame, dfOutFrame, new int[]{1}, new int[]{0}, false, levelMaps);
            Scope.track(tfIdfIntermediate.replace(1, tfIdfIntermediate.vecs()[1].toStringVec()));

            // TF-IDF
            int tfOutFrameColCnt = tfIdfIntermediate.numCols();
            TfIdfTask tfIdfTask = new TfIdfTask(tfOutFrameColCnt - 2, tfOutFrameColCnt - 1);
            Vec tfIdfValues = tfIdfTask.doAll(new byte[]{Vec.T_NUM}, tfIdfIntermediate).outputFrame().anyVec();
            Scope.track(tfIdfValues);

            // Construct final frame containing TF, IDF and TF-IDF values
            tfIdfIntermediate.add(TF_IDF_COL_NAME, tfIdfValues);
            tfIdfIntermediate._key = Key.make();

            if (log.isDebugEnabled())
                log.debug(tfIdfIntermediate.toTwoDimTable().toString());

            tfIdfFrame = tfIdfIntermediate;
        } finally {
            Key[] keysToKeep = tfIdfFrame != null ? tfIdfFrame.keys() : new Key[]{};
            Scope.exit(keysToKeep);
        }
        
        return new ValFrame(tfIdfFrame);
    }

    @Override
    public String str() {
        return "tf-idf";
    }

    /**
     * Final TF-IDF Map-Reduce task used to combine TF and IDF values together.
     */
    private static class TfIdfTask extends MRTask<TfIdfTask> {
        
        // IN
        /**
         * Index of a column containing Term Frequency values in the input frame of this task.
         */
        private final int _tfColIndex;
        /**
         * Index of a column containing Inverse Document Frequency values in the input frame of this task.
         */
        private final int _idfColIndex;
        
        private TfIdfTask(int tfColIndex, int idfColIndex) {
            _tfColIndex = tfColIndex;
            _idfColIndex = idfColIndex;
        }

        @Override
        public void map(Chunk[] cs, NewChunk nc) {
            Chunk tfValues = cs[_tfColIndex];
            Chunk idfValues = cs[_idfColIndex];
            
            for (int row = 0; row < tfValues._len; row++) {
                nc.addNum(tfValues.at8(row) * idfValues.atd(row));
            }
        }
    }
}
