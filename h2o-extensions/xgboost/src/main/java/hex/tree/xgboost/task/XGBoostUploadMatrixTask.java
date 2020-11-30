package hex.tree.xgboost.task;

import hex.DataInfo;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.exec.XGBoostHttpClient;
import hex.tree.xgboost.matrix.SparseMatrixDimensions;
import hex.tree.xgboost.remote.RemoteXGBoostUploadServlet;
import org.apache.log4j.Logger;
import water.H2O;
import water.LocalMR;
import water.MrFun;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.VecUtils;

import java.io.Serializable;
import java.util.Optional;

import static hex.tree.xgboost.XGBoostUtils.sumChunksLength;
import static hex.tree.xgboost.matrix.MatrixFactoryUtils.setResponseWeightAndOffset;
import static hex.tree.xgboost.matrix.SparseMatrixFactory.calculateCSRMatrixDimensions;
import static hex.tree.xgboost.remote.RemoteXGBoostUploadServlet.RequestType.*;
import static water.MemoryManager.malloc4f;

public class XGBoostUploadMatrixTask extends AbstractXGBoostTask<XGBoostUploadMatrixTask> {

    private static final Logger LOG = Logger.getLogger(XGBoostUploadMatrixTask.class);

    private final String[] remoteNodes;
    private final boolean https;
    private final String contextPath;
    private final String userName;
    private final String password;

    private final Frame train;
    private final XGBoostModelInfo modelInfo;
    private final XGBoostModel.XGBoostParameters parms;
    private final boolean sparse;

    public XGBoostUploadMatrixTask(
        XGBoostModel model, Frame train, boolean[] frameNodes, String[] remoteNodes, 
        boolean https, String contextPath, String userName, String password
    ) {
        super(model._key, frameNodes);
        this.remoteNodes = remoteNodes;
        this.https = https;
        this.contextPath = contextPath;
        this.userName = userName;
        this.password = password;

        this.modelInfo = model.model_info();
        this.parms = model._parms;
        this.sparse = model._output._sparse;
        this.train = train;
    }
    
    private XGBoostHttpClient makeClient() {
        String remoteUri = remoteNodes[H2O.SELF.index()] + contextPath;
        return new XGBoostHttpClient(remoteUri, https, userName, password);
    }

    @Override
    protected void execute() {
        XGBoostHttpClient client = makeClient();
        LOG.info("Starting matrix upload for " + _modelKey);
        long start = System.currentTimeMillis();
        assert modelInfo.dataInfo() != null;
        int[] chunks = VecUtils.getLocalChunkIds(train.anyVec());
        final Vec responseVec = train.vec(parms._response_column);
        final Vec weightVec = train.vec(parms._weights_column);
        final Vec offsetsVec = train.vec(parms._offset_column);
        final int[] nRowsByChunk = new int[chunks.length];
        final long nRowsL = sumChunksLength(chunks, responseVec, Optional.ofNullable(weightVec), nRowsByChunk);
        if (nRowsL > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("XGBoost currently doesn't support datasets with more than " +
                Integer.MAX_VALUE + " per node. " +
                "To train a XGBoost model on this dataset add more nodes to your H2O cluster and use distributed training.");
        }
        final int nRows = (int) nRowsL;
        MatrixData matrixData = new MatrixData(nRows, weightVec, offsetsVec);
        if (sparse) {
            LOG.debug("Treating matrix as sparse.");
            matrixData.shape = modelInfo.dataInfo().fullN();
            matrixData.actualRows = csr(
                client, chunks, weightVec, offsetsVec, responseVec, modelInfo.dataInfo(), 
                matrixData.resp, matrixData.weights, matrixData.offsets
            );
        } else {
            LOG.debug("Treating matrix as dense.");
            matrixData.actualRows = dense(
                client, chunks, nRows, nRowsByChunk, weightVec, offsetsVec, responseVec, modelInfo.dataInfo(),
                matrixData.resp, matrixData.weights, matrixData.offsets
            );
        }
        client.uploadObject(_modelKey, RemoteXGBoostUploadServlet.RequestType.matrixData, matrixData);
        LOG.debug("Matrix upload finished in " + ((System.currentTimeMillis() - start) / 1000d));
    }

    public static class MatrixData implements Serializable {
        public final float[] resp;
        public final float[] weights;
        public final float[] offsets;
        public int actualRows;
        public int shape;

        MatrixData(int nRows, Vec weightVec, Vec offsetsVec) {
            resp = malloc4f(nRows);
            if (weightVec != null) {
                weights = malloc4f(nRows);
            } else {
                weights = null;
            }
            if (offsetsVec != null) {
                offsets = malloc4f(nRows);
            } else {
                offsets = null;
            }
        }
    }
    
