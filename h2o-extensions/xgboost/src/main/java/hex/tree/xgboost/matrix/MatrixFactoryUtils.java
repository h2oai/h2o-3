package hex.tree.xgboost.matrix;

import water.fvec.Chunk;

public class MatrixFactoryUtils {

    public static int setResponseAndWeightAndOffset(
        Chunk[] chunks, int respIdx, int weightIdx, int offsetIdx, float[] resp, float[] weights, float[] offsets, 
        int j, int i
    ) {
        if (weightIdx != -1) {
            if (chunks[weightIdx].atd(i) == 0) {
                return j;
            }
            weights[j] = (float) chunks[weightIdx].atd(i);
        }
        if (offsetIdx >= 0) {
            offsets[j] = (float) chunks[offsetIdx].atd(i);
        }
        if (respIdx != -1) {
            resp[j++] = (float) chunks[respIdx].atd(i);
        }
        return j;
    }

    public static int setResponseWeightAndOffset(
        Chunk weightChunk, Chunk offsetChunk, Chunk respChunk, float[] resp, float[] weights, float [] offsets, 
        int j, int i
    ) {
        if (weightChunk != null) {
            if(weightChunk.atd(i) == 0) {
                return j;
            }
            weights[j] = (float) weightChunk.atd(i);
        }
        if (offsetChunk != null) {
            offsets[j] = (float) offsetChunk.atd(i);
        }
        resp[j++] = (float) respChunk.atd(i);
        return j;
    }

}
