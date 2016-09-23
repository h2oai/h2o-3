package hex.deepwater.backends;

import hex.deepwater.datasets.DataSet;

/**
 * Created by fmilo on 9/22/16.
 */
public interface BackendTrain {

    void delete();

    void buildNet(DataSet dataset, RuntimeOptions opts, int num_classes, int batch_size, String name);

    void saveModel(String model_path);

    void loadParam(String param_path);

    void saveParam(String param_path);

    String toJson();

    // learning_rate
    // weight_decay
    // momentum
    // clip_gradient: bool
    void setParameter(String name, float value);

//    public void setLR(float var1) {
//        deepwaterJNI.ImageTrain_setLR(this.swigCPtr, this, var1);
//    }
//
//    public void setWD(float var1) {
//        deepwaterJNI.ImageTrain_setWD(this.swigCPtr, this, var1);
//    }
//
//    public void setMomentum(float var1) {
//        deepwaterJNI.ImageTrain_setMomentum(this.swigCPtr, this, var1);
//    }
//
//    public void setClipGradient(float var1) {
//        deepwaterJNI.ImageTrain_setClipGradient(this.swigCPtr, this, var1);
//    }

    float[] train(float[] data, float[] label);

    float[] predict(float[] data, float[] label);

    float[] predict(float[] data);
}