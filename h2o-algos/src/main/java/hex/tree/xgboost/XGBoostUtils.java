package hex.tree.xgboost;

import hex.DataInfo;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.Key;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static water.H2O.technote;

// TODO: both convert methods probably can be DRYed since the general logic is the same, just a lot of small differences
public class XGBoostUtils {

    public static int countUnique(int[] unsortedArray) {
        if (unsortedArray.length == 0) {
            return 0;
        }
        int[] array = Arrays.copyOf(unsortedArray, unsortedArray.length);
        Arrays.sort(array);
        int count = 1;
        for (int i = 0; i < array.length - 1; i++) {
            if (array[i] != array[i + 1]) {
                count++;
            }
        }
        return count;
    }

    /**
     * convert an H2O Frame to a sparse DMatrix
     * @param f H2O Frame
     * @param onlyLocal if true uses only chunks local to this node
     * @param response name of the response column
     * @param weight name of the weight column
     * @param fold name of the fold assignment column
     * @param featureMap featureMap[0] will be populated with the column names and types
     * @return DMatrix
     * @throws XGBoostError
     */
    public static DMatrix convertFrameToDMatrix(Key<DataInfo> dataInfoKey,
                                                Frame f,
                                                boolean onlyLocal,
                                                String response,
                                                String weight,
                                                String fold,
                                                String[] featureMap,
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

        long nRows = 0;
        for(int chId : chunks) {
            nRows += vec.chunkLen(chId);
        }

        if(0 == nRows) {
            return null;
        }

        DataInfo di = dataInfoKey.get();
        // set the names for the (expanded) columns
        if (featureMap!=null) {
            String[] coefnames = di.coefNames();
            StringBuilder sb = new StringBuilder();
            assert(coefnames.length == di.fullN());
            for (int i = 0; i < di.fullN(); ++i) {
                sb.append(i).append(" ").append(coefnames[i].replaceAll("\\s*","")).append(" ");
                int catCols = di._catOffsets[di._catOffsets.length-1];
                if (i < catCols || f.vec(i-catCols).isBinary())
                    sb.append("i");
                else if (f.vec(i-catCols).isInt())
                    sb.append("int");
                else
                    sb.append("q");
                sb.append("\n");
            }
            featureMap[0] = sb.toString();
        }

        DMatrix trainMat;
        int nz = 0;
        int actualRows = 0;
        Vec.Reader w = weight == null ? null : f.vec(weight).new Reader();
        Vec.Reader[] vecs = new Vec.Reader[f.numCols()];
        for (int i = 0; i < vecs.length; ++i) {
            vecs[i] = f.vec(i).new Reader();
        }

        try {
            if (sparse) {
                Log.info("Treating matrix as sparse.");
                // 1 0 2 0
                // 4 0 0 3
                // 3 1 2 0
                boolean csc = false; //di._cats == 0;

                // truly sparse matrix - no categoricals
                // collect all nonzeros column by column (in parallel), then stitch together into final data structures
                if (csc) {

                    // CSC:
//    long[] colHeaders = new long[] {0,        3,  4,     6,    7}; //offsets
//    float[] data = new float[]     {1f,4f,3f, 1f, 2f,2f, 3f};      //non-zeros down each column
//    int[] rowIndex = new int[]     {0,1,2,    2,  0, 2,  1};       //row index for each non-zero

                    class SparseItem {
                        int pos;
                        double val;
                    }
                    int nCols = di._nums;

                    List<SparseItem>[] col = new List[nCols]; //TODO: use more efficient storage (no GC)
                    // allocate
                    for (int i=0;i<nCols;++i) {
                        col[i] = new ArrayList<>((int)Math.min(nRows, 10000));
                    }

                    // collect non-zeros
                    int nzCount=0;
                    for (int i=0;i<nCols;++i) { //TODO: parallelize over columns
                        Vec v = f.vec(i);
                        for (Integer c : chunks) {
                            Chunk ck = v.chunkForChunkIdx(c);
                            int[] nnz = new int[ck.sparseLenZero()];
                            int nnzCount = ck.nonzeros(nnz);
                            for (int k=0;k<nnzCount;++k) {
                                SparseItem item = new SparseItem();
                                int localIdx = nnz[k];
                                item.pos = (int)ck.start() + localIdx;
                                // both 0 and NA are omitted in the sparse DMatrix
                                if (w != null && w.at(item.pos) == 0) continue;
                                if (ck.isNA(localIdx)) continue;
                                item.val = ck.atd(localIdx);
                                col[i].add(item);
                                nzCount++;
                            }
                        }
                    }
                    long[] colHeaders = new long[nCols + 1];
                    float[] data = new float[nzCount];
                    int[] rowIndex = new int[nzCount];
                    // fill data for DMatrix
                    for (int i=0;i<nCols;++i) { //TODO: parallelize over columns
                        List sparseCol = col[i];
                        colHeaders[i] = nz;
                        for (int j=0;j<sparseCol.size();++j) {
                            SparseItem si = (SparseItem)sparseCol.get(j);
                            rowIndex[nz] = si.pos;
                            data[nz] = (float)si.val;
                            assert(si.val != 0);
                            assert(!Double.isNaN(si.val));
                            assert(w == null || w.at(si.pos) != 0);
                            nz++;
                        }
                    }
                    colHeaders[nCols] = nz;
                    data = Arrays.copyOf(data, nz);
                    rowIndex = Arrays.copyOf(rowIndex, nz);
                    actualRows = countUnique(rowIndex);
                    trainMat = new DMatrix(colHeaders, rowIndex, data, DMatrix.SparseType.CSC, actualRows);
                    assert trainMat.rowNum() == actualRows;
                } else {

                    // CSR:
//    long[] rowHeaders = new long[] {0,      2,      4,         7}; //offsets
//    float[] data = new float[]     {1f,2f,  4f,3f,  3f,1f,2f};     //non-zeros across each row
//    int[] colIndex = new int[]     {0, 2,   0, 3,   0, 1, 2};      //col index for each non-zero

                    long[] rowHeaders = new long[(int)nRows + 1];
                    int initial_size = 1 << 20;
                    float[] data = new float[initial_size];
                    int[] colIndex = new int[initial_size];

                    // extract predictors
                    rowHeaders[0] = 0;
                    for (Integer chunk : chunks) {
                        for(long i = f.anyVec().espc()[chunk]; i < f.anyVec().espc()[chunk+1]; i++) {
                            if (w != null && w.at(i) == 0) continue;
                            int nzstart = nz;
                            // enlarge final data arrays by 2x if needed
                            while (data.length < nz + di._cats + di._nums) {
                                int newLen = (int) Math.min((long) data.length << 1L, (long) (Integer.MAX_VALUE - 10));
                                Log.info("Enlarging sparse data structure from " + data.length + " bytes to " + newLen + " bytes.");
                                if (data.length == newLen) {
                                    throw new IllegalArgumentException(technote(11, "Data is too large to fit into the 32-bit Java float[] array that needs to be passed to the XGBoost C++ backend. Use H2O GBM instead."));
                                }
                                data = Arrays.copyOf(data, newLen);
                                colIndex = Arrays.copyOf(colIndex, newLen);
                            }
                            for (int j = 0; j < di._cats; ++j) {
                                if (!vecs[j].isNA(i)) {
                                    data[nz] = 1; //one-hot encoding
                                    colIndex[nz] = di.getCategoricalId(j, vecs[j].at8(i));
                                    nz++;
                                } else {
                                    // NA == 0 for sparse -> no need to fill
//            data[nz] = 1; //one-hot encoding
//            colIndex[nz] = di.getCategoricalId(j, Double.NaN); //Fill NA bucket
//            nz++;
                                }
                            }
                            for (int j = 0; j < di._nums; ++j) {
                                float val = (float) vecs[di._cats + j].at(i);
                                if (!Float.isNaN(val) && val != 0) {
                                    data[nz] = val;
                                    colIndex[nz] = di._catOffsets[di._catOffsets.length - 1] + j;
                                    nz++;
                                }
                            }
                            if (nz == nzstart) {
                                // for the corner case where there are no categorical values, and all numerical values are 0, we need to
                                // assign a 0 value to any one column to have a consistent number of rows between the predictors and the special vecs (weight/response/etc.)
                                data[nz] = 0;
                                colIndex[nz] = 0;
                                nz++;
                            }
                            rowHeaders[++actualRows] = nz;
                        }
                    }
                    data = Arrays.copyOf(data, nz);
                    colIndex = Arrays.copyOf(colIndex, nz);
                    rowHeaders = Arrays.copyOf(rowHeaders, actualRows + 1);
                    trainMat = new DMatrix(rowHeaders, colIndex, data, DMatrix.SparseType.CSR, di.fullN());
                    assert trainMat.rowNum() == actualRows;
                }
            } else {
                Log.info("Treating matrix as dense.");

                // extract predictors
                float[] data = new float[1 << 20];
                int cols = di.fullN();
                int pos = 0;
                for (Integer chunk : chunks) {
                    for(long i = f.anyVec().espc()[chunk]; i < f.anyVec().espc()[chunk+1]; i++) {
                        if (w != null && w.at(i) == 0) continue;
                        // enlarge final data arrays by 2x if needed
                        while (data.length < (actualRows + 1) * cols) {
                            int newLen = (int) Math.min((long) data.length << 1L, (long) (Integer.MAX_VALUE - 10));
                            Log.info("Enlarging dense data structure from " + data.length + " bytes to " + newLen + " bytes.");
                            if (data.length == newLen) {
                                throw new IllegalArgumentException(technote(11, "Data is too large to fit into the 32-bit Java float[] array that needs to be passed to the XGBoost C++ backend. Use H2O GBM instead."));
                            }
                            data = Arrays.copyOf(data, newLen);
                        }
                        for (int j = 0; j < di._cats; ++j) {
                            if (vecs[j].isNA(i)) {
                                data[pos + di.getCategoricalId(j, Double.NaN)] = 1; // fill NA bucket
                            } else {
                                data[pos + di.getCategoricalId(j, vecs[j].at8(i))] = 1;
                            }
                        }
                        for (int j = 0; j < di._nums; ++j) {
                            if (vecs[di._cats + j].isNA(i))
                                data[pos + di._catOffsets[di._catOffsets.length - 1] + j] = Float.NaN;
                            else
                                data[pos + di._catOffsets[di._catOffsets.length - 1] + j] = (float) vecs[di._cats + j].at(i);
                        }
                        assert di._catOffsets[di._catOffsets.length - 1] + di._nums == cols;
                        pos += cols;
                        actualRows++;
                    }
                }
                data = Arrays.copyOf(data, actualRows * cols);
                trainMat = new DMatrix(data, actualRows, cols, Float.NaN);
                assert trainMat.rowNum() == actualRows;
            }
        } catch (NegativeArraySizeException e) {
            throw new IllegalArgumentException(technote(11, "Data is too large to fit into the 32-bit Java float[] array that needs to be passed to the XGBoost C++ backend. Use H2O GBM instead."));
        }

        // extract weight vector
        float[] weights = new float[actualRows];
        if (w != null) {
            int j = 0;
            for (Integer val : chunks) {
                for (long i = f.anyVec().espc()[val]; i < f.anyVec().espc()[val + 1]; i++) {
                    if (w.at(i) == 0) continue;
                    weights[j++] = (float) w.at(i);
                }
            }
            assert (j == actualRows);
        }

        // extract response vector
        Vec.Reader respVec = f.vec(response).new Reader();
        float[] resp = new float[actualRows];
        int j = 0;
        for (Integer val : chunks) {
            for (long i = f.anyVec().espc()[val]; i < f.anyVec().espc()[val + 1]; i++) {
                if (w != null && w.at(i) == 0) continue;
                resp[j++] = (float) respVec.at(i);
            }
        }
        assert (j == actualRows);
        resp = Arrays.copyOf(resp, actualRows);
        weights = Arrays.copyOf(weights, actualRows);

        trainMat.setLabel(resp);
        if (w!=null)
            trainMat.setWeight(weights);
//    trainMat.setGroup(null); //fold //FIXME - only needed if CV is internally done in XGBoost
        return trainMat;
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
        long nRows = chunks[0]._len;

        DMatrix trainMat;
        int nz = 0;
        int actualRows = 0;

        DataInfo di = dataInfoKey.get();
        try {
            if (sparse) {
                Log.info("Treating matrix as sparse.");
                // 1 0 2 0
                // 4 0 0 3
                // 3 1 2 0
                boolean csc = false; //di._cats == 0;

                // truly sparse matrix - no categoricals
                // collect all nonzeros column by column (in parallel), then stitch together into final data structures
                if (csc) {

                    // CSC:
                    //    long[] colHeaders = new long[] {0,        3,  4,     6,    7}; //offsets
                    //    float[] data = new float[]     {1f,4f,3f, 1f, 2f,2f, 3f};      //non-zeros down each column
                    //    int[] rowIndex = new int[]     {0,1,2,    2,  0, 2,  1};       //row index for each non-zero

                    class SparseItem {
                        int pos;
                        double val;
                    }
                    int nCols = di._nums;

                    List<SparseItem>[] col = new List[nCols]; //TODO: use more efficient storage (no GC)
                    // allocate
                    for (int i=0;i<nCols;++i) {
                        col[i] = new ArrayList<>((int)Math.min(nRows, 10000));
                    }

                    // collect non-zeros
                    int nzCount=0;
                    for (int i=0;i<nCols;++i) { //TODO: parallelize over columns
                            Chunk ck = chunks[i];
                            int[] nnz = new int[ck.sparseLenZero()];
                            int nnzCount = ck.nonzeros(nnz);
                            for (int k=0;k<nnzCount;++k) {
                                SparseItem item = new SparseItem();
                                int localIdx = nnz[k];
                                item.pos = (int)ck.start() + localIdx;
                                // both 0 and NA are omitted in the sparse DMatrix
                                if (weight != -1 && chunks[weight].atd(localIdx) == 0) continue;
                                if (ck.isNA(localIdx)) continue;
                                item.val = ck.atd(localIdx);
                                col[i].add(item);
                                nzCount++;
                            }
                    }
                    long[] colHeaders = new long[nCols + 1];
                    float[] data = new float[nzCount];
                    int[] rowIndex = new int[nzCount];
                    // fill data for DMatrix
                    for (int i=0;i<nCols;++i) { //TODO: parallelize over columns
                        List sparseCol = col[i];
                        colHeaders[i] = nz;
                        for (int j=0;j<sparseCol.size();++j) {
                            SparseItem si = (SparseItem)sparseCol.get(j);
                            rowIndex[nz] = si.pos;
                            data[nz] = (float)si.val;
                            assert(si.val != 0);
                            assert(!Double.isNaN(si.val));
                            assert(weight == -1 || chunks[weight].atd((int)(si.pos - chunks[weight].start())) != 0);
                            nz++;
                        }
                    }
                    colHeaders[nCols] = nz;
                    data = Arrays.copyOf(data, nz);
                    rowIndex = Arrays.copyOf(rowIndex, nz);
                    actualRows = countUnique(rowIndex);
                    trainMat = new DMatrix(colHeaders, rowIndex, data, DMatrix.SparseType.CSC, actualRows);
                    assert trainMat.rowNum() == actualRows;
                } else {

                    // CSR:
//    long[] rowHeaders = new long[] {0,      2,      4,         7}; //offsets
//    float[] data = new float[]     {1f,2f,  4f,3f,  3f,1f,2f};     //non-zeros across each row
//    int[] colIndex = new int[]     {0, 2,   0, 3,   0, 1, 2};      //col index for each non-zero

                    long[] rowHeaders = new long[(int)nRows + 1];
                    int initial_size = 1 << 20;
                    float[] data = new float[initial_size];
                    int[] colIndex = new int[initial_size];

                    // extract predictors
                    rowHeaders[0] = 0;
                    for (int i = 0; i < chunks[0].len(); i++) {
                        if (weight != -1 && chunks[weight].atd(i) == 0) continue;
                        int nzstart = nz;
                        // enlarge final data arrays by 2x if needed
                        while (data.length < nz + di._cats + di._nums) {
                            int newLen = (int) Math.min((long) data.length << 1L, (long) (Integer.MAX_VALUE - 10));
                            Log.info("Enlarging sparse data structure from " + data.length + " bytes to " + newLen + " bytes.");
                            if (data.length == newLen) {
                                throw new IllegalArgumentException(technote(11, "Data is too large to fit into the 32-bit Java float[] array that needs to be passed to the XGBoost C++ backend. Use H2O GBM instead."));
                            }
                            data = Arrays.copyOf(data, newLen);
                            colIndex = Arrays.copyOf(colIndex, newLen);
                        }
                        for (int j = 0; j < di._cats; ++j) {
                            if (!chunks[j].isNA(i)) {
                                data[nz] = 1; //one-hot encoding
                                colIndex[nz] = di.getCategoricalId(j, chunks[j].at8(i));
                                nz++;
                            } else {
                                // NA == 0 for sparse -> no need to fill
//            data[nz] = 1; //one-hot encoding
//            colIndex[nz] = di.getCategoricalId(j, Double.NaN); //Fill NA bucket
//            nz++;
                            }
                        }
                        for (int j = 0; j < di._nums; ++j) {
                            float val = (float) chunks[di._cats + j].atd(i);
                            if (!Float.isNaN(val) && val != 0) {
                                data[nz] = val;
                                colIndex[nz] = di._catOffsets[di._catOffsets.length - 1] + j;
                                nz++;
                            }
                        }
                        if (nz == nzstart) {
                            // for the corner case where there are no categorical values, and all numerical values are 0, we need to
                            // assign a 0 value to any one column to have a consistent number of rows between the predictors and the special vecs (weight/response/etc.)
                            data[nz] = 0;
                            colIndex[nz] = 0;
                            nz++;
                        }
                        rowHeaders[++actualRows] = nz;
                    }
                    data = Arrays.copyOf(data, nz);
                    colIndex = Arrays.copyOf(colIndex, nz);
                    rowHeaders = Arrays.copyOf(rowHeaders, actualRows + 1);
                    trainMat = new DMatrix(rowHeaders, colIndex, data, DMatrix.SparseType.CSR, di.fullN());
                    assert trainMat.rowNum() == actualRows;
                }
            } else {
                Log.info("Treating matrix as dense.");

                // extract predictors
                float[] data = new float[1 << 20];
                int cols = di.fullN();
                int pos = 0;
                for (int i = 0; i < chunks[0].len(); i++) {
                    if (weight != -1 && chunks[weight].atd(i) == 0) continue;
                    // enlarge final data arrays by 2x if needed
                    while (data.length < (actualRows + 1) * cols) {
                        int newLen = (int) Math.min((long) data.length << 1L, (long) (Integer.MAX_VALUE - 10));
                        Log.info("Enlarging dense data structure from " + data.length + " bytes to " + newLen + " bytes.");
                        if (data.length == newLen) {
                            throw new IllegalArgumentException(technote(11, "Data is too large to fit into the 32-bit Java float[] array that needs to be passed to the XGBoost C++ backend. Use H2O GBM instead."));
                        }
                        data = Arrays.copyOf(data, newLen);
                    }
                    for (int j = 0; j < di._cats; ++j) {
                        if (chunks[j].isNA(i)) {
                            data[pos + di.getCategoricalId(j, Double.NaN)] = 1; // fill NA bucket
                        } else {
                            data[pos + di.getCategoricalId(j, chunks[j].at8(i))] = 1;
                        }
                    }
                    for (int j = 0; j < di._nums; ++j) {
                        if (chunks[di._cats + j].isNA(i))
                            data[pos + di._catOffsets[di._catOffsets.length - 1] + j] = Float.NaN;
                        else
                            data[pos + di._catOffsets[di._catOffsets.length - 1] + j] = (float) chunks[di._cats + j].atd(i);
                    }
                    assert di._catOffsets[di._catOffsets.length - 1] + di._nums == cols;
                    pos += cols;
                    actualRows++;
                }
                data = Arrays.copyOf(data, actualRows * cols);
                trainMat = new DMatrix(data, actualRows, cols, Float.NaN);
                assert trainMat.rowNum() == actualRows;
            }
        } catch (NegativeArraySizeException e) {
            throw new IllegalArgumentException(technote(11, "Data is too large to fit into the 32-bit Java float[] array that needs to be passed to the XGBoost C++ backend. Use H2O GBM instead."));
        }

        // extract weight vector
        float[] weights = new float[actualRows];
        if (weight != -1) {
            int j = 0;
            for (int i = 0; i < chunks[weight].len(); i++) {
                if (chunks[weight].atd(i) == 0) continue;
                weights[j++] = (float) chunks[weight].atd(i);
            }
            assert (j == actualRows);
        }

        // extract response vector
        Chunk respChunk = chunks[response];
        float[] resp = new float[actualRows];
        int j = 0;
        for (int i = 0; i < nRows; ++i) {
            if (weight != -1 && chunks[weight].atd(i) == 0) continue;
            resp[j++] = (float) respChunk.atd(i);
        }
        assert (j == actualRows);
        resp = Arrays.copyOf(resp, actualRows);
        weights = Arrays.copyOf(weights, actualRows);

        trainMat.setLabel(resp);
        if (weight!=-1){
            trainMat.setWeight(weights);
        }
//    trainMat.setGroup(null); //fold //FIXME - only needed if CV is internally done in XGBoost
        return trainMat;
    }

}
