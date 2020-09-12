package hex.tree.xgboost.matrix;

import hex.DataInfo;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import ml.dmlc.xgboost4j.java.util.BigDenseMatrix;
import org.apache.log4j.Logger;
import water.H2O;
import water.LocalMR;
import water.MrFun;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.*;

import static hex.tree.xgboost.matrix.MatrixFactoryUtils.setResponseAndWeightAndOffset;

public class DenseMatrixFactory {

    private static final Logger LOG = Logger.getLogger(DenseMatrixFactory.class);

    public static DMatrix dense(
        Chunk[] chunks, DataInfo di, int respIdx, float[] resp, float[] weights, int offsetIdx, float[] offsets
    ) throws XGBoostError {
        LOG.debug("Treating matrix as dense.");
        BigDenseMatrix data = null;
        try {
            data = allocateDenseMatrix(chunks[0].len(), di);
            long actualRows = denseChunk(data, chunks, respIdx, di, resp, weights, offsetIdx, offsets);
            assert actualRows == data.nrow;
            return new DMatrix(data, Float.NaN);
        } finally {
            if (data != null) {
                data.dispose();
            }
        }
    }
    
    public static class DenseDMatrixProvider extends MatrixLoader.DMatrixProvider {

        private BigDenseMatrix data;

        protected DenseDMatrixProvider(
            long actualRows,
            float[] response,
            float[] weights,
            float[] offsets,
            BigDenseMatrix data
        ) {
            super(actualRows, response, weights, offsets);
            this.data = data;
        }
        
        @Override
        public void print() {
            for (int i = 0; i < data.nrow; i++) {
                System.out.print(i + ":");
                for (int j = 0; j < data.ncol; j++) {
                    System.out.print(data.get(i, j) + ", ");
                }
                System.out.print(response[i]);
                System.out.println();
            }
        }

        @Override
        public DMatrix makeDMatrix() throws XGBoostError {
            return new DMatrix(data, Float.NaN);
        }

        @Override
        protected void dispose() {
            if (data != null) {
                data.dispose();
                data = null;
            }
        }

    }
    
    public static DenseDMatrixProvider dense(
        Frame f, int[] chunks, int nRows, int[] nRowsByChunk, Vec weightVec, Vec offsetVec, Vec responseVec,
        DataInfo di, float[] resp, float[] weights, float[] offsets
    ) {
        BigDenseMatrix data = null;
        try {
            data = allocateDenseMatrix(nRows, di);
            int actualRows = denseChunk(data, chunks, nRowsByChunk, f, weightVec, offsetVec, responseVec, di, resp, weights, offsets);
            assert data.nrow == actualRows;
            return new DenseDMatrixProvider(actualRows, resp, weights, offsets, data);
        } catch (Exception e) {
            if (data != null) {
                data.dispose();
            }
            throw new RuntimeException("Error while create off-heap matrix.", e);
        }
    }

    private static int denseChunk(
        BigDenseMatrix data,
        int[] chunks, int[] nRowsByChunk, Frame f, Vec weightsVec, Vec offsetVec, Vec respVec, DataInfo di,
        float[] resp, float[] weights, float[] offsets
    ) {
        int[] rowOffsets = new int[nRowsByChunk.length + 1];
        for (int i = 0; i < chunks.length; i++) {
            rowOffsets[i + 1] = nRowsByChunk[i] + rowOffsets[i];
        }
        WriteDenseChunkFun writeFun = new WriteDenseChunkFun(
            f, chunks, rowOffsets, weightsVec, offsetVec, respVec, di, data, resp, weights, offsets
        );
        H2O.submitTask(new LocalMR(writeFun, chunks.length)).join();
        return writeFun.getTotalRows();
    }

    private static class WriteDenseChunkFun extends MrFun<WriteDenseChunkFun> {
        private final Frame _f;
        private final int[] _chunks;
        private final int[] _rowOffsets;
        private final Vec _weightsVec;
        private final Vec _offsetsVec;
        private final Vec _respVec;
        private final DataInfo _di;
        private final BigDenseMatrix _data;
        private final float[] _resp;
        private final float[] _weights;
        private final float[] _offsets;

        // OUT
        private final int[] _nRowsByChunk;

        private WriteDenseChunkFun(Frame f, int[] chunks, int[] rowOffsets, Vec weightsVec, Vec offsetsVec, Vec respVec, DataInfo di,
            BigDenseMatrix data, float[] resp, float[] weights, float[] offsets) {
            _f = f;
            _chunks = chunks;
            _rowOffsets = rowOffsets;
            _weightsVec = weightsVec;
            _offsetsVec = offsetsVec;
            _respVec = respVec;
            _di = di;
            _data = data;
            _resp = resp;
            _weights = weights;
            _offsets = offsets;
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
            Chunk offsetsChk = _offsetsVec != null ? _offsetsVec.chunkForChunkIdx(chunkIdx) : null;
            Chunk respChk = _respVec.chunkForChunkIdx(chunkIdx);
            long idx = _rowOffsets[id] * _data.ncol;
            int actualRows = 0;
            for (int i = 0; i < chks[0]._len; i++) {
                if (weightsChk != null && weightsChk.atd(i) == 0) continue;

                idx = writeDenseRow(_di, chks, i, _data, idx);
                _resp[_rowOffsets[id] + actualRows] = (float) respChk.atd(i);
                if (weightsChk != null) {
                    _weights[_rowOffsets[id] + actualRows] = (float) weightsChk.atd(i);
                }
                if (offsetsChk != null) {
                    _offsets[_rowOffsets[id] + actualRows] = (float) offsetsChk.atd(i);
                }

                actualRows++;
            }
            assert idx == (long) _rowOffsets[id + 1] * _data.ncol;
            _nRowsByChunk[id] = actualRows;
        }

        private int getTotalRows() {
            int totalRows = 0;
            for (int r : _nRowsByChunk) {
                totalRows += r;
            }
            return totalRows;
        }

    }

    private static long denseChunk(
        BigDenseMatrix data, Chunk[] chunks, int respIdx, DataInfo di, float[] resp, float[] weights, 
        int offsetIdx, float[] offsets
    ) {
        long idx = 0;
        long actualRows = 0;
        int rwRow = 0;
        for (int i = 0; i < chunks[0]._len; i++) {

            idx = writeDenseRow(di, chunks, i, data, idx);
            actualRows++;

            rwRow = setResponseAndWeightAndOffset(chunks, respIdx, -1, offsetIdx, resp, weights, offsets, rwRow, i);
        }
        assert (long) data.nrow * data.ncol == idx;
        return actualRows;
    }

    private static long writeDenseRow(
        DataInfo di, Chunk[] chunks, int rowInChunk,
        BigDenseMatrix data, long idx
    ) {
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

}
