package hex.glm;

import hex.ModelMojoWriter;

import java.io.IOException;

public class GLMMojoWriter extends ModelMojoWriter<GLMModel, GLMModel.GLMParameters, GLMModel.GLMOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public GLMMojoWriter() {}

  public GLMMojoWriter(GLMModel model) {
    super(model);
  }

  @Override
  public String mojoVersion() {
    // 1.01 is emitted only for the models whose scoring actually depends on the remove_offset_effects key
    // written below. An older h2o-genmodel reports "1.00" from its own mojoVersion(), so
    // ModelMojoReader.checkMaxSupportedMojoVersion rejects those MOJOs outright instead of loading them,
    // silently ignoring the unknown key and adding the offset back into eta - a scoring difference with no
    // error. Every other GLM MOJO stays at 1.00 and remains readable by older scorers.
    // A patch-level bump, not 1.10, so that GlmMojoModelBase's `_versionSupportOffset = _mojo_version >= 1.1`
    // keeps its original meaning: 1.1 was reserved for "GLM MOJO supports offset", which is the opposite of
    // what these models want.
    return model != null && model._useRemoveOffsetEffects ? "1.01" : "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writekv("use_all_factor_levels", model._parms._use_all_factor_levels);
    writekv("cats", model.dinfo()._cats);
    writekv("cat_offsets", model.dinfo()._catOffsets);
    writekv("nums", model._output._dinfo._nums);

    boolean imputeMeans = model._parms.imputeMissing();
    writekv("mean_imputation", imputeMeans);
    if (imputeMeans) {
      writekv("num_means", model.dinfo().numNAFill());
      writekv("cat_modes", model.dinfo().catNAFill());
    }
    if (model._parms._control_variables != null && model._parms._control_variables.length > 0)
      writekv("beta", model._output.getControlValBeta(model.beta_internal().clone()));  // "The Control Variables Coefficients"
    else
      writekv("beta", model.beta_internal());  // "The Coefficients"

    // Mirror the flag GLMScore reads rather than _parms._remove_offset_effects: a model derived through
    // MakeGLMModelHandler carries its view in this field, so keying off it makes the MOJO agree with the
    // in-H2O predictions by construction. Older MOJOs have no such key and default to false in the reader.
    writekv("remove_offset_effects", model._useRemoveOffsetEffects);

    writekv("family", model._parms._family);
    writekv("link", model._parms._link);

    if (GLMModel.GLMParameters.Family.tweedie.equals(model._parms._family))
      writekv("tweedie_link_power", model._parms._tweedie_link_power);

    writekv("dispersion_estimated", (model._parms._compute_p_values ? model._parms._dispersion_estimated : 1.0));
  }

}
