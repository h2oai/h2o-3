package hex.tree.xgboost.matrix;

import hex.DataInfo;
import ai.h2o.xgboost4j.java.DMatrix;
import ai.h2o.xgboost4j.java.XGBoostError;
import water.H2O;
import water.LocalMR;
import water.MrFun;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Objects;

import static hex.tree.xgboost.matrix.MatrixFactoryUtils.setResponseAndWeightAndOffset;
import static hex.tree.xgboost.matrix.MatrixFactoryUtils.setResponseWeightAndOffset;
import static water.MemoryManager.*;

/*
- truly sparse matrix - no categoricals
- collect all nonzeros column by column (in parallel), then stitch together into final data structures
 */
public class SparseMatrixFactory {

    public static MatrixLoader.DMatrixProvider csr(
        Frame frame, int[] chunksIds, Vec weightsVec, Vec offsetsVec, Vec responseVec, // for setupLocal
        DataInfo di, float[] resp, float[] weights, float[] offsets
    ) {

        SparseMatrixDimensions sparseMatrixDimensions = calculateCSRMatrixDimensions(frame, chunksIds, weightsVec, di);
        SparseMatrix sparseMatrix = allocateCSRMatrix(sparseMatrixDimensions);

        int actualRows = initializeFromChunkIds(
            frame, chunksIds, weightsVec, offsetsVec,
            di, sparseMatrix, sparseMatrixDimensions,
            responseVec, resp, weights, offsets);

        return toDMatrix(sparseMatrix, sparseMatrixDimensions, actualRows, di.fullN(), resp, weights, offsets);
    }

    public static DMatrix csr(
        Chunk[] chunks, int weight, int respIdx, int offsetIdx, // for MR task
        DataInfo di, float[] resp, float[] weights, float[] offsets
    ) throws XGBoostError {

        SparseMatrixDimensions sparseMatrixDimensions = calculateCSRMatrixDimensions(chunks, di, weight);
        SparseMatrix sparseMatrix = allocateCSRMatrix(sparseMatrixDimensions);

        int actualRows = initializeFromChunks(
            chunks, weight,
            di, sparseMatrix._rowHeaders, sparseMatrix._sparseData, sparseMatrix._colIndices,
            respIdx, resp, weights, offsetIdx, offsets);
        return toDMatrix(sparseMatrix, sparseMatrixDimensions, actualRows, di.fullN(), resp, weights, offsets).get();
    }
    
    public static class SparseDMatrixProvider extends MatrixLoader.DMatrixProvider {

        private long[][] rowHeaders;
        private int[][] colIndices;
        private float[][] sparseData;
        private DMatrix.SparseType csr;
        private int shape;
        private long nonZeroElementsCount;

        public SparseDMatrixProvider(
            long[][] rowHeaders,
            int[][] colIndices,
            float[][] sparseData,
            DMatrix.SparseType csr,
            int shape,
            long nonZeroElementsCount,
            int actualRows,
            float[] response,
            float[] weights,
            float[] offsets
        ) {
            super(actualRows, response, weights, offsets);
            this.rowHeaders = rowHeaders;
            this.colIndices = colIndices;
            this.sparseData = sparseData;
            this.csr = csr;
            this.shape = shape;
            this.nonZeroElementsCount = nonZeroElementsCount;
        }

        @Override
        public DMatrix makeDMatrix() throws XGBoostError {
            return new DMatrix(rowHeaders, colIndices, sparseData, csr, shape, (int) actualRows + 1, nonZeroElementsCount);
        }

