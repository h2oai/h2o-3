package hex.deepwater;

import hex.ModelMojo;

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
    writeln("catOffsets = " + Arrays.toString(_output._catOffsets));
    writeln("normMul = " + Arrays.toString(_output._normMul));
    writeln("normSub = " + Arrays.toString(_output._normSub));
    writeln("useAllFactorLevels = " + (_output._useAllFactorLevels ? "true" : "false"));
  }

  @Override
  protected void writeModelData() throws IOException {
    writeBinaryFile("model.network", _model_info._network);
    writeBinaryFile("model.params", _model_info._modelparams);
    if (_parms._problem_type == DeepWaterParameters.ProblemType.image_classification) {
      String meanImage = _parms._mean_image_file;
      if (meanImage!=null) {
//        writeBinaryFile("meandata", null); //TODO FIXME
      }
    }
  }

}
