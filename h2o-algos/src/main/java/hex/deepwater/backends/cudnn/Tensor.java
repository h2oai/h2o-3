package hex.deepwater.backends.cudnn;

import org.tensorflow.framework.DataType;

/**
 * Created by fmilo on 8/19/16.
 */
public class Tensor {

    private final Operation operation;
    private final int i;
    private final DataType dataType;

    public Tensor(Operation operation, int i, DataType dataType) {
        this.operation = operation;
        this.i = i;
        this.dataType = dataType;
    }

    public DataType getDataType() {
        return dataType;
    }
}
