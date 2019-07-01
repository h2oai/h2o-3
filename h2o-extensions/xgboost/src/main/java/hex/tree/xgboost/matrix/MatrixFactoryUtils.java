package hex.tree.xgboost.matrix;

import water.fvec.Chunk;
import water.fvec.Vec;

public class MatrixFactoryUtils {

    public static int setResponseAndWeight(Chunk[] chunks, int respIdx, int weightIdx, float[] resp, float[] weights, int j, int i) {
        if (weightIdx != -1) {
            if (chunks[weightIdx].atd(i) == 0) {
                return j;
            }
            weights[j] = (float) chunks[weightIdx].atd(i);
        }
        resp[j++] = (float) chunks[respIdx].atd(i);
        return j;
    }

    public static int setResponseAndWeight(Chunk weightChunk, Chunk respChunk, float[] resp, float[] weights, int j, int i) {
        if (weightChunk != null) {
            if(weightChunk.atd(i) == 0) {
                return j;
            }
            weights[j] = (float) weightChunk.atd(i);
        }
        resp[j++] = (float) respChunk.atd(i);
        return j;
    }

}
