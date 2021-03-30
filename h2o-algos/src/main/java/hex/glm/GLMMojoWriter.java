package hex.glm;

import com.sun.corba.se.spi.orb.StringPair;
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
    if(model._parms._interaction_pairs != null) {
      writekv("num_interactions", model._parms._interaction_pairs.length);
      writeStringArrays(parseInteractionPairs(model._parms._interaction_pairs), "interaction_pairs");  
    }
    
    boolean imputeMeans = model._parms.imputeMissing();
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
  
  public String[] parseInteractionPairs(hex.StringPair[] interaction_pairs) {
    String[] parsed = new String[2 * interaction_pairs.length];
    for(int i = 0; i < interaction_pairs.length; i++) {
      hex.StringPair interaction_pair = interaction_pairs[i];
      parsed[2 * i] = interaction_pair._a;
      parsed[(2 * i) + 1] = interaction_pair._b;
    }
    return parsed;
  }

}
