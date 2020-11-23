package hex.tree.xgboost;

import hex.DataInfo;
import hex.tree.xgboost.matrix.DenseMatrixFactory;
import hex.tree.xgboost.matrix.MatrixLoader;
import hex.tree.xgboost.matrix.SparseMatrixFactory;
import ai.h2o.xgboost4j.java.DMatrix;
import ai.h2o.xgboost4j.java.XGBoostError;
import org.apache.log4j.Logger;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.VecUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static water.H2O.technote;
import static water.MemoryManager.malloc4f;

public class XGBoostUtils {

    private static final Logger LOG = Logger.getLogger(XGBoostUtils.class);

    public static void createFeatureMap(XGBoostModel model, Frame train) {
        // Create a "feature map" and store in a temporary file (for Variable Importance, MOJO, ...)
        DataInfo dataInfo = model.model_info().dataInfo();
        assert dataInfo != null;
        String featureMap = makeFeatureMap(train, dataInfo);
        model.model_info().setFeatureMap(featureMap);
    }

    private static String makeFeatureMap(Frame f, DataInfo di) {
        // set the names for the (expanded) columns
        String[] coefnames = di.coefNames();
        StringBuilder sb = new StringBuilder();
        assert(coefnames.length == di.fullN());
        int catCols = di._catOffsets[di._catOffsets.length-1];

        for (int i = 0; i < di.fullN(); i++) {
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
     * @param frame H2O Frame - adapted using a provided data info
     * @param response name of the response column
     * @param weight name of the weight column
     * @return DMatrix
     * @throws XGBoostError
     */
    public static MatrixLoader.DMatrixProvider convertFrameToDMatrix(DataInfo di,
                                                Frame frame,
                                                String response,
                                                String weight,
                                                String offset,
                                                boolean sparse) {
        assert di != null;
        int[] chunks = VecUtils.getLocalChunkIds(frame.anyVec());
        final Vec responseVec = frame.vec(response);
        final Vec weightVec = frame.vec(weight);
        final Vec offsetsVec = frame.vec(offset);
        final int[] nRowsByChunk = new int[chunks.length];
        final long nRowsL = sumChunksLength(chunks, responseVec, Optional.ofNullable(weightVec), nRowsByChunk);
        if (nRowsL > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("XGBoost currently doesn't support datasets with more than " +
                    Integer.MAX_VALUE + " per node. " +
                    "To train a XGBoost model on this dataset add more nodes to your H2O cluster and use distributed training.");
        }
        final int nRows = (int) nRowsL;

        final MatrixLoader.DMatrixProvider trainMat;

        // In the future this 2 arrays might also need to be rewritten into float[][],
        // but only if we want to handle datasets over 2^31-1 on a single machine. For now I'd leave it as it is.
        float[] resp = malloc4f(nRows);
        float[] weights = null;
        float[] offsets = null;
        if (weightVec != null) {
            weights = malloc4f(nRows);
        }
        if (offsetsVec != null) {
            offsets = malloc4f(nRows);
        }
        if (sparse) {
            LOG.debug("Treating matrix as sparse.");
            trainMat = SparseMatrixFactory.csr(frame, chunks, weightVec, offsetsVec, responseVec, di, resp, weights, offsets);
        } else {
            LOG.debug("Treating matrix as dense.");
            trainMat = DenseMatrixFactory.dense(frame, chunks, nRows, nRowsByChunk, weightVec, offsetsVec, responseVec, di, resp, weights, offsets);
        }
        return trainMat;
    }

    /**
     * Counts a total sum of chunks inside a vector. Only chunks present in chunkIds are counted.
     * If a weights vector is provided, only rows with non-zero weights are counted.
     *
     * @param chunkIds Chunk ids to consider during the calculation. Chunks IDs not listed are not included.
     * @param vec      Vector containing given chunk identifiers
     * @param weightsVector Vector with row weights, possibly an empty optional
     * @param chunkLengths Array of integers where the lengths of the individual chunks will be added. Initialization to an array of 0's is expected.
     * @return A sum of chunk lengths. Possibly zero, if there are no chunks or the chunks are empty.
     */
    public static long sumChunksLength(int[] chunkIds, Vec vec, Optional<Vec> weightsVector, int[] chunkLengths) {
        assert chunkLengths.length == chunkIds.length;
        for (int i = 0; i < chunkIds.length; i++) {
            final int chunk = chunkIds[i];
            if (weightsVector.isPresent()) {
                final Chunk weightVecChunk = weightsVector.get().chunkForChunkIdx(chunk);
                assert weightVecChunk.len() == vec.chunkLen(chunk); // Chunk layout of both vectors must be the same
                if (weightVecChunk.len() == 0) continue;

                int nzIndex = 0;
                do {
                    if (weightVecChunk.atd(nzIndex) != 0) chunkLengths[i]++;
                    nzIndex = weightVecChunk.nextNZ(nzIndex, true);
                } while (nzIndex > 0 && nzIndex < weightVecChunk._len);
            } else {
                chunkLengths[i] = vec.chunkLen(chunk);
            }
        }
        long totalChunkLength = 0;
        for (int cl : chunkLengths) {
            totalChunkLength += cl;
        }
        return totalChunkLength;
    }

    /**
     * convert a set of H2O chunks (representing a part of a vector) to a sparse DMatrix
     * @return DMatrix
     * @throws XGBoostError
     */
    public static DMatrix convertChunksToDMatrix(
        DataInfo di, Chunk[] chunks, int response, boolean sparse, int offset
    ) throws XGBoostError {
        int nRows = chunks[0]._len;
        DMatrix trainMat;
        float[] resp = malloc4f(nRows);
        float[] off = null; 
        if (offset >= 0) {
            off = malloc4f(nRows);
        } 
        try {
            if (sparse) {
                LOG.debug("Treating matrix as sparse.");
                trainMat = SparseMatrixFactory.csr(chunks, -1, response, offset, di, resp, null, off);
            } else {
                trainMat = DenseMatrixFactory.dense(chunks, di, response, resp, null, offset, off);
            }
        } catch (NegativeArraySizeException e) {
            throw new IllegalArgumentException(technote(11,
                "Data is too large to fit into the 32-bit Java float[] array that needs to be passed to the XGBoost C++ backend. Use H2O GBM instead."));
        }
        int len = (int) trainMat.rowNum();
        if (off != null) {
            off = Arrays.copyOf(off, len);
            trainMat.setBaseMargin(off);
        }
        resp = Arrays.copyOf(resp, len);
        trainMat.setLabel(resp);
        return trainMat;
    }

    public static FeatureProperties assembleFeatureNames(final DataInfo di) {
        String[] coefnames = di.coefNames();
        assert (coefnames.length == di.fullN());
        int numCatCols = di._catOffsets[di._catOffsets.length - 1];

        String[] featureNames = new String[di.fullN()];
        boolean[] oneHotEncoded = new boolean[di.fullN()];
        int[] originalColumnIndices = di.coefOriginalColumnIndices();
        for (int i = 0; i < di.fullN(); i++) {
            featureNames[i] = coefnames[i];
            if (i < numCatCols) {
                oneHotEncoded[i] = true;
            }
        }
        return new FeatureProperties(di._adaptedFrame._names, featureNames, oneHotEncoded, originalColumnIndices);
    }

    public static class FeatureProperties {
        public String[] _originalNames;
        public Map<String, Integer> _originalNamesMap;
        public String[] _names;
        public boolean[] _oneHotEncoded;
        public int[] _originalColumnIndices;

        public FeatureProperties(String[] originalNames, String[] names, boolean[] oneHotEncoded, int[] originalColumnIndices) {
            _originalNames = originalNames;
            _originalNamesMap = new HashMap<>();
            for(int i = 0; i < originalNames.length; i++){
                _originalNamesMap.put(originalNames[i], i);
            }
            _names = names;
            _oneHotEncoded = oneHotEncoded;
            _originalColumnIndices = originalColumnIndices;
        }
        
        public int getOriginalIndex(String originalName){
            return _originalNamesMap.get(originalName);
        }
        
        public Integer[] mapOriginalNamesToIndices(String[] names){
            Integer[] res = new Integer[names.length];
            for(int i = 0; i<names.length; i++){
                res[i] = getOriginalIndex(names[i]);
            }
            return res;
        }
    }

}
