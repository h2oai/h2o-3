package hex.tree.xgboost;

import hex.DataInfo;
import hex.tree.xgboost.matrix.MatrixFactoryUtils;
import hex.tree.xgboost.matrix.SparseMatrixFactory;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import ml.dmlc.xgboost4j.java.util.BigDenseMatrix;
import water.H2O;
import water.LocalMR;
import water.MrFun;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.VecUtils;

import java.util.*;

import static hex.tree.xgboost.matrix.MatrixFactoryUtils.setResponseAndWeight;
import static water.H2O.technote;
import static water.MemoryManager.malloc4f;

public class XGBoostUtils {

    public static String makeFeatureMap(Frame f, DataInfo di) {
        // set the names for the (expanded) columns
        String[] coefnames = di.coefNames();
        StringBuilder sb = new StringBuilder();
        assert(coefnames.length == di.fullN());
        int catCols = di._catOffsets[di._catOffsets.length-1];

        for (int i = 0; i < di.fullN(); ++i) {
            sb.append(i).append(" ").append(coefnames[i].replaceAll("\\s*","")).append(" ");
            if (i < catCols || f.vec(i-catCols).isBinary())
                sb.append("i");
            else if (f.vec(i-catCols).isInt())
                sb.append("int");
            else
                sb.append("q");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * convert an H2O Frame to a sparse DMatrix
     * @param di data info
     * @param f H2O Frame - adapted using a provided data info
     * @param response name of the response column
     * @param weight name of the weight column
     * @return DMatrix
     * @throws XGBoostError
     */
    public static DMatrix convertFrameToDMatrix(DataInfo di,
                                                Frame f,
                                                String response,
                                                String weight,
                                                boolean sparse) throws XGBoostError {
        assert di != null;
        int[] chunks = VecUtils.getLocalChunkIds(f.anyVec());
        final Vec responseVec = f.vec(response);
        final Vec weightVec = f.vec(weight);
        final int[] nRowsByChunk = new int[chunks.length];
        final long nRowsL = sumChunksLength(chunks, responseVec, weightVec, nRowsByChunk);
        if (nRowsL > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("XGBoost currently doesn't support datasets with more than " +
                    Integer.MAX_VALUE + " per node. " +
                    "To train a XGBoost model on this dataset add more nodes to your H2O cluster and use distributed training.");
        }
        final int nRows = (int) nRowsL;

        final DMatrix trainMat;

        // In the future this 2 arrays might also need to be rewritten into float[][],
        // but only if we want to handle datasets over 2^31-1 on a single machine. For now I'd leave it as it is.
        float[] resp = malloc4f(nRows);
        float[] weights = null;
        if (weightVec != null) {
            weights = malloc4f(nRows);
        }
        if (sparse) {
            Log.debug("Treating matrix as sparse.");
            // truly sparse matrix - no categoricals
            // collect all nonzeros column by column (in parallel), then stitch together into final data structures
            trainMat = SparseMatrixFactory.csr(f, chunks, weightVec, responseVec, di, resp, weights);
        } else {
            Log.debug("Treating matrix as dense.");
            BigDenseMatrix data = null;
            try {
                data = allocateDenseMatrix(nRows, di);
                long actualRows = denseChunk(data, chunks, nRowsByChunk, f, weightVec, responseVec, di, resp, weights);
                assert data.nrow == actualRows;
                trainMat = new DMatrix(data, Float.NaN);
            } finally {
                if (data != null) {
                    data.dispose();
                }
            }
        }

        assert trainMat.rowNum() == nRows;
        trainMat.setLabel(resp);
        if (weights != null) {
            trainMat.setWeight(weights);
        }

        return trainMat;
    }

    /**
     * Counts a total sum of chunks inside a vector. Only chunks present in chunkIds are considered.
     *
     * @param chunkIds Chunk identifier of a vector
     * @param vec      Vector containing given chunk identifiers
     * @param weightsVector Vector with row weights, possibly null
     * @return A sum of chunk lengths. Possibly zero, if there are no chunks or the chunks are empty.
     */
    private static long sumChunksLength(int[] chunkIds, Vec vec, Vec weightsVector, int[] chunkLengths) {
        for (int i = 0; i < chunkIds.length; i++) {
            final int chunk = chunkIds[i];
            chunkLengths[i] = vec.chunkLen(chunk);
            if (weightsVector == null)
                continue;

            Chunk weightVecChunk = weightsVector.chunkForChunkIdx(chunk);
            if (weightVecChunk.atd(0) == 0) chunkLengths[i]--;
            int nzIndex = 0;
            do {
                nzIndex = weightVecChunk.nextNZ(nzIndex, true);
                if (nzIndex < 0 || nzIndex >= weightVecChunk._len) break;
                if (weightVecChunk.atd(nzIndex) == 0) chunkLengths[i]--;
            } while (true);
        }

        long totalChunkLength = 0;
        for (int cl : chunkLengths) {
            totalChunkLength += cl;
        }
        return totalChunkLength;
    }

    /**
     * convert a set of H2O chunks (representing a part of a vector) to a sparse DMatrix
     * @param response name of the response column
     * @return DMatrix
     * @throws XGBoostError
     */
    public static DMatrix convertChunksToDMatrix(DataInfo di,
                                                 Chunk[] chunks,
                                                 int response,
                                                 boolean sparse) throws XGBoostError {
        int nRows = chunks[0]._len;
        DMatrix trainMat;
        float[] resp = malloc4f(nRows);
        try {
            if (sparse) {
                Log.debug("Treating matrix as sparse.");
                trainMat = SparseMatrixFactory.csr(chunks, -1, response, di, resp, null);
            } else {
                trainMat = dense(chunks, di, response, resp, null);
            }
        } catch (NegativeArraySizeException e) {
            throw new IllegalArgumentException(technote(11,
                "Data is too large to fit into the 32-bit Java float[] array that needs to be passed to the XGBoost C++ backend. Use H2O GBM instead."));
        }

        int len = (int) trainMat.rowNum();
        resp = Arrays.copyOf(resp, len);
        trainMat.setLabel(resp);
        return trainMat;
    }

    /****************************************************************************************************************
     ************************************** DMatrix creation for dense matrices *************************************
     ****************************************************************************************************************/

    private static DMatrix dense(Chunk[] chunks, DataInfo di, int respIdx, float[] resp, float[] weights) throws XGBoostError {
        Log.debug("Treating matrix as dense.");
        BigDenseMatrix data = null;
        try {
            data = allocateDenseMatrix(chunks[0].len(), di);
            long actualRows = denseChunk(data, chunks, respIdx, di, resp, weights);
            assert actualRows == data.nrow;
            return new DMatrix(data, Float.NaN);
        } finally {
            if (data != null) {
                data.dispose();
            }
        }
    }
    
    private static long denseChunk(BigDenseMatrix data,
                                   int[] chunks, int[] nRowsByChunk, Frame f, Vec weightsVec, Vec respVec, DataInfo di,
                                   float[] resp, float[] weights) {
        int[] offsets = new int[nRowsByChunk.length + 1];
        for (int i = 0; i < chunks.length; i++) {
            offsets[i + 1] = nRowsByChunk[i] + offsets[i];
        }
        WriteDenseChunkFun writeFun = new WriteDenseChunkFun(f, chunks, offsets, weightsVec, respVec, di, data, resp, weights);
        H2O.submitTask(new LocalMR(writeFun, chunks.length)).join();
        return writeFun.getTotalRows();
    }

    private static class WriteDenseChunkFun extends MrFun<WriteDenseChunkFun> {
        private final Frame _f;
        private final int[] _chunks;
        private final int[] _offsets;
        private final Vec _weightsVec;
        private final Vec _respVec;
        private final DataInfo _di;
        private final BigDenseMatrix _data;
        private final float[] _resp;
        private final float[] _weights;

        // OUT
        private int[] _nRowsByChunk;

        private WriteDenseChunkFun(Frame f, int[] chunks, int[] offsets, Vec weightsVec, Vec respVec, DataInfo di,
                                   BigDenseMatrix data, float[] resp, float[] weights) {
            _f = f;
            _chunks = chunks;
            _offsets = offsets;
            _weightsVec = weightsVec;
            _respVec = respVec;
            _di = di;
            _data = data;
            _resp = resp;
            _weights = weights;
            _nRowsByChunk = new int[chunks.length];
        }

        @Override
        protected void map(int id) {
            final int chunkIdx = _chunks[id];
            Chunk[] chks = new Chunk[_f.numCols()];
            for (int c = 0; c < chks.length; c++) {
                chks[c] = _f.vec(c).chunkForChunkIdx(chunkIdx);
            }
            Chunk weightsChk = _weightsVec != null ? _weightsVec.chunkForChunkIdx(chunkIdx) : null;
            Chunk respChk = _respVec.chunkForChunkIdx(chunkIdx);
            long idx = _offsets[id] * _data.ncol;
            int actualRows = 0;
            for (int i = 0; i < chks[0]._len; i++) {
                if (weightsChk != null && weightsChk.atd(i) == 0) continue;

                idx = writeDenseRow(_di, chks, i, _data, idx);
                _resp[_offsets[id] + actualRows] = (float) respChk.atd(i);
                if (weightsChk != null) {
                    _weights[_offsets[id] + actualRows] = (float) weightsChk.atd(i);
                }

                actualRows++;
            }
            assert idx == (long) _offsets[id + 1] * _data.ncol;
            _nRowsByChunk[id] = actualRows;
        }

        private long getTotalRows() {
            long totalRows = 0;
            for (int r : _nRowsByChunk) {
                totalRows += r;
            }
            return totalRows;
        }

    }

    private static long denseChunk(BigDenseMatrix data, Chunk[] chunks, int respIdx, DataInfo di, float[] resp, float[] weights) {
        long idx = 0;
        long actualRows = 0;
        int rwRow = 0;
        for (int i = 0; i < chunks[0]._len; i++) {

            idx = writeDenseRow(di, chunks, i, data, idx);
            actualRows++;

            rwRow = setResponseAndWeight(chunks, respIdx, -1, resp, weights, rwRow, i);
        }
        assert (long) data.nrow * data.ncol == idx;
        return actualRows;
    }

    private static long writeDenseRow(DataInfo di, Chunk[] chunks, int rowInChunk,
                                      BigDenseMatrix data, long idx) {
        for (int j = 0; j < di._cats; ++j) {
            int len = di._catOffsets[j+1] - di._catOffsets[j];
            double val = chunks[j].isNA(rowInChunk) ? Double.NaN : chunks[j].at8(rowInChunk);
            int pos = di.getCategoricalId(j, val) - di._catOffsets[j];
            for (int cat = 0; cat < len; cat++)
                data.set(idx + cat, 0f); // native memory => explicit zero-ing is necessary
            data.set(idx + pos, 1f);
            idx += len;
        }
        for (int j = 0; j < di._nums; ++j) {
            float val = chunks[di._cats + j].isNA(rowInChunk) ? Float.NaN : (float) chunks[di._cats + j].atd(rowInChunk);
            data.set(idx++, val);
        }
            return idx;
    }

    /****************************************************************************************************************
     *********************************** DMatrix creation for sparse (CSR) matrices *********************************
     ****************************************************************************************************************/

    
    /**
     * Allocated an exactly-sized float[] array serving as a backing array for XGBoost's {@link DMatrix}.
     * The backing array created by this method does not contain any actual data and needs to be filled.
     *
     * @param rowCount Number of rows to allocate data for
     * @param dataInfo An instance of {@link DataInfo}
     * @return An exactly-sized Float[] backing array for XGBoost's {@link DMatrix} to be filled with data.
     */
    private static BigDenseMatrix allocateDenseMatrix(final int rowCount, final DataInfo dataInfo) {
        return new BigDenseMatrix(rowCount, dataInfo.fullN());
    }

    public static FeatureProperties assembleFeatureNames(final DataInfo di) {
        String[] coefnames = di.coefNames();
        assert (coefnames.length == di.fullN());
        int numCatCols = di._catOffsets[di._catOffsets.length - 1];

        String[] featureNames = new String[di.fullN()];
        boolean[] oneHotEncoded = new boolean[di.fullN()];
        for (int i = 0; i < di.fullN(); ++i) {
            featureNames[i] = coefnames[i];
            if (i < numCatCols) {
                oneHotEncoded[i] = true;
            }
        }
        return new FeatureProperties(featureNames, oneHotEncoded);
    }

    static class FeatureProperties {
        public String[] _names;
        public boolean[] _oneHotEncoded;

        public FeatureProperties(String[] names, boolean[] oneHotEncoded) {
            _names = names;
            _oneHotEncoded = oneHotEncoded;
        }
    }

    static Map<String, FeatureScore> parseFeatureScores(String[] modelDump) {
        Map<String, FeatureScore> featureScore = new HashMap<>();
        for (String tree : modelDump) {
            for (String node : tree.split("\n")) {
                String[] array = node.split("\\[", 2);
                if (array.length < 2)
                    continue;
                String[] content = array[1].split("\\]", 2);
                if (content.length < 2)
                    continue;
                String fid = content[0].split("<")[0];

                FeatureScore fs = new FeatureScore();
                String[] keyValues = content[1].split(",");
                for (String keyValue : keyValues) {
                    if (keyValue.startsWith(FeatureScore.GAIN_KEY + "=")) {
                        fs._gain = Float.parseFloat(keyValue.substring(FeatureScore.GAIN_KEY.length() + 1));
                    } else if (keyValue.startsWith(FeatureScore.COVER_KEY + "=")) {
                        fs._cover = Float.parseFloat(keyValue.substring(FeatureScore.COVER_KEY.length() + 1));
                    }
                }
                fs._frequency = 1;
                
                if (featureScore.containsKey(fid)) {
                    featureScore.get(fid).add(fs);
                } else {
                    featureScore.put(fid, fs);
                }
            }
        }
        return featureScore;
    }

    static class FeatureScore {
        static final String GAIN_KEY = "gain";
        static final String COVER_KEY = "cover";
        
        int _frequency;
        float _gain;
        float _cover;

        void add(FeatureScore fs) {
            _frequency += fs._frequency;
            _gain += fs._gain;
            _cover += fs._cover;
        }
    }

}
