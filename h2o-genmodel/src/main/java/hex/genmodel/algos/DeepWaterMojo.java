package hex.genmodel.algos;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.BackendTrain;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import sun.rmi.runtime.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

public class DeepWaterMojo extends MojoModel {
  final int _mini_batch_size;
  final int _height;
  final int _width;
  final int _channels;

  final int _nums;
  final int _cats;
  final int[] _catOffsets;
  final double[] _normMul;
  final double[] _normSub;
  final boolean _useAllFactorLevels;

  final protected byte[] _network;
  final protected byte[] _parameters;

  final BackendTrain _backend; //interface provider
  final BackendModel _model;  //pointer to C++ process

  final ImageDataSet _imageDataSet; //interface provider
  final RuntimeOptions _opts;
  final BackendParams _backendParams;

  public DeepWaterMojo(MojoReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
    super(cr, info, columns, domains);
    try {
      _network = _reader.getBinaryFile("model.network");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    try {
      _parameters = _reader.getBinaryFile("model.params");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    _backend = createDeepWaterBackend((String)info.get("backend")); // new ImageTrain(_width, _height, _channels, _deviceID, (int)parameters.getOrMakeRealSeed(), _gpu);
    _mini_batch_size = (int)info.get("mini_batch_size");
    _height = (int)info.get("height");
    _width = (int)info.get("width");
    _channels = (int)info.get("channels");
    _nums = (int)info.get("nums");
    _cats = (int)info.get("cats");
    _catOffsets = (int[])info.get("catOffsets");
    _normMul = (double[])info.get("normMul");
    _normSub = (double[])info.get("normSub");
    _useAllFactorLevels = (boolean)info.get("useAllFactorLevels");

    _imageDataSet = new ImageDataSet(_width, _height, _channels);
//    float[] meanData = _backend.loadMeanImage(_model, (String)info.get("mean_image_file"));
//    if(meanData.length > 0) {
//      _imageDataSet.setMeanData(meanData);
//    }

    _opts = new RuntimeOptions();
    _opts.setSeed(0); // ignored
    _opts.setUseGPU(false); // don't use a GPU for inference
    _opts.setDeviceID(0); // ignored

    _backendParams = new BackendParams();
    _backendParams.set("mini_batch_size", 1);

    Path path = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString() + ".json");
    try {
      Files.write(path, _network);
    } catch (IOException e) {
      e.printStackTrace();
    }
    _model = _backend.buildNet(_imageDataSet, _opts, _backendParams, _nclasses, path.toString());

    path = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
    try {
      Files.write(path, _parameters);
    } catch (IOException e) {
      e.printStackTrace();
    }
    _backend.loadParam(_model, path.toString());
  }

  /**
   * Corresponds to `hex.DeepWater.score0()`
   */
  @Override
  public final double[] score0(double[] doubles, double offset, double[] preds) {
    assert(doubles != null) : "doubles are null";
    float[] floats = new float[_nums + _catOffsets[_cats]]; //TODO: use thread-local storage
    GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, _normMul, _normSub, _useAllFactorLevels);
//    System.err.println(Arrays.toString(doubles));
//    System.err.println(Arrays.toString(floats));
    float[] predFloats = _backend.predict(_model, floats);
    assert(_nclasses>=2) : "Only classification is supported right now.";
    assert(_nclasses == predFloats.length) : "nclasses " + _nclasses + " predFloats.length " + predFloats.length;
    for (int i=0; i<predFloats.length; ++i) preds[1+i] = predFloats[i];
    preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, doubles, _defaultThreshold);
    return preds;
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  static public BackendTrain createDeepWaterBackend(String backend) {
    try {
      try {
        if (backend.equals("mxnet")) backend="deepwater.backends.mxnet.MXNetBackend";
        return (BackendTrain)(Class.forName(backend).newInstance());
      } catch (InstantiationException e) {
        e.printStackTrace();
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    return null;
  }
}
