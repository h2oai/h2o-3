package hex.tree.xgboost.matrix;

import hex.DataInfo;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.H2O;
import water.LocalMR;
import water.MrFun;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import static hex.tree.xgboost.matrix.MatrixFactoryUtils.setResponseAndWeight;
import static water.MemoryManager.*;
import static water.MemoryManager.malloc4;

/*
- truly sparse matrix - no categoricals
- collect all nonzeros column by column (in parallel), then stitch together into final data structures
 */
public class SparseMatrixFactory {

    public static DMatrix csr(
        Frame f, int[] chunksIds, Vec weightsVec, Vec responseVec, // for setupLocal
        DataInfo di, float[] resp, float[] weights
    ) throws XGBoostError {

        SparseMatrixDimensions sparseMatrixDimensions = calculateCSRMatrixDimensions(f, chunksIds, weightsVec, di);
        SparseMatrix sparseMatrix = allocateCSRMatrix(sparseMatrixDimensions);

        int actualRows = initializeFromChunkIds(
            f, chunksIds, weightsVec,
            di, sparseMatrix, sparseMatrixDimensions,
            responseVec, resp, weights);

        return toDMatrix(sparseMatrix, sparseMatrixDimensions, actualRows, di);
    }

    public static DMatrix csr(
        Chunk[] chunks, int weight, int respIdx, // for MR task
        DataInfo di, float[] resp, float[] weights
    ) throws XGBoostError {

        SparseMatrixDimensions sparseMatrixDimensions = calculateCSRMatrixDimensions(chunks, di, weight);
        SparseMatrix sparseMatrix = allocateCSRMatrix(sparseMatrixDimensions);

        int actualRows = initializeFromChunks(
            chunks, weight,
            di, sparseMatrix._rowHeaders, sparseMatrix._sparseData, sparseMatrix._colIndices,
            respIdx, resp, weights);
        return toDMatrix(sparseMatrix, sparseMatrixDimensions, actualRows, di);
    }

    private static DMatrix toDMatrix(SparseMatrix sm, SparseMatrixDimensions smd, int actualRows, DataInfo di) throws XGBoostError {
        DMatrix trainMat = new DMatrix(sm._rowHeaders, sm._colIndices, sm._sparseData,
            DMatrix.SparseType.CSR, di.fullN(), actualRows + 1,
            smd._nonZeroElementsCount);
        assert trainMat.rowNum() == actualRows;
        return trainMat;
    }
    
    private static class NestedArrayPointer {
        int _row, _col;

        public NestedArrayPointer() {
        }

        public NestedArrayPointer(long pos) {
            this._row = (int) pos / SparseMatrix.MAX_DIM;
            this._col = (int) pos % SparseMatrix.MAX_DIM;
        }

        void increment() {
            _col++;
            if(_col == SparseMatrix.MAX_DIM){
                _col = 0;
                _row++;
            }
        }

        void set(long[][] dest, long val) {
            dest[_row][_col] = val;
        }

        void set(float[][] dest, float val) {
            dest[_row][_col] = val;
        }

        void set(int[][] dest, int val) {
            dest[_row][_col] = val;
        }

        void setAndIncrement(long[][] dest, long val) {
            set(dest, val);
            increment();
        }

    }

    public static int initializeFromChunkIds(
        Frame f, int[] chunks, Vec w, DataInfo di,
        SparseMatrix matrix, SparseMatrixDimensions dimensions,
        Vec respVec, float[] resp, float[] weights
    ) {
        InitializeCSRMatrixFromChunkIdsMrFun fun = new InitializeCSRMatrixFromChunkIdsMrFun(
            f, chunks, w, di, matrix, dimensions, respVec, resp, weights
        );
        H2O.submitTask(new LocalMR(fun, chunks.length)).join();

        return ArrayUtils.sum(fun._actualRows);
    }

    private static class InitializeCSRMatrixFromChunkIdsMrFun extends MrFun<CalculateCSRMatrixDimensionsMrFun> {

