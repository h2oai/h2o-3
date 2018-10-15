package hex.tree.xgboost;

import hex.DataInfo;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import ml.dmlc.xgboost4j.java.util.BigDenseMatrix;
import water.H2O;
import water.Key;
import water.LocalMR;
import water.MrFun;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.VecUtils;

import java.util.*;

import static water.H2O.technote;
import static water.MemoryManager.malloc4;
import static water.MemoryManager.malloc4f;
import static water.MemoryManager.malloc8;

public class XGBoostUtils {

    /**
     * Arbitrary chosen initial size of array allocated for XGBoost's purpose.
     * Used in case of sparse matrices.
     */
    private static final int ALLOCATED_ARRAY_LEN = 1048576;

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
     * @param f H2O Frame
     * @param onlyLocal if true uses only chunks local to this node
     * @param response name of the response column
     * @param weight name of the weight column
     * @param fold name of the fold assignment column
     * @return DMatrix
     * @throws XGBoostError
     */
    public static DMatrix convertFrameToDMatrix(Key<DataInfo> dataInfoKey,
                                                Frame f,
                                                boolean onlyLocal,
                                                String response,
                                                String weight,
                                                String fold,
                                                boolean sparse) throws XGBoostError {

        int[] chunks;
        Vec vec = f.anyVec();
        if(!onlyLocal) {
            // All chunks
            chunks = new int[f.anyVec().nChunks()];
            for(int i = 0; i < chunks.length; i++) {
                chunks[i] = i;
            }
        } else {
            chunks = VecUtils.getLocalChunkIds(f.anyVec());
        }
        final Vec weightVector = f.vec(weight);
        final int[] nRowsByChunk = new int[chunks.length];
        final long nRowsL = sumChunksLength(chunks, vec, weightVector, nRowsByChunk);
        if (nRowsL > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("XGBoost currently doesn't support datasets with more than " +
                    Integer.MAX_VALUE + " per node. " +
                    "To train a XGBoost model on this dataset add more nodes to your H2O cluster and use distributed training.");
        }
        final int nRows = (int) nRowsL;

        final DataInfo di = dataInfoKey.get();
        assert di != null;
        final DMatrix trainMat;

        // In the future this 2 arrays might also need to be rewritten into float[][],
        // but only if we want to handle datasets over 2^31-1 on a single machine. For now I'd leave it as it is.
        float[] resp = malloc4f(nRows);
        float[] weights = null;
        if (weightVector != null) {
            weights = malloc4f(nRows);
        }

        if (sparse) {
            Log.debug("Treating matrix as sparse.");
            // 1 0 2 0
            // 4 0 0 3
            // 3 1 2 0
            boolean csc = false; //di._cats == 0;

            // truly sparse matrix - no categoricals
            // collect all nonzeros column by column (in parallel), then stitch together into final data structures
            Vec.Reader w = weight == null ? null : weightVector.new Reader();
            if (csc) {
                trainMat = csc(f, chunks, w, f.vec(response).new Reader(), nRows, di, resp, weights);
            } else {
                Vec.Reader[] vecs = new Vec.Reader[f.numCols()];
                for (int i = 0; i < vecs.length; ++i) {
                    vecs[i] = f.vec(i).new Reader();
                }
                trainMat = csr(f, chunks, vecs, w, f.vec(response).new Reader(), nRows, di, resp, weights);
            }
        } else {
            Log.debug("Treating matrix as dense.");
            BigDenseMatrix data = null;
            try {
                data = allocateDenseMatrix(nRows, di);
                long actualRows = denseChunk(data, chunks, nRowsByChunk, f, weightVector, f.vec(response), di, resp, weights);
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

    // FIXME this and the other method should subtract rows where response is 0
    private static int getDataRows(Chunk[] chunks, Frame f, int[] chunksIds, int cols) {
        double totalRows = 0;
        if(null != chunks) {
            for (Chunk ch : chunks) {
                totalRows += ch.len();
            }
        } else {
            for(int chunkId : chunksIds) {
                totalRows += f.anyVec().chunkLen(chunkId);
            }
        }
        return (int) Math.ceil(totalRows * cols / ARRAY_MAX);
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

    private static int setResponseAndWeight(Chunk[] chunks, int respIdx, int weightIdx, float[] resp, float[] weights, int j, int i) {
        if (weightIdx != -1) {
            if(chunks[weightIdx].atd(i) == 0) {
                return j;
            }
            weights[j] = (float) chunks[weightIdx].atd(i);
        }
        resp[j++] = (float) chunks[respIdx].atd(i);
        return j;
    }

    private static int setResponseAndWeight(Vec.Reader w, float[] resp, float[] weights, Vec.Reader respVec, int j, long i) {
        if (w != null) {
            if(w.at(i) == 0) {
                return j;
            }
            weights[j] = (float) w.at(i);
        }
        resp[j++] = (float) respVec.at(i);
        return j;
    }

    private static int getNzCount(Frame f, int[] chunks, final Vec.Reader w, int nCols, List<SparseItem>[] col, int nzCount) {
        for (int i=0;i<nCols;++i) { //TODO: parallelize over columns
            Vec v = f.vec(i);
            for (Integer c : chunks) {
                Chunk ck = v.chunkForChunkIdx(c);
                int[] nnz = new int[ck.sparseLenZero()];
                int nnzCount = ck.nonzeros(nnz);
                nzCount = getNzCount(new ZeroWeight() {
                    @Override
                    public boolean zeroWeight(int idx) {
                        return w != null && w.at(idx) == 0;
                    }
                }, col[i], nzCount, ck, nnz, nnzCount, false);
            }
        }
        return nzCount;
    }

    interface ZeroWeight {
        boolean zeroWeight(int idx);
    }

    private static int getNzCount(final Chunk[] chunks, final int weight, int nCols, List<SparseItem>[] col, int nzCount) {
        for (int i=0;i<nCols;++i) { //TODO: parallelize over columns
            final Chunk ck = chunks[i];
            int[] nnz = new int[ck.sparseLenZero()];
            int nnzCount = ck.nonzeros(nnz);
            nzCount = getNzCount(new ZeroWeight() {
                @Override
                public boolean zeroWeight(int idx) {
                    return weight != -1 && ck.atd(idx) == 0;
                }
            }, col[i], nzCount, ck, nnz, nnzCount, true);
        }
        return nzCount;
    }

    private static int getNzCount(ZeroWeight zw, List<SparseItem> sparseItems, int nzCount, Chunk ck, int[] nnz, int nnzCount, boolean localWeight) {
        for (int k=0;k<nnzCount;++k) {
            SparseItem item = new SparseItem();
            int localIdx = nnz[k];
            item.pos = (int)ck.start() + localIdx;
            // both 0 and NA are omitted in the sparse DMatrix
            if (zw.zeroWeight(localWeight ? localIdx : item.pos)) continue;
            if (ck.isNA(localIdx)) continue;
            item.val = ck.atd(localIdx);
            sparseItems.add(item);
            nzCount++;
        }
        return nzCount;
    }

    /**
     * convert a set of H2O chunks (representing a part of a vector) to a sparse DMatrix
     * @param response name of the response column
     * @param weight name of the weight column
     * @param fold name of the fold assignment column
     * @return DMatrix
     * @throws XGBoostError
     */
    public static DMatrix convertChunksToDMatrix(Key<DataInfo> dataInfoKey,
                                                 Chunk[] chunks,
                                                 int response,
                                                 int weight,
                                                 int fold,
                                                 boolean sparse) throws XGBoostError {
        int nRows = chunks[0]._len;

        DMatrix trainMat;
        DataInfo di = dataInfoKey.get();

        float[] resp = malloc4f(nRows);
        float[] weights = null;
        if(-1 != weight) {
            weights = malloc4f(nRows);
        }
        try {
            if (sparse) {
                Log.debug("Treating matrix as sparse.");
                // 1 0 2 0
                // 4 0 0 3
                // 3 1 2 0
                boolean csc = false; //di._cats == 0;

                // truly sparse matrix - no categoricals
                // collect all nonzeros column by column (in parallel), then stitch together into final data structures
                if (csc) {
                    trainMat = csc(chunks, weight, nRows, di, resp, weights);
                } else {
                    trainMat = csr(chunks, weight, response, nRows, di, resp, weights);
                }
            } else {
                trainMat = dense(chunks, weight, di, response, resp, weights);
            }
        } catch (NegativeArraySizeException e) {
            throw new IllegalArgumentException(technote(11,
                "Data is too large to fit into the 32-bit Java float[] array that needs to be passed to the XGBoost C++ backend. Use H2O GBM instead."));
        }

        int len = (int) trainMat.rowNum();
        resp = Arrays.copyOf(resp, len);
        trainMat.setLabel(resp);
        if (weight!=-1){
            weights = Arrays.copyOf(weights, len);
            trainMat.setWeight(weights);
        }
//    trainMat.setGroup(null); //fold //FIXME - only needed if CV is internally done in XGBoost
        return trainMat;
    }

    /****************************************************************************************************************
     ************************************** DMatrix creation for dense matrices *************************************
     ****************************************************************************************************************/

    private static DMatrix dense(Chunk[] chunks, int weight, DataInfo di, int respIdx, float[] resp, float[] weights) throws XGBoostError {
        Log.debug("Treating matrix as dense.");
        BigDenseMatrix data = null;
        try {
            data = allocateDenseMatrix(chunks[0].len(), di);
            long actualRows = denseChunk(data, chunks, weight, respIdx, di, resp, weights);
            assert actualRows == data.nrow;
            return new DMatrix(data, Float.NaN);
        } finally {
            if (data != null) {
                data.dispose();
            }
        }
    }

    private static final int ARRAY_MAX = Integer.MAX_VALUE - 10;

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

    private static long denseChunk(BigDenseMatrix data, Chunk[] chunks, int weight, int respIdx, DataInfo di, float[] resp, float[] weights) {
        long idx = 0;
        long actualRows = 0;
        int rwRow = 0;
        for (int i = 0; i < chunks[0]._len; i++) {
            if (weight != -1 && chunks[weight].atd(i) == 0) continue;

            idx = writeDenseRow(di, chunks, i, data, idx);
            actualRows++;

            rwRow = setResponseAndWeight(chunks, respIdx, weight, resp, weights, rwRow, i);
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

    private static DMatrix csr(Frame f, int[] chunksIds, Vec.Reader[] vecs, Vec.Reader w, Vec.Reader respReader, // for setupLocal
                               int nRows, DataInfo di, float[] resp, float[] weights)
        throws XGBoostError {
        return csr(null, -1, -1, f, chunksIds, vecs, w, respReader, nRows, di, resp, weights);
    }

    private static DMatrix csr(Chunk[] chunks, int weight, int respIdx, // for MR task
                               int nRows, DataInfo di, float[] resp, float[] weights) throws XGBoostError {
        return csr(chunks, weight, respIdx, null, null, null, null, null, nRows, di, resp, weights);
    }

    private static DMatrix csr(Chunk[] chunks, int weight, int respIdx, // for MR task
                               Frame f, int[] chunksIds, Vec.Reader[] vecs, Vec.Reader w, Vec.Reader respReader, // for setupLocal
                               int nRows, DataInfo di, float[] resp, float[] weights)
        throws XGBoostError {
        DMatrix trainMat;
        int actualRows = 0;
        // CSR:
//    long[] rowHeaders = new long[] {0,      2,      4,         7}; //offsets
//    float[] data = new float[]     {1f,2f,  4f,3f,  3f,1f,2f};     //non-zeros across each row
//    int[] colIndex = new int[]     {0, 2,   0, 3,   0, 1, 2};      //col index for each non-zero

        long[][] rowHeaders;
        float[][] data;
        int[][] colIndex;
        final SparseMatrixDimensions sparseMatrixDimensions;
        if(null != chunks) {
            sparseMatrixDimensions = calculateCSRMatrixDimensions(chunks, di, weight);
            SparseMatrix sparseMatrix = allocateCSRMatrix(sparseMatrixDimensions);
            data = sparseMatrix._sparseData;
            rowHeaders = sparseMatrix._rowIndices;
            colIndex = sparseMatrix._colIndices;

            actualRows = initializeFromChunks(
                    chunks, weight,
                    di, actualRows, rowHeaders, data, colIndex,
                    respIdx, resp, weights);
        } else {
            sparseMatrixDimensions = calculateCSRMatrixDimensions(f, chunksIds, vecs, w, di);
            SparseMatrix sparseMatrix = allocateCSRMatrix(sparseMatrixDimensions);
            data = sparseMatrix._sparseData;
            rowHeaders = sparseMatrix._rowIndices;
            colIndex = sparseMatrix._colIndices;
            actualRows = initalizeFromChunkIds(
                    f, chunksIds, vecs, w,
                    di, actualRows, rowHeaders, data, colIndex,
                    respReader, resp, weights);
        }


        long size = sparseMatrixDimensions._nonZeroElementsCount;
        int rowHeadersSize = (int) sparseMatrixDimensions._rowIndicesCount;

        trainMat = new DMatrix(rowHeaders, colIndex, data, DMatrix.SparseType.CSR, di.fullN(), rowHeadersSize, size);
        assert trainMat.rowNum() == actualRows;
        return trainMat;
    }

    private static int initalizeFromChunkIds(Frame f, int[] chunks, Vec.Reader[] vecs, Vec.Reader w, DataInfo di, int actualRows,
                                             long[][] rowHeaders, float[][] data, int[][] colIndex,
                                             Vec.Reader respVec, float[] resp, float[] weights) {
        // CSR:
        //    long[] rowHeaders = new long[] {0,      2,      4,         7}; //offsets
        //    float[] data = new float[]     {1f,2f,  4f,3f,  3f,1f,2f};     //non-zeros across each row
        //    int[] colIndex = new int[]     {0, 2,   0, 3,   0, 1, 2};      //col index for each non-zero

        // extract predictors
        int nonZeroCount = 0;
        int currentRow = 0;
        int currentCol = 0;
        int rwRow = 0;

        for (Integer chunk : chunks) {
            for(long i = f.anyVec().espc()[chunk]; i < f.anyVec().espc()[chunk+1]; i++) {
                if (w != null && w.at(i) == 0) continue;

                final int startNonZeroCount = nonZeroCount;
                // enlarge final data arrays by 2x if needed

                for (int j = 0; j < di._cats; ++j) {
                    data[currentRow][currentCol] = 1; //one-hot encoding
                    if (vecs[j].isNA(i)) {
                        colIndex[currentRow][currentCol++] = di.getCategoricalId(j, Float.NaN);
                    } else {
                        colIndex[currentRow][currentCol++] = di.getCategoricalId(j, vecs[j].at8(i));
                    }
                    nonZeroCount++;
                }

                for (int j = 0; j < di._nums; ++j) {
                    float val = (float) vecs[di._cats + j].at(i);
                    if (val != 0) {
                        data[currentRow][currentCol] = val;
                        colIndex[currentRow][currentCol++] = di._catOffsets[di._catOffsets.length - 1] + j;
                        nonZeroCount++;
                    }
                }

                rowHeaders[0][++actualRows] = nonZeroCount;

                rwRow = setResponseAndWeight(w, resp, weights, respVec, rwRow, i);
            }
        }

        return actualRows;
    }

    private static int initializeFromChunks(Chunk[] chunks, int weight, DataInfo di, int actualRows, long[][] rowHeaders, float[][] data, int[][] colIndex, int respIdx, float[] resp, float[] weights) {
        int nonZeroCount = 0;
        int currentRow = 0;
        int currentCol = 0;
        int rwRow = 0;

        for (int i = 0; i < chunks[0].len(); i++) {
            if (weight != -1 && chunks[weight].atd(i) == 0) continue;

            for (int j = 0; j < di._cats; ++j) {
                data[currentRow][currentCol] = 1; //one-hot encoding
                if (chunks[j].isNA(i)) {
                    colIndex[currentRow][currentCol++] = di.getCategoricalId(j, Float.NaN);
                } else {
                    colIndex[currentRow][currentCol++] = di.getCategoricalId(j, chunks[j].at8(i));
                }
                nonZeroCount++;
            }
            for (int j = 0; j < di._nums; ++j) {
                float val = (float) chunks[di._cats + j].atd(i);
                if (val != 0) {
                    data[currentRow][currentCol] = val;
                    colIndex[currentRow][currentCol++] = di._catOffsets[di._catOffsets.length - 1] + j;
                    nonZeroCount++;
                }
            }

            rowHeaders[0][++actualRows] = nonZeroCount;

            rwRow = setResponseAndWeight(chunks, respIdx, weight, resp, weights, rwRow, i);
        }
        return actualRows;
    }

    static class SparseItem {
        int pos;
        double val;
    }

    /****************************************************************************************************************
     *********************************** DMatrix creation for sparse (CSC) matrices *********************************
     ****************************************************************************************************************/

    private static DMatrix csc(Chunk[] chunks, int weight,
                               long nRows, DataInfo di,
                               float[] resp, float[] weights) throws XGBoostError {
        return csc(chunks, weight, null, null, null, null, nRows, di, resp, weights);
    }

    private static DMatrix csc(Frame f, int[] chunksIds, Vec.Reader w, Vec.Reader respReader,
                               long nRows, DataInfo di,
                               float[] resp, float[] weights) throws XGBoostError {
        return csc(null, -1, f, chunksIds, w, respReader, nRows, di, resp, weights);
    }

    private static DMatrix csc(Chunk[] chunks, int weight, // for MR tasks
                               Frame f, int[] chunksIds, Vec.Reader w, Vec.Reader respReader, // for setupLocal computation
                               long nRows, DataInfo di,
                               float[] resp, float[] weights) throws XGBoostError {
        DMatrix trainMat;

        // CSC:
        //    long[] colHeaders = new long[] {0,        3,  4,     6,    7}; //offsets
        //    float[] data = new float[]     {1f,4f,3f, 1f, 2f,2f, 3f};      //non-zeros down each column
        //    int[] rowIndex = new int[]     {0,1,2,    2,  0, 2,  1};       //row index for each non-zero

        int nCols = di._nums;

        List<SparseItem>[] col = new List[nCols]; //TODO: use more efficient storage (no GC)
        // allocate
        for (int i=0;i<nCols;++i) {
            col[i] = new ArrayList<>((int)Math.min(nRows, 10000));
        }

        // collect non-zeros
        int nzCount = 0;
        if(null != chunks) {
            nzCount = getNzCount(chunks, weight, nCols, col, nzCount);
        } else {
            nzCount = getNzCount(f, chunksIds, w, nCols, col, nzCount);
        }

        int currentRow = 0;
        int currentCol = 0;
        int nz = 0;
        long[][] colHeaders = new long[1][nCols + 1];
        float[][] data = new float[getDataRows(chunks, f, chunksIds, di.fullN())][nzCount];
        int[][] rowIndex = new int[1][nzCount];
        int rwRow = 0;
        // fill data for DMatrix
        for (int i=0;i<nCols;++i) { //TODO: parallelize over columns
            List<SparseItem> sparseCol = col[i];
            colHeaders[0][i] = nz;

            enlargeTables(data, rowIndex, sparseCol.size(), currentRow, currentCol);

            for (int j=0;j<sparseCol.size();++j) {
                if(currentCol == ARRAY_MAX) {
                    currentCol = 0;
                    currentRow++;
                }

                SparseItem si = sparseCol.get(j);
                rowIndex[currentRow][currentCol] = si.pos;
                data[currentRow][currentCol] = (float)si.val;
                assert(si.val != 0);
                assert(!Double.isNaN(si.val));
//                assert(weight == -1 || chunks[weight].atd((int)(si.pos - chunks[weight].start())) != 0);
                nz++;
                currentCol++;

                // Do only once
                if(0 == i) {
                    rwRow = setResponseAndWeight(w, resp, weights, respReader, rwRow, j);
                }
            }
        }
        colHeaders[0][nCols] = nz;
        data[data.length - 1] = Arrays.copyOf(data[data.length - 1], nz % ARRAY_MAX);
        rowIndex[rowIndex.length - 1] = Arrays.copyOf(rowIndex[rowIndex.length - 1], nz % ARRAY_MAX);
        int actualRows = countUnique(rowIndex);

        trainMat = new DMatrix(colHeaders, rowIndex, data, DMatrix.SparseType.CSC, actualRows, di.fullN(), nz);
        assert trainMat.rowNum() == actualRows;
        assert trainMat.rowNum() == rwRow;
        return trainMat;
    }

    private static int countUnique(int[][] array) {
        if (array.length == 0) {
            return 0;
        }

        BitSet values = new BitSet(ARRAY_MAX);

        int count = 1;
        for (int i = 0; i < array.length; i++) {
            for (int j = 0; j < array[i].length - 1; j++) {
                if (!values.get(array[i][j])) {
                    count++;
                    values.set(array[i][j]);
                }
            }
        }
        return count;
    }

    // Assumes both matrices are getting filled at the same rate and will require the same amount of space
    private static void enlargeTables(float[][] data, int[][] rowIndex, int cols, int currentRow, int currentCol) {
        while (data[currentRow].length < currentCol + cols) {
            if(data[currentRow].length == ARRAY_MAX) {
                currentCol = 0;
                cols -= (data[currentRow].length - currentCol);
                currentRow++;
                data[currentRow] = malloc4f(ALLOCATED_ARRAY_LEN);
                rowIndex[currentRow] = malloc4(ALLOCATED_ARRAY_LEN);
            } else {
                int newLen = (int) Math.min((long) data[currentRow].length << 1L, (long) ARRAY_MAX);
                data[currentRow] = Arrays.copyOf(data[currentRow], newLen);
                rowIndex[currentRow] = Arrays.copyOf(rowIndex[currentRow], newLen);
            }
        }
    }

    /**
     * Creates a {@link SparseMatrix} object with pre-instantiated backing arrays for row-oriented compression schema (CSR).
     * All backing arrays are allocated using MemoryManager.
     *
     * @param sparseMatrixDimensions Dimensions of a sparse matrix
     * @return An instance of {@link SparseMatrix} with pre-allocated backing arrays.
     */
    private static SparseMatrix allocateCSRMatrix(SparseMatrixDimensions sparseMatrixDimensions) {
        // Number of rows in non-zero elements matrix
        final int dataRowsNumber = (int) (sparseMatrixDimensions._nonZeroElementsCount / ARRAY_MAX);
        final int dataLastRowSize = (int)(sparseMatrixDimensions._nonZeroElementsCount % ARRAY_MAX);
        //Number of rows in matrix with row indices
        final int rowIndicesRowsNumber = (int)(sparseMatrixDimensions._rowIndicesCount / ARRAY_MAX);
        final int rowIndicesLastRowSize = (int)(sparseMatrixDimensions._rowIndicesCount % ARRAY_MAX);
        // Number of rows in matrix with column indices of sparse matrix non-zero elements
        final int colIndicesRowsNumber = (int)(sparseMatrixDimensions._nonZeroElementsCount / ARRAY_MAX);
        final int colIndicesLastRowSize = (int)(sparseMatrixDimensions._nonZeroElementsCount % ARRAY_MAX);

        // Sparse matrix elements (non-zero elements)
        float[][] sparseData = new float[dataLastRowSize == 0 ? dataRowsNumber : dataRowsNumber + 1][];
        for (int sparseDataRow = 0; sparseDataRow < sparseData.length - 1; sparseDataRow++) {
            sparseData[sparseDataRow] = malloc4f(ARRAY_MAX);
        }
        if (dataLastRowSize > 0) {
            sparseData[sparseData.length - 1] = malloc4f(dataLastRowSize);
        }
        // Row indices
        long[][] rowIndices = new long[rowIndicesLastRowSize == 0 ? rowIndicesRowsNumber : rowIndicesRowsNumber + 1][];
        for (int rowIndicesRow = 0; rowIndicesRow < rowIndices.length - 1; rowIndicesRow++) {
            rowIndices[rowIndicesRow] = malloc8(ARRAY_MAX);
        }
        if (rowIndicesLastRowSize > 0) {
            rowIndices[rowIndices.length - 1] = malloc8(rowIndicesLastRowSize);
        }

        // Column indices
        int[][] colIndices = new int[colIndicesLastRowSize == 0 ? colIndicesRowsNumber : colIndicesRowsNumber + 1][];
        for (int colIndicesRow = 0; colIndicesRow < colIndices.length - 1; colIndicesRow++) {
            colIndices[colIndicesRow] = malloc4(ARRAY_MAX);
        }
        if (colIndicesLastRowSize > 0) {
            colIndices[colIndices.length - 1] = malloc4(colIndicesLastRowSize);
        }

        // Wrap backing arrays into a SparseMatrix object and return them
        return new SparseMatrix(sparseData, rowIndices, colIndices);
    }

    private static SparseMatrixDimensions calculateCSRMatrixDimensions(Chunk[] chunks, DataInfo di, int weightColIndex){

        long nonZeroElementsCount = 0;
        long rowIndicesCount = 0;

        for (int i = 0; i < chunks[0].len(); i++) {
            // Rows with zero weights are going to be ignored
            if (weightColIndex != -1 && chunks[weightColIndex].atd(i) == 0) continue;


            nonZeroElementsCount += di._cats;

            for (int j = 0; j < di._nums; ++j) {
                float val = (float) chunks[di._cats + j].atd(i);
                if (val != 0) {
                    nonZeroElementsCount++;
                }
            }
            rowIndicesCount++;

        }

        return new SparseMatrixDimensions(nonZeroElementsCount, ++rowIndicesCount);
    }

    private static SparseMatrixDimensions calculateCSRMatrixDimensions(Frame f, int[] chunks, Vec.Reader[] vecs, Vec.Reader w, DataInfo di) {
        long nonZeroElementsCount = 0;
        long rowIndicesCount = 0;

        for (Integer chunk : chunks) {
            for (long i = f.anyVec().espc()[chunk]; i < f.anyVec().espc()[chunk + 1]; i++) {
                if (w != null && w.at(i) == 0) continue;

                nonZeroElementsCount+= di._cats;

                for (int j = 0; j < di._nums; ++j) {
                    float val = (float) vecs[di._cats + j].at(i);
                    if (val != 0) {
                        nonZeroElementsCount++;
                    }
                }
                rowIndicesCount++;
            }
        }

        return new SparseMatrixDimensions(nonZeroElementsCount, ++rowIndicesCount);
    }


    /**
     * Dimensions of a Sparse Matrix
     */
    private static final class SparseMatrixDimensions{
        private final long _nonZeroElementsCount;
        private final long _rowIndicesCount;

        /**
         * Constructs an instance of {@link SparseMatrixDimensions}
         *
         * @param nonZeroElementsCount Number of non-zero elements (number of elements in sparse matrix). Also
         *                             number of column indices.
         * @param rowIndicesCount      Number of indices of elements rows begin with
         */
        public SparseMatrixDimensions(long nonZeroElementsCount, long rowIndicesCount) {
            _nonZeroElementsCount = nonZeroElementsCount;
            _rowIndicesCount = rowIndicesCount;
        }
    }

    /**
     * Sparse Matrix representation for XGBoost
     */
    private static final class SparseMatrix {
        private final float[][] _sparseData;
        private final long[][] _rowIndices;
        private final int[][] _colIndices;

        /**
         * Constructs a {@link SparseMatrix} instance
         *
         * @param sparseData Non-zero data of a sparse matrix
         * @param rowIndices Indices to elements in sparseData rows begin with
         * @param colIndices Column indices of elements in sparseData
         */
        public SparseMatrix(final float[][] sparseData, final long[][] rowIndices, final int[][] colIndices) {
            _sparseData = sparseData;
            _rowIndices = rowIndices;
            _colIndices = colIndices;
        }
    }

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

}
