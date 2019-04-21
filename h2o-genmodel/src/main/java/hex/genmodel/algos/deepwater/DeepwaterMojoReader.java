package hex.genmodel.algos.deepwater;

import deepwater.backends.BackendParams;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;
import hex.genmodel.ModelMojoReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;


/**
 */
public class DeepwaterMojoReader extends ModelMojoReader<DeepwaterMojoModel> {

  @Override
  public String getModelName() {
    return "Deep Water";
  }

  @Override
  protected void readModelData() throws IOException {
    try {
      _model._network = readblob("model_network");
      _model._parameters = readblob("model_params");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    _model._backend = DeepwaterMojoModel.createDeepWaterBackend((String) readkv("backend")); // new ImageTrain(_width, _height, _channels, _deviceID, (int)parameters.getOrMakeRealSeed(), _gpu);
    if (_model._backend == null) {
      throw new IllegalArgumentException("Couldn't instantiate the Deep Water backend.");
    }
    _model._problem_type = readkv("problem_type");
    _model._mini_batch_size = readkv("mini_batch_size");
    _model._height = readkv("height");
    _model._width = readkv("width");
    _model._channels = readkv("channels");
    _model._nums = readkv("nums");
    _model._cats = readkv("cats");
    _model._catOffsets = readkv("cat_offsets");
    _model._normMul = readkv("norm_mul");
    _model._normSub = readkv("norm_sub");
    _model._normRespMul = readkv("norm_resp_mul");
    _model._normRespSub = readkv("norm_resp_sub");
    _model._useAllFactorLevels = readkv("use_all_factor_levels");

    _model._imageDataSet = new ImageDataSet(_model._width, _model._height, _model._channels, _model._nclasses);

    _model._opts = new RuntimeOptions();
    _model._opts.setSeed(0); // ignored - not needed during scoring
    _model._opts.setUseGPU((boolean)readkv("gpu"));
    _model._opts.setDeviceID((int[])readkv("device_id"));

    _model._backendParams = new BackendParams();
    _model._backendParams.set("mini_batch_size", 1);

    File file = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString() + ".json");
    try {
      FileOutputStream os = new FileOutputStream(file.toString());
      os.write(_model._network);
      os.close();
      _model._model = _model._backend.buildNet(_model._imageDataSet, _model._opts, _model._backendParams, _model._nclasses, file.toString());
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (file!=null)
        _model._backend.deleteSavedModel(file.toString());
    }
    // 1) read the raw bytes of the mean image file from the MOJO
    byte[] meanBlob;
    try {
      meanBlob = readblob("mean_image_file"); //throws exception if not found
      // 2) write the mean image file
      File meanFile = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString() + ".mean");
      try {
        FileOutputStream os = new FileOutputStream(meanFile.toString());
        os.write(meanBlob);
        os.close();
        // 3) tell the backend to use that mean image file (just in case it needs it)
        _model._imageDataSet.setMeanData(_model._backend.loadMeanImage(_model._model, meanFile.toString()));
        // 4) keep a float[] version of the mean array to be used during image processing
        _model._meanImageData = _model._imageDataSet.getMeanData();
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        if (meanFile!=null)
          meanFile.delete();
      }
    } catch (IOException e) {
      // e.printStackTrace();
    }

    file = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
    try {
      _model._backend.writeBytes(file, _model._parameters);
      _model._backend.loadParam(_model._model, file.toString());
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (file!=null)
        _model._backend.deleteSavedParam(file.toString());
    }
  }

  @Override
  protected DeepwaterMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new DeepwaterMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() {
    return "1.00";
  }
}
