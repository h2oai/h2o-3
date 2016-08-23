package hex.deepwater.backends.cudnn;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.Pointer;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.OpDef;
import org.tensorflow.framework.TensorDescription;
import org.tensorflow.framework.TensorShapeProto;

import java.nio.ByteBuffer;
import java.util.List;

import static hex.deepwater.backends.cudnn.cudnnUtils.checkCUDNN;
import static org.bytedeco.javacpp.cublas.*;
import static org.bytedeco.javacpp.cudnn.*;


public class DenseOp {

    cudnnTensorStruct biasDesc;
    cudnnTensorStruct outputDesc;
    cudnnTensorStruct weightDesc;
    cudnnActivationStruct activationDesc;

    ByteBuffer bias;
    ByteBuffer weight;
    ByteBuffer output;

    CudaSession s;

    public DenseOp(OpDef opdef, CudaSession s) {
        // get type
        DataType in_type = opdef.getInputArgList().get(0).getType();
        DataType out_type = opdef.getOutputArgList().get(0).getType();
        this.s = s;
    }

    public void allocate() {

        biasDesc = new cudnnTensorStruct();
        weightDesc = new cudnnTensorStruct();
        outputDesc = new cudnnTensorStruct();

        checkCUDNN(cudnnCreateTensorDescriptor(biasDesc));
        checkCUDNN(cudnnCreateTensorDescriptor(weightDesc));
        checkCUDNN(cudnnCreateTensorDescriptor(outputDesc));

        configureTensor(
                TensorDescription.newBuilder().build(),
                weightDesc
        );

        configureTensor(
                TensorDescription.newBuilder().build(),
                biasDesc
        );

        configureTensor(
                TensorDescription.newBuilder().build(),
                outputDesc
        );

        // resize(n * c * h * w, dstData);
        checkCUDNN(cudnnSetActivationDescriptor(activationDesc,
                CUDNN_ACTIVATION_RELU,
                CUDNN_PROPAGATE_NAN,
                0));
    }

    int cudnnDataType(DataType dtypeValue) {
        assert dtypeValue.getNumber() == DataType.DT_FLOAT_VALUE;
        return CUDNN_DATA_FLOAT;
    }

    void configureTensor(TensorDescription tdesc, cudnnTensorStruct t) {
        int dataType = cudnnDataType(tdesc.getDtype());
        List<TensorShapeProto.Dim> dimensions = tdesc.getShape().getDimList();

        long n = dimensions.get(0).getSize();
        long c = dimensions.get(1).getSize();
        long h = dimensions.get(2).getSize();
        long w = dimensions.get(3).getSize();

        int tensorFormat = CUDNN_TENSOR_NCHW;

        checkCUDNN(cudnnSetTensor4dDescriptor(t,
                tensorFormat,
                dataType,
                (int) n,
                (int) c,
                (int) h,
                (int) w)
        );
    }

    /*
    public BytePointer call(FloatPointer srcData) {

        FloatPointer alpha = new FloatPointer(1.0f);
        FloatPointer beta = new FloatPointer(1.0f);

        int input_x = 0;
        int input_y = 0;
        int input_memory_d;
        int incx_stride = 1;
        int incy_stide = 1;

        checkCUDNN(cublasSgemv_v2(s.cublasHandle, CUBLAS_OP_T,
                input_x, input_y,
                alpha,
                input_memory_d, input_x,
                srcData, incx_stride,
                beta,
                dstData, incy_stide));

        checkCUDNN(cudnnAddTensor(s.cudnnHandle,
                alpha,
                biasDesc,
                biasDesc,
                beta,
                outputDesc,
                output
        ));

        // Pinters to scaling factors in host memory used to blend the computation result with prior value in the output layer as follows
        // alpha[0]* result + beta[0]*priorDstValue;
        alpha = new FloatPointer(1.0f);
        beta = new FloatPointer(0.0f);
        checkCUDNN(cudnnActivationForward(s.cudnnHandle,
                activationDesc,
                alpha,
                activationDesc,
                srcData,
                beta,
                activationDesc,
                dstData));

        return result;
    }*/

    public void deallocate() {
        checkCUDNN(cudnnDestroyTensorDescriptor(biasDesc));
        checkCUDNN(cudnnDestroyTensorDescriptor(weightDesc));
        checkCUDNN(cudnnDestroyTensorDescriptor(outputDesc));

        checkCUDNN(cudnnDestroyActivationDescriptor(activationDesc));
    }
}
