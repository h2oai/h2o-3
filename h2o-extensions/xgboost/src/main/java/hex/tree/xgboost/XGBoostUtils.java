package hex.tree.xgboost;

import hex.DataInfo;
import hex.tree.xgboost.matrix.DenseMatrixFactory;
import hex.tree.xgboost.matrix.SparseMatrixFactory;
import hex.tree.xgboost.util.FeatureScore;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.VecUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
     * @param frame H2O Frame - adapted using a provided data info
     * @param response name of the response column
     * @param weight name of the weight column
     * @return DMatrix
     * @throws XGBoostError
     */
    public static DMatrix convertFrameToDMatrix(DataInfo di,
                                                Frame frame,
                                                String response,
                                                String weight,
                                                String offset,
                                                boolean sparse) throws XGBoostError {
        assert di != null;
        int[] chunks = VecUtils.getLocalChunkIds(frame.anyVec());
        final Vec responseVec = frame.vec(response);
        final Vec weightVec = frame.vec(weight);
        final Vec offsetsVec = frame.vec(offset);
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
        float[] offsets = null;
        if (weightVec != null) {
            weights = malloc4f(nRows);
        }
        if (offsetsVec != null) {
            offsets = malloc4f(nRows);
        }
        if (sparse) {
            Log.debug("Treating matrix as sparse.");
            trainMat = SparseMatrixFactory.csr(frame, chunks, weightVec, offsetsVec, responseVec, di, resp, weights, offsets);
        } else {
            Log.debug("Treating matrix as dense.");
            trainMat = DenseMatrixFactory.dense(frame, chunks, nRows, nRowsByChunk, weightVec, offsetsVec, responseVec, di, resp, weights, offsets);
        }

        assert trainMat.rowNum() == nRows;
        trainMat.setLabel(resp);
        if (weights != null) {
            trainMat.setWeight(weights);
        }
        if (offsets != null) {
            trainMat.setBaseMargin(offsets);
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
                Log.debug("Treating matrix as sparse.");
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

    public static Map<String, FeatureScore> parseFeatureScores(String[] modelDump) {
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

}