        @Override
        public void print(int nrow) {
            NestedArrayPointer r = new NestedArrayPointer();
            NestedArrayPointer d = new NestedArrayPointer();
            long elemIndex = 0;
            r.increment();
            for (int i = 0; i < (nrow > 0 ? nrow : actualRows); i++) {
                System.out.print(i + ":\t");
                long rowEnd = r.get(rowHeaders);
                r.increment();
                for (; elemIndex < rowEnd; elemIndex++) {
                    System.out.print(d.get(colIndices) + ":" + d.get(sparseData) + "\t");
                    d.increment();
                }
                System.out.print(response[i]);
                System.out.println();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            SparseDMatrixProvider that = (SparseDMatrixProvider) o;
            return shape == that.shape &&
                nonZeroElementsCount == that.nonZeroElementsCount &&
                Arrays.deepEquals(rowHeaders, that.rowHeaders) &&
                Arrays.deepEquals(colIndices, that.colIndices) &&
                Arrays.deepEquals(sparseData, that.sparseData) &&
                csr == that.csr;
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(super.hashCode(), csr, shape, nonZeroElementsCount);
            result = 31 * result + Arrays.hashCode(rowHeaders);
            result = 31 * result + Arrays.hashCode(colIndices);
            result = 31 * result + Arrays.hashCode(sparseData);
            return result;
        }
    }

    public static SparseDMatrixProvider toDMatrix(
        SparseMatrix sm, SparseMatrixDimensions smd, int actualRows, int shape, float[] resp, float[] weights, float[] offsets) {
        return new SparseDMatrixProvider(
            sm._rowHeaders, sm._colIndices, sm._sparseData, DMatrix.SparseType.CSR, shape, smd._nonZeroElementsCount, 
            actualRows, resp, weights, offsets
        );
    }
    
    static class NestedArrayPointer {
        int _row, _col;

        public NestedArrayPointer() {
        }

        public NestedArrayPointer(long pos) {
            this._row = (int) (pos / SparseMatrix.MAX_DIM);
            this._col = (int) (pos % SparseMatrix.MAX_DIM);
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

        public long get(long[][] dest) {
            return dest[_row][_col];
        }

        public int get(int[][] dest) {
            return dest[_row][_col];
        }

        public float get(float[][] dest) {
            return dest[_row][_col];
        }
    }

    public static int initializeFromChunkIds(
        Frame frame, int[] chunks, Vec weightsVec, Vec offsetsVec, DataInfo di,
        SparseMatrix matrix, SparseMatrixDimensions dimensions,
        Vec respVec, float[] resp, float[] weights, float[] offsets
    ) {
        InitializeCSRMatrixFromChunkIdsMrFun fun = new InitializeCSRMatrixFromChunkIdsMrFun(
            frame, chunks, weightsVec, offsetsVec, di, matrix, dimensions, respVec, resp, weights, offsets
        );
        H2O.submitTask(new LocalMR(fun, chunks.length)).join();

        return ArrayUtils.sum(fun._actualRows);
    }

    private static class InitializeCSRMatrixFromChunkIdsMrFun extends MrFun<InitializeCSRMatrixFromChunkIdsMrFun> {

        Frame _frame;
        int[] _chunks;
        Vec _weightVec;
        Vec _offsetsVec;
        DataInfo _di;
        SparseMatrix _matrix;
        SparseMatrixDimensions _dims;
        Vec _respVec;
        float[] _resp;
        float[] _weights;
        float[] _offsets;
        
        // OUT
        int[] _actualRows;
        
        InitializeCSRMatrixFromChunkIdsMrFun(
            Frame frame, int[] chunks, Vec weightVec, Vec offsetVec, DataInfo di,
            SparseMatrix matrix, SparseMatrixDimensions dimensions,
            Vec respVec, float[] resp, float[] weights, float[] offsets
        ) {
            _actualRows = new int[chunks.length];
            
            _frame = frame;
            _chunks = chunks;
            _weightVec = weightVec;
            _offsetsVec = offsetVec;
            _di = di;
            _matrix = matrix;
            _dims = dimensions;
            _respVec = respVec;
            _resp = resp;
            _weights = weights;
            _offsets = offsets;
        }