        Frame _f;
        int[] _chunks;
        Vec _w;
        DataInfo _di;
        SparseMatrix _matrix;
        SparseMatrixDimensions _dims;
        Vec _respVec;
        float[] _resp; 
        float[] _weights;
        
        // OUT
        int[] _actualRows;
        
        InitializeCSRMatrixFromChunkIdsMrFun(
            Frame f, int[] chunks, Vec w, DataInfo di,
            SparseMatrix matrix, SparseMatrixDimensions dimensions,
            Vec respVec, float[] resp, float[] weights
        ) {
            _actualRows = new int[chunks.length];
            
            _f = f;
            _chunks = chunks;
            _w = w;
            _di = di;
            _matrix = matrix;
            _dims = dimensions;
            _respVec = respVec;
            _resp = resp;
            _weights = weights;
        }

        @Override
        protected void map(int chunkIdx) {
            int chunk = _chunks[chunkIdx];
            long nonZeroCount = _dims._precedingNonZeroElementsCounts[chunkIdx];
            int rwRow = _dims._precedingRowCounts[chunkIdx];
            NestedArrayPointer rowHeaderPointer = new NestedArrayPointer(rwRow);
            NestedArrayPointer dataPointer = new NestedArrayPointer(nonZeroCount);

            Chunk weightChunk = _w != null ? _w.chunkForChunkIdx(chunk) : null;
            Chunk respChunk = _respVec.chunkForChunkIdx(chunk);
            Chunk[] featChunks = new Chunk[_f.vecs().length];
            for (int i = 0; i < featChunks.length; i++) {
                featChunks[i] = _f.vecs()[i].chunkForChunkIdx(chunk);
            }
            for(int i = 0; i < respChunk._len; i++) {
                if (weightChunk != null && weightChunk.atd(i) == 0) continue;
                rowHeaderPointer.setAndIncrement(_matrix._rowHeaders, nonZeroCount);
                _actualRows[chunkIdx]++;
                for (int j = 0; j < _di._cats; ++j) {
                    dataPointer.set(_matrix._sparseData, 1);
                    if (featChunks[j].isNA(i)) {
                        dataPointer.set(_matrix._colIndices, _di.getCategoricalId(j, Float.NaN));
                    } else {
                        dataPointer.set(_matrix._colIndices, _di.getCategoricalId(j, featChunks[j].at8(i)));
                    }
                    dataPointer.increment();
                    nonZeroCount++;
                }
                for (int j = 0; j < _di._nums; ++j) {
                    float val = (float) featChunks[_di._cats + j].atd(i);
                    if (val != 0) {
                        dataPointer.set(_matrix._sparseData, val);
                        dataPointer.set(_matrix._colIndices, _di._catOffsets[_di._catOffsets.length - 1] + j);
                        dataPointer.increment();
                        nonZeroCount++;
                    }
                }
                rwRow = setResponseAndWeight(weightChunk, respChunk, _resp, _weights, rwRow, i);
            }
            rowHeaderPointer.set(_matrix._rowHeaders, nonZeroCount);
        }
    }

    private static int initializeFromChunks(Chunk[] chunks, int weight, DataInfo di, long[][] rowHeaders, float[][] data, int[][] colIndex, int respIdx, float[] resp, float[] weights) {
        int actualRows = 0;
        int nonZeroCount = 0;
        int rwRow = 0;

        NestedArrayPointer rowHeaderPointer = new NestedArrayPointer();
        NestedArrayPointer dataPointer = new NestedArrayPointer();

        for (int i = 0; i < chunks[0].len(); i++) {
            if (weight != -1 && chunks[weight].atd(i) == 0) continue;
            actualRows++;
            rowHeaderPointer.setAndIncrement(rowHeaders, nonZeroCount);
            for (int j = 0; j < di._cats; ++j) {
                dataPointer.set(data, 1); //one-hot encoding
                if (chunks[j].isNA(i)) {
                    dataPointer.set(colIndex, di.getCategoricalId(j, Float.NaN));
                } else {
                    dataPointer.set(colIndex, di.getCategoricalId(j, chunks[j].at8(i)));
                }
                dataPointer.increment();
                nonZeroCount++;
            }
            for (int j = 0; j < di._nums; ++j) {
                float val = (float) chunks[di._cats + j].atd(i);
                if (val != 0) {
                    dataPointer.set(data, val);
                    dataPointer.set(colIndex, di._catOffsets[di._catOffsets.length - 1] + j);
                    dataPointer.increment();
                    nonZeroCount++;
                }
            }
            rwRow = setResponseAndWeight(chunks, respIdx, weight, resp, weights, rwRow, i);
        }
        rowHeaderPointer.set(rowHeaders, nonZeroCount);
        return actualRows;
    }

