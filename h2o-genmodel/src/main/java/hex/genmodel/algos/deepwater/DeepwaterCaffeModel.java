package hex.genmodel.algos.deepwater;

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

  private Process _process;

  DeepwaterCaffeModel(int batch_size, int[] sizes, String[] types, double[] dropout_ratios) {
    _batch_size = batch_size;
    _sizes = sizes;
    _types = types;
    _dropout_ratios = dropout_ratios;
  }

  void learning_rate(float val) {
    if (_process != null)
      throw new RuntimeException("Already started");
    _learning_rate = val;
  }

  void momentum(float val) {
    if (_process != null)
      throw new RuntimeException("Already started");
    _momentum = val;
  }

  //

  private void checkStarted() {
    try {
      if (_process == null) {
        startDocker("h2oai/deepwater");
//        startRegular();

        Cmd proto = new Cmd();
        proto.type = Deepwater.Create;
        // proto.graph = _graph;  // TODO
        proto.solverType = "SGD";
        proto.sizes = _sizes;
        proto.types = _types;
        proto.dropoutRatios = _dropout_ratios;
        proto.learningRate = _learning_rate;
        proto.momentum = _momentum;
        // TODO
        // proto.randomSeed = 5;

        call(proto);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void saveModel(String model_path) {
    checkStarted();
  }

  void saveParam(String param_path) {
    checkStarted();
  }

  void loadParam(String param_path) {
    checkStarted();
  }

  public void train(float[] data, float[] label) {
    checkStarted();

    Cmd cmd = new Cmd();
    cmd.type = Deepwater.Train;
    if (data.length != _batch_size * _sizes[0])
      throw new RuntimeException();
    cmd.batch = new byte[data.length * 4];
    if (label.length != _batch_size * _sizes[_sizes.length - 1])
      throw new RuntimeException();
    cmd.labels = new byte[label.length * 4];

    ByteBuffer buffer = ByteBuffer.allocateDirect(cmd.batch.length);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.get(cmd.batch);

    call(cmd);
  }

  public float[] predict(float[] data) {
    checkStarted();

    return null;
  }

  //

  private void startDocker(String image) throws IOException {
    int uid = Integer.parseInt(new BufferedReader(new InputStreamReader(
        Runtime.getRuntime().exec("id -u").getInputStream())).readLine());
    int gid = Integer.parseInt(new BufferedReader(new InputStreamReader(
        Runtime.getRuntime().exec("id -g").getInputStream())).readLine());
    String pwd = System.getProperty("user.dir") + "/caffe";

//        String cmd = "python -u test.py";
    String opts = "-i --rm --user " + uid + ":" + gid + " -v " + pwd + ":" + pwd + " -w " + pwd;
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
//      return _process.exitValue();
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
      System.out.println(len);

      while (buffer.position() < buffer.limit()) {
        read = stdout.read(buffer.array(), buffer.position(), buffer.limit());
        buffer.position(buffer.position() + read);
      }

      Cmd res = new Cmd();
      CodedInputByteBufferNano in = CodedInputByteBufferNano.newInstance(
          buffer.array(), 0, buffer.position());
      cmd.mergeFrom(in);
      return res;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

