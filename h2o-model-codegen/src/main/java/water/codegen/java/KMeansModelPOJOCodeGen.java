package water.codegen.java;

import hex.kmeans.KMeansModel;
import water.codegen.java.mixins.KMeansModelMixin;

import static water.codegen.java.JCodeGenUtil.s;
import static water.codegen.java.JCodeGenUtil.VALUE;


/**
 * Created by michal on 3/21/16.
 */
public class KMeansModelPOJOCodeGen
    extends POJOModelCodeGenerator<KMeansModelPOJOCodeGen, KMeansModel> {

  protected KMeansModelPOJOCodeGen(KMeansModel model) {
    super(model);
  }

  @Override
  protected KMeansModelPOJOCodeGen buildImpl(CompilationUnitGenerator cucg, ClassCodeGenerator ccg) {
    // Inject all fields generators
    ccg.withMixin(model, KMeansModelMixin.class);

    if (model._parms._standardize) {
      // Generate additional fields
      ccg.field("CENTERS")
          .withComment("Normalized cluster centers[K][features]")
          .withValue(VALUE(model._output._centers_std_raw));
    } else {
      ccg.field("CENTERS")
          .withComment("Denormalized cluster centers[K][features]")
          .withValue(VALUE(model._output._centers_raw));
    }

    return self();
  }
}

