package hex.deepwater;

import hex.deepwater.backends.cudnn.CudaSession;
import hex.deepwater.backends.cudnn.DenseOp;
import org.junit.Test;
import org.tensorflow.framework.OpDef;
import water.util.Pair;

import java.util.List;

/**
 * Created by fmilo on 8/19/16.
 */
public class TestCudnn {

    public int[] cudnnTrain(float[] batch, int batch_size) throws Exception {
        CudaSession sess = null;
        DenseOp op = null;

        try {
            sess = new CudaSession();

            op = new DenseOp(OpDef.newBuilder().build(), sess);
            op.allocate();
            //op.call(FloatBuffer.wrap(batch));
        } finally {
            if (op != null) {
                op.deallocate();
            }
            if (sess != null) {
                sess.deallocate();
            }
        }

        return new int[]{};
    }

    @Test
    public void trainMNIST() throws Exception {
        MnistCudnn cudnn = new MnistCudnn();
        MNISTImageDataset dataset = new MNISTImageDataset("/tmp/data/t10k-labels-idx1-ubyte.gz", "/tmp/data/t10k-images-idx3-ubyte.gz");
        List<Pair<Integer, float[]>> images = dataset.loadDigitImages();

        int wrong_prediction = 0;
        int right_prediction = 0;
        int batch_size = 10;
        float[] batch = new float[784 * batch_size];
        int[] batch_labels = new int[784 * batch_size];
        int current_batch_size = 0;
        for (Pair<Integer, float[]> sample : images) {
            float[] array = sample.getValue();
            // accumulate images and labels into a batch
            System.arraycopy(array, 0, batch, current_batch_size * 784, array.length);
            batch_labels[current_batch_size] = sample.getKey();
            current_batch_size++;
            if (current_batch_size < batch_size) {
                continue;
            }

            current_batch_size = 0;
            int[] predictions = cudnnTrain(batch, batch_size);
            for (int i = 0; i < predictions.length; i++) {
                if (predictions[i] != batch_labels[i]) {
                    wrong_prediction++;
                } else {
                    right_prediction++;
                }
            }
        }

        System.out.println("right predictions: " + right_prediction + " - wrong Predictions:" + wrong_prediction);
        System.out.println("accuracy:" + right_prediction / 1.0 * (right_prediction + wrong_prediction));
    }
}