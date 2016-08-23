package hex.deepwater.backends.cudnn;

import org.bytedeco.javacpp.FloatPointer;

import static org.bytedeco.javacpp.cuda.cudaDeviceSynchronize;
import static org.bytedeco.javacpp.cuda.cudaFree;
import static org.bytedeco.javacpp.cuda.cudaMalloc;
import static org.bytedeco.javacpp.cuda.cudaMemcpy;
import static org.bytedeco.javacpp.cuda.cudaMemcpyDeviceToHost;
import static org.bytedeco.javacpp.cudnn.*;

import static org.bytedeco.javacpp.cuda.cudaDeviceReset;
import static org.bytedeco.javacpp.cudnn.CUDNN_STATUS_SUCCESS;

/**
 * Created by fmilo on 8/19/16.
 */
public class cudnnUtils {

    static void resize(int size, FloatPointer data) {
        if (!data.isNull()) {
            checkCudaErrors( cudaFree(data) );
        }
        checkCudaErrors( cudaMalloc(data, size * 32/8) );
    }

    static void FatalError(String s) {
        System.err.println(s);
        Thread.dumpStack();
        System.err.println("Aborting...");
        cudaDeviceReset();
        System.exit(-1);
    }

    static void checkCUDNN(int status) {
        if (status != CUDNN_STATUS_SUCCESS) {
            FatalError("CUDNN failure: " + status);
        }
    }

    static void checkCudaErrors(int status) {
        if (status != 0) {
            FatalError("Cuda failure: " + status);
        }
    }

    static void printDeviceVector(int size, FloatPointer vec_d) {
        FloatPointer vec = new FloatPointer(size);
        cudaDeviceSynchronize();
        cudaMemcpy(vec, vec_d, size * 8, cudaMemcpyDeviceToHost);
        for (int i = 0; i < size; i++) {
            System.out.print(vec.get(i) + " ");
        }
        System.out.println();
    }
}