    /**
     * Creates a {@link SparseMatrix} object with pre-instantiated backing arrays for row-oriented compression schema (CSR).
     * All backing arrays are allocated using MemoryManager.
     *
     * @param sparseMatrixDimensions Dimensions of a sparse matrix
     * @return An instance of {@link SparseMatrix} with pre-allocated backing arrays.
     */
    public static SparseMatrix allocateCSRMatrix(SparseMatrixDimensions sparseMatrixDimensions) {
        // Number of rows in non-zero elements matrix
        final int dataRowsNumber = (int) (sparseMatrixDimensions._nonZeroElementsCount / SparseMatrix.MAX_DIM);
        final int dataLastRowSize = (int)(sparseMatrixDimensions._nonZeroElementsCount % SparseMatrix.MAX_DIM);
        //Number of rows in matrix with row indices
        final int rowIndicesRowsNumber = (int)(sparseMatrixDimensions._rowHeadersCount / SparseMatrix.MAX_DIM);
        final int rowIndicesLastRowSize = (int)(sparseMatrixDimensions._rowHeadersCount % SparseMatrix.MAX_DIM);
        // Number of rows in matrix with column indices of sparse matrix non-zero elements
        // There is one column index per each non-zero element, no need to recalculate.
        final int colIndicesRowsNumber = dataRowsNumber;
        final int colIndicesLastRowSize = dataLastRowSize;

        // Sparse matrix elements (non-zero elements)
        float[][] sparseData = new float[dataLastRowSize == 0 ? dataRowsNumber : dataRowsNumber + 1][];
        int iterationLimit = dataLastRowSize == 0 ? sparseData.length : sparseData.length - 1;
        for (int sparseDataRow = 0; sparseDataRow < iterationLimit; sparseDataRow++) {
            sparseData[sparseDataRow] = malloc4f(SparseMatrix.MAX_DIM);
        }
        if (dataLastRowSize > 0) {
            sparseData[sparseData.length - 1] = malloc4f(dataLastRowSize);
        }
        // Row indices
        long[][] rowIndices = new long[rowIndicesLastRowSize == 0 ? rowIndicesRowsNumber : rowIndicesRowsNumber + 1][];
        iterationLimit = rowIndicesLastRowSize == 0 ? rowIndices.length : rowIndices.length - 1;
        for (int rowIndicesRow = 0; rowIndicesRow < iterationLimit; rowIndicesRow++) {
            rowIndices[rowIndicesRow] = malloc8(SparseMatrix.MAX_DIM);
        }
        if (rowIndicesLastRowSize > 0) {
            rowIndices[rowIndices.length - 1] = malloc8(rowIndicesLastRowSize);
        }

        // Column indices
        int[][] colIndices = new int[colIndicesLastRowSize == 0 ? colIndicesRowsNumber : colIndicesRowsNumber + 1][];
        iterationLimit = colIndicesLastRowSize == 0 ? colIndices.length : colIndices.length - 1;
        for (int colIndicesRow = 0; colIndicesRow < iterationLimit; colIndicesRow++) {
            colIndices[colIndicesRow] = malloc4(SparseMatrix.MAX_DIM);
        }
        if (colIndicesLastRowSize > 0) {
            colIndices[colIndices.length - 1] = malloc4(colIndicesLastRowSize);
        }

        // Wrap backing arrays into a SparseMatrix object and return them
        return new SparseMatrix(sparseData, rowIndices, colIndices);
    }