    public static class DenseMatrixDimensions implements Serializable {
        public final int rows;
        public final int cols;
        public final int[] rowOffsets;

        public DenseMatrixDimensions(int rows, int cols, int[] rowOffsets) {
            this.rows = rows;
            this.cols = cols;
            this.rowOffsets = rowOffsets;
        }
    }

    private int dense(
        XGBoostHttpClient client, int[] chunksIds, int nRows, int[] nRowsByChunk, 
        Vec weightVec, Vec offsetsVec, Vec responseVec, DataInfo dataInfo, 
        float[] resp, float[] weights, float[] offsets
    ) {
        int[] rowOffsets = new int[nRowsByChunk.length + 1];
        for (int i = 0; i < chunksIds.length; i++) {
            rowOffsets[i + 1] = nRowsByChunk[i] + rowOffsets[i];
        }
        client.uploadObject(_modelKey, denseMatrixDimensions, new DenseMatrixDimensions(nRows, dataInfo.fullN(), rowOffsets));
        UploadDenseChunkFun writeFun = new UploadDenseChunkFun(
            train, chunksIds, rowOffsets, weightVec, offsetsVec, responseVec, dataInfo, resp, weights, offsets
        );
        H2O.submitTask(new LocalMR<>(writeFun, chunksIds.length)).join();
        return writeFun.getTotalRows();
    }

    public static class DenseMatrixChunk implements Serializable {
        public final int id;
        public final float[] data;

        DenseMatrixChunk(int id, int dataSize) {
            this.id = id;
            this.data = new float[dataSize];
        }
    }

    private class UploadDenseChunkFun extends MrFun<UploadDenseChunkFun> {

        private final Frame _f;
        private final int[] _chunks;
        private final int[] _rowOffsets;
        private final Vec _weightsVec;
        private final Vec _offsetsVec;
        private final Vec _respVec;
        private final DataInfo _di;
        private final float[] _resp;
        private final float[] _weights;
        private final float[] _offsets;

        // OUT
        private final int[] _nRowsByChunk;

        private UploadDenseChunkFun(
            Frame f, int[] chunks, int[] rowOffsets, Vec weightsVec, Vec offsetsVec, Vec respVec, DataInfo di,
            float[] resp, float[] weights, float[] offsets
        ) {
            _f = f;
            _chunks = chunks;
            _rowOffsets = rowOffsets;
            _weightsVec = weightsVec;
            _offsetsVec = offsetsVec;
            _respVec = respVec;
            _di = di;
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
            int idx = 0;
            DenseMatrixChunk chunkData = new DenseMatrixChunk(id, (_rowOffsets[id+1] - _rowOffsets[id]) * _di.fullN());
            int actualRows = 0;
            for (int i = 0; i < chks[0]._len; i++) {
                if (weightsChk != null && weightsChk.atd(i) == 0) continue;

                idx = writeDenseRow(_di, chks, i, chunkData.data, idx);
                _resp[_rowOffsets[id] + actualRows] = (float) respChk.atd(i);
                if (weightsChk != null) {
                    _weights[_rowOffsets[id] + actualRows] = (float) weightsChk.atd(i);
                }
                if (offsetsChk != null) {
                    _offsets[_rowOffsets[id] + actualRows] = (float) offsetsChk.atd(i);
                }

                actualRows++;
            }
            assert idx == chunkData.data.length : "idx should be " + chunkData.data.length + " but it is " + idx;
            _nRowsByChunk[id] = actualRows;
            makeClient().uploadObject(_modelKey, denseMatrixChunk, chunkData);
        }

        private int writeDenseRow(
            DataInfo di, Chunk[] chunks, int rowInChunk, float[] data, int idx
        ) {
            for (int j = 0; j < di._cats; j++) {
                int len = di._catOffsets[j+1] - di._catOffsets[j];
                double val = chunks[j].isNA(rowInChunk) ? Double.NaN : chunks[j].at8(rowInChunk);
                int pos = di.getCategoricalId(j, val) - di._catOffsets[j];
                data[idx + pos] = 1f;
                idx += len;
            }
            for (int j = 0; j < di._nums; j++) {
                float val = chunks[di._cats + j].isNA(rowInChunk) ? Float.NaN : (float) chunks[di._cats + j].atd(rowInChunk);
                data[idx++] = val;
            }
            return idx;
        }

        private int getTotalRows() {
            int totalRows = 0;
            for (int r : _nRowsByChunk) {
                totalRows += r;
            }
            return totalRows;
        }

    }
    