        @Override
        protected void map(int chunkIdx) {
            int chunk = _chunks[chunkIdx];
            long nonZeroCount = _dims._precedingNonZeroElementsCounts[chunkIdx];
            int rwRow = _dims._precedingRowCounts[chunkIdx];
            NestedArrayPointer rowHeaderPointer = new NestedArrayPointer(rwRow);
            NestedArrayPointer dataPointer = new NestedArrayPointer(nonZeroCount);

            Chunk weightChunk = _weightVec != null ? _weightVec.chunkForChunkIdx(chunk) : null;
            Chunk offsetChunk = _offsetsVec != null ? _offsetsVec.chunkForChunkIdx(chunk) : null;
            Chunk respChunk = _respVec.chunkForChunkIdx(chunk);
            Chunk[] featChunks = new Chunk[_frame.vecs().length];
            for (int i = 0; i < featChunks.length; i++) {
                featChunks[i] = _frame.vecs()[i].chunkForChunkIdx(chunk);
            }
            for(int i = 0; i < respChunk._len; i++) {
                if (weightChunk != null && weightChunk.atd(i) == 0) continue;
                rowHeaderPointer.setAndIncrement(_matrix._rowHeaders, nonZeroCount);
                _actualRows[chunkIdx]++;
                for (int j = 0; j < _di._cats; j++) {
                    dataPointer.set(_matrix._sparseData, 1);
                    if (featChunks[j].isNA(i)) {
                        dataPointer.set(_matrix._colIndices, _di.getCategoricalId(j, Float.NaN));
                    } else {
                        dataPointer.set(_matrix._colIndices, _di.getCategoricalId(j, featChunks[j].at8(i)));
                    }
                    dataPointer.increment();
                    nonZeroCount++;
                }
                for (int j = 0; j < _di._nums; j++) {
                    float val = (float) featChunks[_di._cats + j].atd(i);
                    if (val != 0) {
                        dataPointer.set(_matrix._sparseData, val);
                        dataPointer.set(_matrix._colIndices, _di._catOffsets[_di._catOffsets.length - 1] + j);
                        dataPointer.increment();
                        nonZeroCount++;
                    }
                }
                rwRow = setResponseWeightAndOffset(weightChunk, offsetChunk, respChunk, _resp, _weights, _offsets, rwRow, i);
            }
            rowHeaderPointer.set(_matrix._rowHeaders, nonZeroCount);
        }
    }

    private static int initializeFromChunks(
        Chunk[] chunks, int weight, DataInfo di, long[][] rowHeaders, float[][] data, int[][] colIndex, 
        int respIdx, float[] resp, float[] weights, int offsetIdx, float[] offsets
    ) {
        int actualRows = 0;
        int nonZeroCount = 0;
        int rwRow = 0;

        NestedArrayPointer rowHeaderPointer = new NestedArrayPointer();
        NestedArrayPointer dataPointer = new NestedArrayPointer();

        for (int i = 0; i < chunks[0].len(); i++) {
            if (weight != -1 && chunks[weight].atd(i) == 0) continue;
            actualRows++;
            rowHeaderPointer.setAndIncrement(rowHeaders, nonZeroCount);
            for (int j = 0; j < di._cats; j++) {
                dataPointer.set(data, 1); //one-hot encoding
                if (chunks[j].isNA(i)) {
                    dataPointer.set(colIndex, di.getCategoricalId(j, Float.NaN));
                } else {
                    dataPointer.set(colIndex, di.getCategoricalId(j, chunks[j].at8(i)));
                }
                dataPointer.increment();
                nonZeroCount++;
            }
            for (int j = 0; j < di._nums; j++) {
                float val = (float) chunks[di._cats + j].atd(i);
                if (val != 0) {
                    dataPointer.set(data, val);
                    dataPointer.set(colIndex, di._catOffsets[di._catOffsets.length - 1] + j);
                    dataPointer.increment();
                    nonZeroCount++;
                }
            }
            rwRow = setResponseAndWeightAndOffset(chunks, respIdx, weight, offsetIdx, resp, weights, offsets, rwRow, i);
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
            for (int j = 0; j < di._nums; j++) {
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
