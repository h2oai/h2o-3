package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNodeStat;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AuxNodeWeightsHelper {

    private static final int DOUBLE_BYTES = Double.SIZE / Byte.SIZE;
    private static final int INTEGER_BYTES = Integer.SIZE / Byte.SIZE;
    
    public static byte[] toBytes(double[][] auxNodeWeights) {
        int elements = 0;
        for (double[] weights : auxNodeWeights)
            elements += weights.length;
        int len = (1 + auxNodeWeights.length) * DOUBLE_BYTES + elements * INTEGER_BYTES;
        ByteBuffer bb = ByteBuffer.wrap(new byte[len]).order(ByteOrder.nativeOrder());
        bb.putInt(auxNodeWeights.length);
        for (double[] weights : auxNodeWeights) {
            bb.putInt(weights.length);
            for (double w : weights)
                bb.putDouble(w);
        }
        return bb.array();
    }

    static double[][] fromBytes(byte[] auxNodeWeightBytes) {
        ByteBuffer bb = ByteBuffer.wrap(auxNodeWeightBytes).order(ByteOrder.nativeOrder());
        double[][] auxNodeWeights = new double[bb.getInt()][];
        for (int i = 0; i < auxNodeWeights.length; i++) {
            double[] weights = new double[bb.getInt()];
            for (int j = 0; j < weights.length; j++)
                weights[j] = bb.getDouble();
            auxNodeWeights[i] = weights;
        }
        return auxNodeWeights;
    }

    // FIXME: ugly & hacky - good for a POC only
    static void updateNodeWeights(RegTree[] trees, double[][] nodeWeights) {
        final Field field;
        try {
            field = RegTreeNodeStat.class.getDeclaredField("sum_hess");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Unable to access field 'sum_hess'.");
        }
        try {
            for (int i = 0; i < nodeWeights.length; i++) {
                RegTreeNodeStat[] stats = trees[i].getStats();
                assert stats.length == nodeWeights[i].length;
                for (int j = 0; j < nodeWeights[i].length; j++)
                    field.setFloat(stats[j], (float) nodeWeights[i][j]);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
