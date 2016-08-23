package hex.deepwater;

import java.io.*;
import org.bytedeco.javacpp.*;

import static org.bytedeco.javacpp.cublas.*;
import static org.bytedeco.javacpp.cuda.*;
import static org.bytedeco.javacpp.cudnn.*;

public class MnistCudnn {
    static final int BYTES = 32/8;
    static final int IMAGE_H = 28;
    static final int IMAGE_W = 28;

    static final String first_image = "one_28x28.pgm";
    static final String second_image = "three_28x28.pgm";
    static final String third_image = "five_28x28.pgm";

    static final String conv1_bin = "conv1.bin";
    static final String conv1_bias_bin = "conv1.bias.bin";
    static final String conv2_bin = "conv2.bin";
    static final String conv2_bias_bin = "conv2.bias.bin";
    static final String ip1_bin = "ip1.bin";
    static final String ip1_bias_bin = "ip1.bias.bin";
    static final String ip2_bin = "ip2.bin";
    static final String ip2_bias_bin = "ip2.bias.bin";

    /********************************************************
     * Prints the error message, and exits
     * ******************************************************/

    static final int EXIT_FAILURE = 1;
    static final int EXIT_SUCCESS = 0;
    static final int EXIT_WAIVED = 0;

    static void FatalError(String s) {
        System.err.println(s);
        Thread.dumpStack();
        System.err.println("Aborting...");
        cudaDeviceReset();
        System.exit(EXIT_FAILURE);
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

    static String get_path(String fname, String pname) {
        return "data/" + fname;
    }

    static class Layer_t {
        int inputs = 0;
        int outputs = 0;
        // linear dimension (i.e. size is kernel_dim * kernel_dim)
        int kernel_dim = 0;
        FloatPointer[] data_h = new FloatPointer[1], data_d = new FloatPointer[1];
        FloatPointer[] bias_h = new FloatPointer[1], bias_d = new FloatPointer[1];

        Layer_t(int _inputs, int _outputs, int _kernel_dim, String fname_weights,
                String fname_bias, String pname) {
            inputs = _inputs;
            outputs = _outputs;
            kernel_dim = _kernel_dim;
            String weights_path, bias_path;
            if (pname != null) {
                weights_path = get_path(fname_weights, pname);
                bias_path = get_path(fname_bias, pname);
            } else {
                weights_path = fname_weights;
                bias_path = fname_bias;
            }
            readBinaryFile(weights_path, inputs * outputs * kernel_dim * kernel_dim,
                    data_h, data_d);
            readBinaryFile(bias_path, outputs, bias_h, bias_d);
        }

        public void release() {
            checkCudaErrors(cudaFree(data_d[0]));
        }

        private void readBinaryFile(String fname, int size, FloatPointer[] data_h, FloatPointer[] data_d) {
            try {
                FileInputStream stream = new FileInputStream(fname);
                int size_b = size * BYTES;
                byte[] data = new byte[size_b];
                if (stream.read(data) < size_b) {
                    FatalError("Error reading file " + fname);
                }
                stream.close();
                data_h[0] = new FloatPointer(new BytePointer(data));
                data_d[0] = new FloatPointer();

                checkCudaErrors(cudaMalloc(data_d[0], size_b));
                checkCudaErrors(cudaMemcpy(data_d[0], data_h[0],
                        size_b,
                        cudaMemcpyHostToDevice));
            } catch (IOException e) {
                FatalError("Error opening file " + fname);
            }
        }
    }

    static void printDeviceVector(int size, FloatPointer vec_d) {
        FloatPointer vec = new FloatPointer(size);
        cudaDeviceSynchronize();
        cudaMemcpy(vec, vec_d, size * BYTES, cudaMemcpyDeviceToHost);
        for (int i = 0; i < size; i++) {
            System.out.print(vec.get(i) + " ");
        }
        System.out.println();
    }

    static class network_t {
        int dataType = CUDNN_DATA_FLOAT;
        int tensorFormat = CUDNN_TENSOR_NCHW;
        cudnnContext cudnnHandle = new cudnnContext();
        cudnnTensorStruct srcTensorDesc = new cudnnTensorStruct(),
                dstTensorDesc = new cudnnTensorStruct(),
                biasTensorDesc = new cudnnTensorStruct();
        cudnnFilterStruct filterDesc = new cudnnFilterStruct();
        cudnnConvolutionStruct convDesc = new cudnnConvolutionStruct();
        cudnnActivationStruct activationDesc = new cudnnActivationStruct();
        cudnnPoolingStruct poolingDesc = new cudnnPoolingStruct();
        cublas.cublasContext cublasHandle = new cublas.cublasContext();

        void createHandles() {
            checkCUDNN(cudnnCreate(cudnnHandle));
            checkCUDNN(cudnnCreateTensorDescriptor(srcTensorDesc));
            checkCUDNN(cudnnCreateTensorDescriptor(dstTensorDesc));
            checkCUDNN(cudnnCreateTensorDescriptor(biasTensorDesc));
            checkCUDNN(cudnnCreateFilterDescriptor(filterDesc));
            checkCUDNN(cudnnCreateConvolutionDescriptor(convDesc));
            checkCUDNN(cudnnCreateActivationDescriptor(activationDesc));
            checkCUDNN(cudnnCreatePoolingDescriptor(poolingDesc));

            checkCudaErrors(cublasCreate_v2(cublasHandle));
        }

        void destroyHandles() {
            checkCUDNN(cudnnDestroyPoolingDescriptor(poolingDesc));
            checkCUDNN(cudnnCreateActivationDescriptor(activationDesc));
            checkCUDNN(cudnnDestroyConvolutionDescriptor(convDesc));
            checkCUDNN(cudnnDestroyFilterDescriptor(filterDesc));
            checkCUDNN(cudnnDestroyTensorDescriptor(srcTensorDesc));
            checkCUDNN(cudnnDestroyTensorDescriptor(dstTensorDesc));
            checkCUDNN(cudnnDestroyTensorDescriptor(biasTensorDesc));
            checkCUDNN(cudnnDestroy(cudnnHandle));

            checkCudaErrors(cublasDestroy_v2(cublasHandle));
        }

        public network_t() {
            createHandles();
        }

        public void release() {
            destroyHandles();
        }

        public void resize(int size, FloatPointer data) {
            if (!data.isNull()) {
                checkCudaErrors(cudaFree(data));
            }
            checkCudaErrors(cudaMalloc(data, size * BYTES));
        }

        void addBias(cudnnTensorStruct dstTensorDesc, Layer_t layer, int c, FloatPointer data) {
            checkCUDNN(cudnnSetTensor4dDescriptor(biasTensorDesc,
                    tensorFormat,
                    dataType,
                    1, c,
                    1,
                    1));
            FloatPointer alpha = new FloatPointer(1.0f);
            FloatPointer beta = new FloatPointer(1.0f);
            checkCUDNN(cudnnAddTensor(cudnnHandle,
                    alpha, biasTensorDesc,
                    layer.bias_d[0],
                    beta,
                    dstTensorDesc,
                    data));
        }

        void fullyConnectedForward(Layer_t ip,
                                   int[] n, int[] c, int[] h, int[] w,
                                   FloatPointer srcData, FloatPointer dstData) {
            if (n[0] != 1) {
                FatalError("Not Implemented");
            }
            int dim_x = c[0] * h[0] * w[0];
            int dim_y = ip.outputs;
            resize(dim_y, dstData);

            FloatPointer alpha = new FloatPointer(1.0f), beta = new FloatPointer(1.0f);
            // place bias into dstData
            checkCudaErrors(cudaMemcpy(dstData, ip.bias_d[0], dim_y * BYTES, cudaMemcpyDeviceToDevice));

            checkCudaErrors(cublasSgemv_v2(cublasHandle, CUBLAS_OP_T,
                    dim_x, dim_y,
                    alpha,
                    ip.data_d[0], dim_x,
                    srcData, 1,
                    beta,
                    dstData, 1));

            h[0] = 1;
            w[0] = 1;
            c[0] = dim_y;
        }

        void convoluteForward(Layer_t conv,
                              int[] n, int[] c, int[] h, int[] w,
                              FloatPointer srcData, FloatPointer dstData) {
            int[] algo = new int[1];

            checkCUDNN(cudnnSetTensor4dDescriptor(srcTensorDesc,
                    tensorFormat,
                    dataType,
                    n[0], c[0],
                    h[0], w[0]));

            checkCUDNN(cudnnSetFilter4dDescriptor(filterDesc,
                    dataType,
                    tensorFormat,
                    conv.outputs,
                    conv.inputs,
                    conv.kernel_dim,
                    conv.kernel_dim));

            checkCUDNN(cudnnSetConvolution2dDescriptor(convDesc,
                    // srcTensorDesc,
                    //filterDesc,
                    0, 0, // padding
                    1, 1, // stride
                    1, 1, // upscale
                    CUDNN_CROSS_CORRELATION));
            // find dimension of convolution output
            checkCUDNN(cudnnGetConvolution2dForwardOutputDim(convDesc,
                    srcTensorDesc,
                    filterDesc,
                    n, c, h, w));

            checkCUDNN(cudnnSetTensor4dDescriptor(dstTensorDesc,
                    tensorFormat,
                    dataType,
                    n[0], c[0],
                    h[0],
                    w[0]));
            checkCUDNN(cudnnGetConvolutionForwardAlgorithm(cudnnHandle,
                    srcTensorDesc,
                    filterDesc,
                    convDesc,
                    dstTensorDesc,
                    CUDNN_CONVOLUTION_FWD_PREFER_FASTEST,
                    0,
                    algo
            ));
            resize(n[0] * c[0] * h[0] * w[0], dstData);
            SizeTPointer sizeInBytes = new SizeTPointer(1);
            Pointer workSpace = new Pointer();
            checkCUDNN(cudnnGetConvolutionForwardWorkspaceSize(cudnnHandle,
                    srcTensorDesc,
                    filterDesc,
                    convDesc,
                    dstTensorDesc,
                    algo[0],
                    sizeInBytes));
            if (sizeInBytes.get(0) != 0) {
                checkCudaErrors(cudaMalloc(workSpace, sizeInBytes.get(0)));
            }
            FloatPointer alpha = new FloatPointer(1.0f);
            FloatPointer beta = new FloatPointer(0.0f);
            checkCUDNN(cudnnConvolutionForward(cudnnHandle,
                    alpha,
                    srcTensorDesc,
                    srcData,
                    filterDesc,
                    conv.data_d[0],
                    convDesc,
                    algo[0],
                    workSpace,
                    sizeInBytes.get(0),
                    beta,
                    dstTensorDesc,
                    dstData));
            addBias(dstTensorDesc, conv, c[0], dstData);
            if (sizeInBytes.get(0) != 0) {
                checkCudaErrors(cudaFree(workSpace));
            }
        }

        void poolForward(int[] n, int[] c, int[] h, int[] w,
                         FloatPointer srcData, FloatPointer dstData) {
            checkCUDNN(cudnnSetPooling2dDescriptor(poolingDesc,
                    CUDNN_POOLING_MAX,
                    CUDNN_PROPAGATE_NAN,
                    2, 2, // window
                    0, 0, // padding
                    2, 2  // stride
            ));
            checkCUDNN(cudnnSetTensor4dDescriptor(srcTensorDesc,
                    tensorFormat,
                    dataType,
                    n[0], c[0],
                    h[0],
                    w[0]));
            h[0] = h[0] / 2;
            w[0] = w[0] / 2;
            checkCUDNN(cudnnSetTensor4dDescriptor(dstTensorDesc,
                    tensorFormat,
                    dataType,
                    n[0], c[0],
                    h[0],
                    w[0]));
            resize(n[0] * c[0] * h[0] * w[0], dstData);
            FloatPointer alpha = new FloatPointer(1.0f);
            FloatPointer beta = new FloatPointer(0.0f);
            checkCUDNN(cudnnPoolingForward(cudnnHandle,
                    poolingDesc,
                    alpha,
                    srcTensorDesc,
                    srcData,
                    beta,
                    dstTensorDesc,
                    dstData));
        }

        void softmaxForward(int n, int c, int h, int w, FloatPointer srcData, FloatPointer dstData) {
            resize(n * c * h * w, dstData);

            checkCUDNN(cudnnSetTensor4dDescriptor(srcTensorDesc,
                    tensorFormat,
                    dataType,
                    n, c,
                    h,
                    w));
            checkCUDNN(cudnnSetTensor4dDescriptor(dstTensorDesc,
                    tensorFormat,
                    dataType,
                    n, c,
                    h,
                    w));
            FloatPointer alpha = new FloatPointer(1.0f);
            FloatPointer beta = new FloatPointer(0.0f);
            checkCUDNN(cudnnSoftmaxForward(cudnnHandle,
                    CUDNN_SOFTMAX_ACCURATE,
                    CUDNN_SOFTMAX_MODE_CHANNEL,
                    alpha,
                    srcTensorDesc,
                    srcData,
                    beta,
                    dstTensorDesc,
                    dstData));
        }

        void activationForward(int n, int c, int h, int w, FloatPointer srcData, FloatPointer dstData) {
            resize(n * c * h * w, dstData);
            checkCUDNN(cudnnSetTensor4dDescriptor(srcTensorDesc,
                    tensorFormat,
                    dataType,
                    n, c,
                    h,
                    w));
            checkCUDNN(cudnnSetTensor4dDescriptor(dstTensorDesc,
                    tensorFormat,
                    dataType,
                    n, c,
                    h,
                    w));
            checkCUDNN(cudnnSetActivationDescriptor(activationDesc,
                    CUDNN_ACTIVATION_RELU,
                    CUDNN_PROPAGATE_NAN,
                    0));
            FloatPointer alpha = new FloatPointer(1.0f);
            FloatPointer beta = new FloatPointer(0.0f);
            checkCUDNN(cudnnActivationForward(cudnnHandle,
                    activationDesc,
                    alpha,
                    srcTensorDesc,
                    srcData,
                    beta,
                    dstTensorDesc,
                    dstData));
        }

        public int classify_example(String fname, Layer_t conv1,
                             Layer_t conv2,
                             Layer_t ip1,
                             Layer_t ip2) {
            int[] n = new int[1], c = new int[1], h = new int[1], w = new int[1];
            FloatPointer srcData = new FloatPointer(), dstData = new FloatPointer();
            FloatPointer imgData_h = new FloatPointer(IMAGE_H * IMAGE_W);

            // load gray-scale image from disk
            System.out.println("Loading image " + fname);
            try {
                // declare a host image object for an 8-bit grayscale image
                FileInputStream oHostSrc = new FileInputStream(fname);
                int lines = 0;
                while (lines < 4) {
                    // skip header, comment, width, height, and max value
                    if (oHostSrc.read() == '\n') {
                        lines++;
                    }
                }

                // Plot to console and normalize image to be in range [0,1]
                for (int i = 0; i < IMAGE_H; i++) {
                    for (int j = 0; j < IMAGE_W; j++) {
                        int idx = IMAGE_W * i + j;
                        imgData_h.put(idx, oHostSrc.read() / 255.0f);
                    }
                }
                oHostSrc.close();
            } catch (IOException rException) {
                FatalError(rException.toString());
            }

            System.out.println("Performing forward propagation ...");

            checkCudaErrors(cudaMalloc(srcData, IMAGE_H * IMAGE_W * BYTES));
            checkCudaErrors(cudaMemcpy(srcData, imgData_h,
                    IMAGE_H * IMAGE_W * BYTES,
                    cudaMemcpyHostToDevice));

            n[0] = c[0] = 1;
            h[0] = IMAGE_H;
            w[0] = IMAGE_W;
            convoluteForward(conv1, n, c, h, w, srcData, dstData);
            poolForward(n, c, h, w, dstData, srcData);

            convoluteForward(conv2, n, c, h, w, srcData, dstData);
            poolForward(n, c, h, w, dstData, srcData);

            fullyConnectedForward(ip1, n, c, h, w, srcData, dstData);
            activationForward(n[0], c[0], h[0], w[0], dstData, srcData);

            fullyConnectedForward(ip2, n, c, h, w, srcData, dstData);
            softmaxForward(n[0], c[0], h[0], w[0], dstData, srcData);

            final int max_digits = 10;
            FloatPointer result = new FloatPointer(max_digits);
            checkCudaErrors(cudaMemcpy(result, srcData, max_digits * BYTES, cudaMemcpyDeviceToHost));
            int id = 0;
            for (int i = 1; i < max_digits; i++) {
                if (result.get(id) < result.get(i)) id = i;
            }

            System.out.println("Resulting weights from Softmax:");
            printDeviceVector(n[0] * c[0] * h[0] * w[0], srcData);

            checkCudaErrors(cudaFree(srcData));
            checkCudaErrors(cudaFree(dstData));
            return id;
        }
    }
}