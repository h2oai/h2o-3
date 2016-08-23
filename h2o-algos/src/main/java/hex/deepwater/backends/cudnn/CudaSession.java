package hex.deepwater.backends.cudnn;

import org.bytedeco.javacpp.cublas;
import org.bytedeco.javacpp.cudnn;
import org.tensorflow.framework.*;

import static hex.deepwater.backends.cudnn.cudnnUtils.checkCUDNN;
import static hex.deepwater.backends.cudnn.cudnnUtils.checkCudaErrors;
import static org.bytedeco.javacpp.cudnn.cudnnCreate;
import static org.bytedeco.javacpp.cudnn.cudnnDestroy;

/**
 * Created by fmilo on 8/19/16.
 */
public class CudaSession {
    protected final cublas.cublasContext cublasHandle;
    protected cudnn.cudnnContext cudnnHandle;

    public CudaSession(){
        cudnnHandle = new cudnn.cudnnContext();
        cublasHandle = new cublas.cublasContext();

        checkCUDNN(cudnnCreate(cudnnHandle));
        checkCudaErrors(cublas.cublasCreate_v2(cublasHandle));
    }

    public void deallocate(){
        checkCUDNN(cudnnDestroy(cudnnHandle));
        checkCudaErrors(cublas.cublasDestroy_v2(cublasHandle));
    }

    public int Create(GraphDef gdef){
        for (int i = 0; i < gdef.getNodeCount(); i++) {
            NodeDef node = gdef.getNode(i);
            String opName = node.getOp();
            Operation op = OpRegistry.get(opName);
        }

        // allocate all the tensors that are not by_ref
    }
}
