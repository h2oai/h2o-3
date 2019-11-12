package hex.glm;

import hex.ModelMojoWriter;
import hex.glm.GLMModel.GLMParameters.MissingValuesHandling;

import java.io.IOException;

public class GLMMojoWriter extends ModelMojoWriter<GLMModel, GLMModel.GLMParameters, GLMModel.GLMOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public GLMMojoWriter() {}

  public GLMMojoWriter(GLMModel model) {
    super(model);
  }

  @Override
  public String mojoVersion() {
    return "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writekv("use_all_factor_levels", model._parms._use_all_factor_levels);
    writekv("cats", model.dinfo()._cats);
    writekv("cat_offsets", model.dinfo()._catOffsets);
    writekv("nums", model._output._dinfo._nums);

    boolean imputeMeans = model._parms.missingValuesHandling().equals(MissingValuesHandling.MeanImputation);
    writekv("mean_imputation", imputeMeans);
    if (imputeMeans) {
      writekv("num_means", model.dinfo().numNAFill());
      writekv("cat_modes", model.dinfo().catNAFill());
    }

    writekv("beta", model.beta_internal());

    writekv("family", model._parms._family);
    writekv("link", model._parms._link);

    if (GLMModel.GLMParameters.Family.tweedie.equals(model._parms._family))
      writekv("tweedie_link_power", model._parms._tweedie_link_power);
  }

}
