package hex.genmodel.algos.deepwater;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.BackendTrain;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.deepwater.caffe.DeepwaterCaffeBackend;
import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowToRawDataConverter;

import java.io.File;
import java.util.Map;

public class DeepwaterMojoModel extends MojoModel {
  public String _problem_type;
  public int _mini_batch_size;
  public int _height;
  public int _width;
  public int _channels;

  public int _nums;
  public int _cats;
  public int[] _catOffsets;
  public double[] _normMul;
  public double[] _normSub;
  public double[] _normRespMul;
  public double[] _normRespSub;
  public boolean _useAllFactorLevels;

  transient byte[] _network;
  transient byte[] _parameters;
  public transient float[] _meanImageData;

  BackendTrain _backend; //interface provider
  BackendModel _model;  //pointer to C++ process
  ImageDataSet _imageDataSet; //interface provider
  RuntimeOptions _opts;
  BackendParams _backendParams;

  DeepwaterMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }


  /**
   * Corresponds to `hex.DeepWater.score0()`
   */
  @Override
  public final double[] score0(double[] doubles, double offset, double[] preds) {
    assert(doubles != null) : "doubles are null";
    float[] floats;
    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    if (_nums > 0) {
      floats = new float[_nums + cats]; //TODO: use thread-local storage
      GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, _normMul, _normSub, _useAllFactorLevels, true);
    } else {
      floats = new float[doubles.length];
      for (int i=0; i<floats.length; ++i) {
        floats[i] = (float) doubles[i] - (_meanImageData == null ? 0 : _meanImageData[i]);
      }
    }
    float[] predFloats = _backend.predict(_model, floats);
    assert(_nclasses == predFloats.length) : "nclasses " + _nclasses + " predFloats.length " + predFloats.length;
    if (_nclasses > 1) {
      for (int i = 0; i < predFloats.length; ++i)
        preds[1 + i] = predFloats[i];
      if (_balanceClasses)
        GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
      preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, doubles, _defaultThreshold);
    } else {
      if (_normRespMul!=null && _normRespSub!=null)
        preds[0] = predFloats[0] * _normRespMul[0] + _normRespSub[0];
      else
        preds[0] = predFloats[0];
    }
    return preds;
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  static public BackendTrain createDeepWaterBackend(String backend) {
    try {
      // For Caffe, only instantiate if installed at the right place
      File f = new File(DeepwaterCaffeBackend.CAFFE_H2O_DIR);
      if (backend.equals("caffe") && f.exists() && f.isDirectory())
        return new DeepwaterCaffeBackend();
      if (backend.equals("mxnet"))
        backend="deepwater.backends.mxnet.MXNetBackend";
      else if (backend.equals("tensorflow"))
        backend = "deepwater.backends.tensorflow.TensorflowBackend";
//      else if (backend.equals("xgrpc"))
//        backend="deepwater.backends.grpc.XGRPCBackendTrain";
      return (BackendTrain) (Class.forName(backend).newInstance());
    } catch (Exception ignored) {
      //ignored.printStackTrace();
    }
    return null;
  }

  @Override
  public RowToRawDataConverter makeDefaultRowConverter(Map<String, Integer> columnToOffsetIdx,
                                                       Map<Integer, CategoricalEncoder> offsetToEncoder,
                                                       EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                                                       EasyPredictModelWrapper.Config config) {
    if (_problem_type.equals("image"))
      return new DWImageConverter(this, columnToOffsetIdx, offsetToEncoder, errorConsumer, config);
    else if (_problem_type.equals("text")) {
      return new DWTextConverter(columnToOffsetIdx, offsetToEncoder, errorConsumer, config);
    }
    //fallback to default
    return super.makeDefaultRowConverter(columnToOffsetIdx, offsetToEncoder, errorConsumer, config);
  }
}
