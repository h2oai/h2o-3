package water.codegen.java;

import hex.genmodel.annotations.CG;
import hex.kmeans.KMeansModel;

import static water.codegen.java.JCodeGenUtil.s;
import static water.codegen.java.JCodeGenUtil.VALUE;


/**
 * Created by michal on 3/21/16.
 */
public class KMeansModelJCodeGen extends ModelCodeGenerator<KMeansModelJCodeGen, KMeansModel> {

  protected KMeansModelJCodeGen(KMeansModel model) {
    super(model);
  }

  @Override
  protected KMeansModelJCodeGen buildImpl(CompilationUnitGenerator cucg, ClassCodeGenerator ccg) {
    // Inject all fields generators
    ccg.withMixin(model, KMeansModelMixin.class);

    // Get reference to score0 method
    MethodCodeGenerator score0Method = ccg.method("score0");

    if (model._parms._standardize) {
      // Generate additional fields
      ccg.field("CENTERS")
          .withComment("Normalized cluster centers[K][features]")
          .withValue(VALUE(model._output._centers_std_raw));

      // Generate score0 method body
      score0Method.withBody(
          s("preds[0] = KMeans_closest(CENTERS, data, DOMAINS, MEANS, MULTS);").nl()
          .p("return preds;").nl()
      );
    } else {
      ccg.field("CENTERS")
          .withComment("Denormalized cluster centers[K][features]")
          .withValue(VALUE(model._output._centers_raw));

      // Generate score0 method body
      score0Method.withBody(
              s("preds[0] = KMeans_closest(CENTERS, data, DOMAINS, null, null);").nl()
                .p("return preds;").nl()
      );
    }

    return self();
  }
}

