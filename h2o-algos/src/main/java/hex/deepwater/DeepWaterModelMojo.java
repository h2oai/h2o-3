package hex.deepwater;

import hex.ModelMojo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Mojo definition for DeepWater model.
 */
public class DeepWaterModelMojo extends ModelMojo<DeepWaterModel, DeepWaterParameters, DeepWaterModelOutput> {

  public DeepWaterModelMojo(DeepWaterModel model) {
    super(model);
    _parms = model.get_params();
    _model_info = model.model_info();
    _output = model._output;
  }
  DeepWaterParameters _parms;
  DeepWaterModelInfo _model_info;
  DeepWaterModelOutput _output;

  @Override
  protected void writeExtraModelInfo() throws IOException {
    super.writeExtraModelInfo();
    writeln("backend = " + _parms._backend);
    writeln("problem_type = " + _parms._problem_type.toString());
    writeln("mini_batch_size = " + _parms._mini_batch_size);
    writeln("height = " + _model_info._height);
    writeln("width = " + _model_info._width);
    writeln("channels = " + _model_info._channels);
    writeln("nums = " + _output._nums);
    writeln("cats = " + _output._cats);
    writeln("cat_offsets = " + Arrays.toString(_output._catOffsets));
    writeln("norm_mul = " + Arrays.toString(_output._normMul));
    writeln("norm_sub = " + Arrays.toString(_output._normSub));
    writeln("use_all_factor_levels = " + (_output._useAllFactorLevels ? "true" : "false"));
  }

  @Override
  protected void writeModelData() throws IOException {
    writeBinaryFile("model_network", _model_info._network);
    writeBinaryFile("model_params", _model_info._modelparams);
    if (_parms._problem_type == DeepWaterParameters.ProblemType.image_classification) {
      String meanImage = _parms._mean_image_file;
      if (meanImage!=null) {
        byte[] data = new byte[(int)new File(meanImage).length()];
        FileInputStream is = new FileInputStream(meanImage);
        is.read(data);
        is.close();
        writeBinaryFile("mean_image_file", data);
      }
    }
  }

}
