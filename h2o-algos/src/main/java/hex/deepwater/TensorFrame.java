package hex.deepwater;

import static org.bytedeco.javacpp.tensorflow.*;

public class TensorFrame<T extends Number> extends Tensor {

    static public TensorFrame newInt32(long ...shape){
        return new TensorFrame<Integer>(DT_INT32, new TensorShape(shape));
    }

    static public TensorFrame newFloat64(long ...shape){
        return new TensorFrame<Double>(DT_DOUBLE, new TensorShape(shape));
    }

    static public TensorFrame allocateFloat32(long ...shape){
        return new TensorFrame<Float>(DT_FLOAT, new TensorShape(shape));
    }

    private TensorFrame(int type, TensorShape shape){
        super(type, shape);
    }

}
