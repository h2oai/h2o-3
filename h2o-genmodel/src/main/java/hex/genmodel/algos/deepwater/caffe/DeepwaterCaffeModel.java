package hex.genmodel.algos.deepwater.caffe;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import deepwater.backends.BackendModel;
import hex.genmodel.algos.deepwater.caffe.nano.Deepwater;
import hex.genmodel.algos.deepwater.caffe.nano.Deepwater.Cmd;

public class DeepwaterCaffeModel implements BackendModel {
  private int[] _input_shape = new int[0];
  private int[] _sizes = new int[0];        // neurons per layer
  private String[] _types = new String[0];  // layer types
  private double[] _dropout_ratios = new double[0];
  private long _seed;
  private boolean _useGPU;
  private String _graph = "";

  private Process _process;
  private static final ThreadLocal<ByteBuffer> _buffer = new ThreadLocal<>();



  public DeepwaterCaffeModel(int batch_size, int[] sizes,
                             String[] types, double[] dropout_ratios,
                             long seed, boolean useGPU) {
    _input_shape = new int[] {batch_size, 1, 1, sizes[0]};
    _sizes = sizes;
    _types = types;
    _dropout_ratios = dropout_ratios;
    _seed = seed;
    _useGPU = useGPU;

    start();
  }

  public DeepwaterCaffeModel(String graph, int[] input_shape, long seed, boolean useGPU) {
    _graph = graph;
    _input_shape = input_shape;
    _seed = seed;
    _useGPU = useGPU;

    start();
  }

  private void start() {
    if (_process == null) {
      try {
        startRegular();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      Cmd cmd = new Cmd();
      cmd.type = Deepwater.Create;
      cmd.graph = _graph;
      cmd.inputShape = _input_shape;
      cmd.solverType = "Adam";
      cmd.sizes = _sizes;
      cmd.types = _types;
      cmd.dropoutRatios = _dropout_ratios;
      cmd.learningRate = .01f;
      cmd.momentum = .99f;
      cmd.randomSeed = _seed;
      cmd.useGpu = _useGPU;
      call(cmd);
    }
  }

  public void saveModel(String model_path) {
    Cmd cmd = new Cmd();
    cmd.type = Deepwater.SaveGraph;
    cmd.path = model_path;
    call(cmd);
  }

  public void saveParam(String param_path) {
    Cmd cmd = new Cmd();
    cmd.type = Deepwater.Save;
    cmd.path = param_path;
    call(cmd);
  }

  public void loadParam(String param_path) {
    Cmd cmd = new Cmd();
    cmd.type = Deepwater.Load;
    cmd.path = param_path;
    call(cmd);
  }

  private static void copy(float[] data, byte[] buff) {
    if (data.length * 4 != buff.length)
      throw new RuntimeException();
    ByteBuffer buffer = _buffer.get();
    if (buffer == null || buffer.capacity() < buff.length) {
      _buffer.set(buffer = ByteBuffer.allocateDirect(buff.length));
      buffer.order(ByteOrder.LITTLE_ENDIAN);
    }
    buffer.clear();
    buffer.asFloatBuffer().put(data);
    buffer.get(buff);
  }

  private static void copy(float[][] buffs, Cmd cmd) {
    cmd.data = new byte[buffs.length][];
    for (int i = 0; i < buffs.length; i++) {
      cmd.data[i] = new byte[buffs[i].length * 4];
      copy(buffs[i], cmd.data[i]);
    }
  }

  public void train(float[] data, float[] label) {
    Cmd cmd = new Cmd();
    cmd.type = Deepwater.Train;
    cmd.inputShape = _input_shape;
    int len = _input_shape[0] * _input_shape[1] * _input_shape[2] * _input_shape[3];
    if (data.length != len)
      throw new RuntimeException();
    if (label.length != _input_shape[0])
      throw new RuntimeException();
    float[][] buffs = new float[][] {data, label};
    copy(buffs, cmd);
    call(cmd);
  }

  public float[] predict(float[] data) {
    Cmd cmd = new Cmd();
    cmd.type = Deepwater.Predict;
    cmd.inputShape = _input_shape;
//    int len = _input_shape[0] * _input_shape[1] * _input_shape[2] * _input_shape[3];
//    if (data.length != len)
//      throw new RuntimeException(data.length + " vs " + len);
    float[][] buffs = new float[][] {data};
    copy(buffs, cmd);
    cmd = call(cmd);
    ByteBuffer buffer = _buffer.get();
    if (buffer == null || buffer.capacity() < cmd.data[0].length) {
      _buffer.set(buffer = ByteBuffer.allocateDirect(cmd.data[0].length));
      buffer.order(ByteOrder.LITTLE_ENDIAN);
    }
    buffer.clear();
    buffer.put(cmd.data[0]);
    float[] res = new float[cmd.data[0].length / 4];
    buffer.flip();
    buffer.asFloatBuffer().get(res);
    return res;
  }

  // Debug, or if wee find a way to package Caffe without Docker
  private void startRegular() throws IOException {
    String pwd = DeepwaterCaffeBackend.CAFFE_H2O_DIR;
    ProcessBuilder pb = new ProcessBuilder("python3 backend.py".split(" "));
    pb.environment().put("PYTHONPATH", DeepwaterCaffeBackend.CAFFE_DIR + "python");
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    pb.directory(new File(pwd));
    _process = pb.start();
  }

  void close() {
    _process.destroy();
    try {
      _process.waitFor();
    } catch (InterruptedException ex) {
      // Ignore
    }
  }

  private Cmd call(Cmd cmd) {
    try {
      OutputStream stdin = _process.getOutputStream();

      int len = cmd.getSerializedSize();
      ByteBuffer buffer = ByteBuffer.allocate(4 + len);
      buffer.putInt(len);
      CodedOutputByteBufferNano ou = CodedOutputByteBufferNano.newInstance(
          buffer.array(), buffer.position(), buffer.remaining());
      cmd.writeTo(ou);
      buffer.position(buffer.position() + len);
      stdin.write(buffer.array(), 0, buffer.position());
      stdin.flush();

      InputStream stdout = _process.getInputStream();
      int read = stdout.read(buffer.array(), 0, 4);
      if (read != 4)
        throw new RuntimeException();
      buffer.position(0);
      buffer.limit(read);
      len = buffer.getInt();
      if (buffer.capacity() < len)
        buffer = ByteBuffer.allocate(len);
      buffer.position(0);
      buffer.limit(len);

      while (buffer.position() < buffer.limit()) {
        read = stdout.read(buffer.array(), buffer.position(), buffer.limit());
        buffer.position(buffer.position() + read);
      }

      Cmd res = new Cmd();
      CodedInputByteBufferNano in = CodedInputByteBufferNano.newInstance(
          buffer.array(), 0, buffer.position());
      res.mergeFrom(in);
      return res;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

