package hex.genmodel.algos.glm;

import com.google.gson.JsonObject;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.ModelAttributesGLM;
import hex.genmodel.attributes.ModelJsonReader;

import java.io.IOException;

public class GlmMojoReader extends ModelMojoReader<GlmMojoModelBase> {

  @Override
  public String getModelName() {
    return "Generalized Linear Model";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._useAllFactorLevels = readkv("use_all_factor_levels", false);

    _model._cats = readkv("cats", -1);
    _model._catModes = readkv("cat_modes", new int[0]);
    _model._catOffsets = readkv("cat_offsets", new int[0]);

    _model._nums = readkv("nums", -1);
    _model._numMeans = readkv("num_means", new double[0]);
    _model._meanImputation = readkv("mean_imputation", false);

    _model._beta = readkv("beta");
    _model._family = readkv("family");
    _model._dispersion_estimated = readkv("dispersion_estimated", 1.0);

    if (_model instanceof GlmMojoModel) {
      GlmMojoModel m = (GlmMojoModel) _model;
      m._link = readkv("link");
      m._tweedieLinkPower = readkv("tweedie_link_power", 0.0);
    }
    
    _model.init();
  }

  @Override
  protected ModelAttributes readModelSpecificAttributes() {
    final JsonObject modelJson = ModelJsonReader.parseModelJson(_reader);
    if(modelJson != null) {
      return new ModelAttributesGLM(_model, modelJson);
    } else {
      return null;
    }
  }

  @Override
  protected GlmMojoModelBase makeModel(String[] columns, String[][] domains, String responseColumn) {
    String family = readkv("family");
    if ("multinomial".equals(family))
      return new GlmMultinomialMojoModel(columns, domains, responseColumn);
    else if ("ordinal".equals(family))
      return new GlmOrdinalMojoModel(columns, domains, responseColumn);
    else
      return new GlmMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() {
    return "1.00";
  } // add support to offset

}