    protected static SparseMatrixDimensions calculateCSRMatrixDimensions(Chunk[] chunks, DataInfo di, int weightColIndex){

        int[] nonZeroElementsCounts = new int[1];
        int[] rowIndicesCounts = new int[1];

        for (int i = 0; i < chunks[0].len(); i++) {
            // Rows with zero weights are going to be ignored
            if (weightColIndex != -1 && chunks[weightColIndex].atd(i) == 0) continue;
            rowIndicesCounts[0]++;

            nonZeroElementsCounts[0] += di._cats;

            for (int j = 0; j < di._nums; ++j) {
                double val = chunks[di._cats + j].atd(i);
                if (val != 0) {
                    nonZeroElementsCounts[0]++;
                }
            }
        }

        return new SparseMatrixDimensions(nonZeroElementsCounts, rowIndicesCounts);
    }

    public static SparseMatrixDimensions calculateCSRMatrixDimensions(Frame f, int[] chunkIds, Vec w, DataInfo di) {
        CalculateCSRMatrixDimensionsMrFun fun = new CalculateCSRMatrixDimensionsMrFun(f, di, w, chunkIds);
        H2O.submitTask(new LocalMR(fun, chunkIds.length)).join();

        return new SparseMatrixDimensions(fun._nonZeroElementsCounts, fun._rowIndicesCounts);
    }

    private static class CalculateCSRMatrixDimensionsMrFun extends MrFun<CalculateCSRMatrixDimensionsMrFun> {
        private Frame _f;
        private DataInfo _di;
        private Vec _w;
        private int[] _chunkIds;

        // OUT
        private int[] _rowIndicesCounts;
        private int[] _nonZeroElementsCounts;

        CalculateCSRMatrixDimensionsMrFun(Frame f, DataInfo di, Vec w, int[] chunkIds) {
            _f = f;
            _di = di;
            _w = w;
            _chunkIds = chunkIds;
            _rowIndicesCounts = new int[chunkIds.length];
            _nonZeroElementsCounts = new int[chunkIds.length];
        }

        @Override
        protected void map(int i) {
            final int cidx = _chunkIds[i];

            int rowIndicesCount = 0;
            int nonZeroElementsCount = 0;

            if (_di._nums == 0) {
                if (_w == null) {
                    // no weights and only categoricals => sizing is trivial
                    rowIndicesCount = _f.anyVec().chunkForChunkIdx(cidx)._len;
                    nonZeroElementsCount = rowIndicesCount * _di._cats;
                } else {
                    Chunk ws = _w.chunkForChunkIdx(cidx);
                    int nzWeights = 0;
                    for (int r = 0; r < ws._len; r++)
                        if (ws.atd(r) != 0) {
                            nzWeights++;
                        }
                    rowIndicesCount += nzWeights;
                    nonZeroElementsCount += nzWeights * _di._cats;
                }
            } else {
                Chunk[] cs = new Chunk[_di._nums];
                for (int c = 0; c < cs.length; c++) {
                    cs[c] = _f.vec(_di._cats + c).chunkForChunkIdx(cidx);
                }
                Chunk ws = _w != null ? _w.chunkForChunkIdx(cidx) : null;
                for (int r = 0; r < cs[0]._len; r++) {
                    if (ws != null && ws.atd(r) == 0) continue;
                    rowIndicesCount++;
                    nonZeroElementsCount += _di._cats;
                    for (int j = 0; j < _di._nums; j++) {
                        if (cs[j].atd(r) != 0) {
                            nonZeroElementsCount++;
                        }
                    }
                }
            }
            _rowIndicesCounts[i] = rowIndicesCount;
            _nonZeroElementsCounts[i] = nonZeroElementsCount;
        }
    }

}
