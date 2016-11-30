package hex.deepwater;

import hex.ModelMojoWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static water.H2O.technote;

/**
 * Mojo definition for DeepWater model.
 */
public class DeepwaterMojoWriter extends ModelMojoWriter<DeepWaterModel, DeepWaterParameters, DeepWaterModelOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public DeepwaterMojoWriter() {}

  public DeepwaterMojoWriter(DeepWaterModel model) {
    super(model);
    _parms = model.get_params();
    _model_info = model.model_info();
    _output = model._output;
    if (_model_info._unstable) {
      throw new UnsupportedOperationException(technote(4, "Refusing to create a MOJO for an unstable model."));
    }
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

  private DeepWaterParameters _parms;
  private DeepWaterModelInfo _model_info;
  private DeepWaterModelOutput _output;

  @Override
  protected void writeModelData() throws IOException {
    writekv("backend", _parms._backend);
    writekv("problem_type", _parms._problem_type);
    writekv("mini_batch_size", _parms._mini_batch_size);
    writekv("height", _model_info._height);
    writekv("width", _model_info._width);
    writekv("channels", _model_info._channels);
    writekv("nums", _output._nums);
    writekv("cats", _output._cats);
    writekv("cat_offsets", _output._catOffsets);
    writekv("norm_mul", _output._normMul);
    writekv("norm_sub", _output._normSub);
    writekv("norm_resp_mul", _output._normRespMul);
    writekv("norm_resp_sub", _output._normRespSub);
    writekv("use_all_factor_levels", _output._useAllFactorLevels);
    writekv("gpu", _parms._gpu);
    writekv("device_id", _parms._device_id);

    writeblob("model_network", _model_info._network);
    writeblob("model_params", _model_info._modelparams);
    if (_parms._problem_type == DeepWaterParameters.ProblemType.image) {
      String meanImage = _parms._mean_image_file;
      if (meanImage != null && !meanImage.isEmpty() ) {
        byte[] data = new byte[(int)new File(meanImage).length()];
        FileInputStream is = new FileInputStream(meanImage);
        is.read(data);
        is.close();
        writeblob("mean_image_file", data);
      }
    }
  }

}
