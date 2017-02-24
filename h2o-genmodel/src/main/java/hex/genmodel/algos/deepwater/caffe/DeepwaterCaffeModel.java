package hex.genmodel.algos.deepwater.caffe;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import deepwater.backends.BackendModel;
import hex.genmodel.algos.deepwater.caffe.nano.Deepwater;
import hex.genmodel.algos.deepwater.caffe.nano.Deepwater.Cmd;

public class DeepwaterCaffeModel implements BackendModel {
  private int _batch_size;
  private int[] _sizes;                    // neurons per layer
  private String[] _types;                 // layer types
  private double[] _dropout_ratios;
  private float _learning_rate;
  private float _momentum;
  private long _seed;
  private boolean _useGPU;

  private Process _process;
  private static final ThreadLocal<ByteBuffer> _buffer = new ThreadLocal<>();

  public DeepwaterCaffeModel(int batch_size, int[] sizes, String[] types, double[] dropout_ratios, long seed, boolean useGPU) {
    _batch_size = batch_size;
    _sizes = sizes;
    _types = types;
    _dropout_ratios = dropout_ratios;
    _seed = seed;
    _useGPU = useGPU;
  }

  public void learning_rate(float val) {
    if (_process != null)
      throw new RuntimeException("Already started");
    _learning_rate = val;
  }

  public void momentum(float val) {
    if (_process != null)
      throw new RuntimeException("Already started");
    _momentum = val;
  }

  //

  private void checkStarted() {
    try {
      if (_process == null) {
//        startDocker("h2oai/deepwater:cpu");
        startDocker("h2oai/deepwater:gpu");
//        startRegular();

        Cmd cmd = new Cmd();
        cmd.type = Deepwater.Create;
        // proto.graph = _graph;  // TODO
        cmd.batchSize = _batch_size;
        cmd.solverType = "SGD";
        cmd.sizes = _sizes;
        cmd.types = _types;
        cmd.dropoutRatios = _dropout_ratios;
        cmd.learningRate = _learning_rate;
        cmd.momentum = _momentum;
        cmd.randomSeed = _seed;
        cmd.useGpu = _useGPU;
        call(cmd);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void start() {
    checkStarted();
  }

  public void saveModel(String model_path) {
    checkStarted();
    Cmd cmd = new Cmd();
    cmd.type = Deepwater.SaveGraph;
    cmd.path = model_path;
    call(cmd);
  }

  public void saveParam(String param_path) {
    checkStarted();
    Cmd cmd = new Cmd();
    cmd.type = Deepwater.Save;
    cmd.path = param_path;
    call(cmd);
  }

  public void loadParam(String param_path) {
    checkStarted();
    Cmd cmd = new Cmd();
    cmd.type = Deepwater.Load;
    cmd.path = param_path;
    call(cmd);
  }

  static void copy(float[] data, byte[] buff) {
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

  static void copy(float[][] buffs, Cmd cmd) {
    cmd.data = new byte[buffs.length][];
    for (int i = 0; i < buffs.length; i++) {
      cmd.data[i] = new byte[buffs[i].length * 4];
      copy(buffs[i], cmd.data[i]);
    }
  }

  public void train(float[] data, float[] label) {
    checkStarted();
    Cmd cmd = new Cmd();
    cmd.type = Deepwater.Train;
    if (data.length != _batch_size * _sizes[0])
      throw new RuntimeException();
    if (label.length != _batch_size)
      throw new RuntimeException();
    float[][] buffs = new float[][] {data, label};
    copy(buffs, cmd);
    call(cmd);
  }

  public float[] predict(float[] data) {
    checkStarted();
    Cmd cmd = new Cmd();
    cmd.type = Deepwater.Predict;
    if (data.length != _batch_size * _sizes[0])
      throw new RuntimeException();
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
    buffer.asFloatBuffer().get(res);
    return res;
  }

  //

  private void startDocker(String image) throws IOException {
    int uid = Integer.parseInt(new BufferedReader(new InputStreamReader(
        Runtime.getRuntime().exec("id -u").getInputStream())).readLine());
    int gid = Integer.parseInt(new BufferedReader(new InputStreamReader(
        Runtime.getRuntime().exec("id -g").getInputStream())).readLine());
    String pwd = System.getProperty("user.dir") + "/caffe";

//    String opts = "-i --rm --user " + uid + ":" + gid + " -v " + pwd + ":" + pwd + " -w " + pwd;
    String opts = "-i --user " + uid + ":" + gid + " -v " + pwd + ":" + pwd + " -w " + pwd;
    String home = System.getProperty("user.home");
    opts += " -v " + home + "/h2o-docker/caffe:/h2o-docker/caffe";
    String tmp = System.getProperty("java.io.tmpdir");
    opts += " -v " + tmp + ":" + tmp;
    String s = "nvidia-docker run " + opts + " " + image + " python /h2o-docker/caffe/backend.py";
    ProcessBuilder pb = new ProcessBuilder(s.split(" "));
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    _process = pb.start();
  }

  // Debug, or if wee find a way to package Caffe without Docker
  private void startRegular() throws IOException {
    String home = System.getProperty("user.home");
    String pwd = home + "/h2o-docker/caffe";
    ProcessBuilder pb = new ProcessBuilder("python backend.py".split(" "));
    pb.environment().put("PYTHONPATH", home + "/caffe/python");
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    pb.directory(new File(pwd));
    _process = pb.start();
  }

  void close() {
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