    private int csr(
        XGBoostHttpClient client, int[] chunksIds, 
        Vec weightVec, Vec offsetsVec, Vec responseVec, DataInfo dataInfo, 
        float[] resp, float[] weights, float[] offsets
    ) {
        SparseMatrixDimensions dimensions = calculateCSRMatrixDimensions(train, chunksIds, weightVec, dataInfo);
        client.uploadObject(_modelKey, sparseMatrixDimensions, dimensions);
        UploadSparseMatrixFun fun = new UploadSparseMatrixFun(
            train, chunksIds, weightVec, offsetsVec, dataInfo, dimensions, responseVec, resp, weights, offsets
        );
        H2O.submitTask(new LocalMR<>(fun, chunksIds.length)).join();
        return ArrayUtils.sum(fun._actualRows);
    }
    
    public static class SparseMatrixChunk implements Serializable {
        public final int id;
        public final long[] rowHeader;
        public final float[] data;
        public final int[] colIndices;

        SparseMatrixChunk(int id, int rowHeaderSize, int dataSize) {
            this.id = id;
            this.rowHeader = new long[rowHeaderSize];
            this.data = new float[dataSize];
            this.colIndices = new int[dataSize];
        }
    }
    
    private class UploadSparseMatrixFun extends MrFun<UploadSparseMatrixFun> {

        Frame _frame;
        int[] _chunks;
        Vec _weightVec;
        Vec _offsetsVec;
        DataInfo _di;
        SparseMatrixDimensions _dims;
        Vec _respVec;
        float[] _resp;
        float[] _weights;
        float[] _offsets;

        // OUT
        int[] _actualRows;

        UploadSparseMatrixFun(
            Frame frame, int[] chunks, Vec weightVec, Vec offsetVec, DataInfo di,
            SparseMatrixDimensions dimensions,
            Vec respVec, float[] resp, float[] weights, float[] offsets
        ) {
            _actualRows = new int[chunks.length];

            _frame = frame;
            _chunks = chunks;
            _weightVec = weightVec;
            _offsetsVec = offsetVec;
            _di = di;
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
            int rowHeaderSize;
            long dataSize;
            if (chunkIdx == _dims._precedingNonZeroElementsCounts.length-1) {
                rowHeaderSize = _dims._rowHeadersCount - rwRow;
                dataSize = _dims._nonZeroElementsCount - nonZeroCount;
            } else {
                rowHeaderSize = _dims._precedingRowCounts[chunkIdx+1] - rwRow + 1;
                dataSize = _dims._precedingNonZeroElementsCounts[chunkIdx+1] - nonZeroCount;
            }
            assert dataSize < Integer.MAX_VALUE;

            Chunk weightChunk = _weightVec != null ? _weightVec.chunkForChunkIdx(chunk) : null;
            Chunk offsetChunk = _offsetsVec != null ? _offsetsVec.chunkForChunkIdx(chunk) : null;
            Chunk respChunk = _respVec.chunkForChunkIdx(chunk);
            Chunk[] featChunks = new Chunk[_frame.vecs().length];
            for (int i = 0; i < featChunks.length; i++) {
                featChunks[i] = _frame.vecs()[i].chunkForChunkIdx(chunk);
            }
            SparseMatrixChunk chunkData = new SparseMatrixChunk(chunkIdx, rowHeaderSize, (int) dataSize);
            int dataIndex = 0;
            int rowHeaderIndex = 0;
            for(int i = 0; i < respChunk._len; i++) {
                if (weightChunk != null && weightChunk.atd(i) == 0) continue;
                chunkData.rowHeader[rowHeaderIndex++] = nonZeroCount;
                _actualRows[chunkIdx]++;
                for (int j = 0; j < _di._cats; j++) {
                    chunkData.data[dataIndex] = 1;
                    if (featChunks[j].isNA(i)) {
                        chunkData.colIndices[dataIndex] = _di.getCategoricalId(j, Float.NaN);
                    } else {
                        chunkData.colIndices[dataIndex] = _di.getCategoricalId(j, featChunks[j].at8(i));
                    }
                    dataIndex++;
                    nonZeroCount++;
                }
                for (int j = 0; j < _di._nums; j++) {
                    float val = (float) featChunks[_di._cats + j].atd(i);
                    if (val != 0) {
                        chunkData.data[dataIndex] = val;
                        chunkData.colIndices[dataIndex] = _di._catOffsets[_di._catOffsets.length - 1] + j;
                        dataIndex++;
                        nonZeroCount++;
                    }
                }
                rwRow = setResponseWeightAndOffset(weightChunk, offsetChunk, respChunk, _resp, _weights, _offsets, rwRow, i);
            }
            chunkData.rowHeader[rowHeaderIndex] = nonZeroCount;
            makeClient().uploadObject(_modelKey, sparseMatrixChunk, chunkData);
        }
    }

}
