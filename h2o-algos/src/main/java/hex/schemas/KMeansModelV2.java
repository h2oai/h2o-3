package hex.schemas;

import hex.kmeans.KMeansModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
//import water.util.DocGen.HTML;

public class KMeansModelV2 extends ModelSchema<KMeansModel, KMeansModel.KMeansParameters, KMeansModel.KMeansOutput, KMeansModelV2> {

  public static final class KMeansModelOutputV2 extends ModelOutputSchema<KMeansModel.KMeansOutput, KMeansModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help="Clusters[k][features]")
    public double[/*k*/][/*features*/] clusters;

    @API(help="Rows[k]")
    public long[/*k*/] rows;

    @API(help="Within cluster Mean Square Error per cluster")
    public double[/*k*/] withinmse;   // Within-cluster MSE, variance

    @API(help="Average within cluster Mean Square Error")
    public double avgwithinss;       // Average within-cluster MSE, variance

    @API(help="Average Mean Square Error to grand mean")
    public double avgss;    // Total MSE to grand mean centroid

    @API(help="Average between cluster Mean Square Error")
    public double avgbetweenss;

    @API(help="Iterations executed")
    public double iters;

    @API(help="Number of categorical columns trained on")
    public int ncats;

  } // KMeansModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public KMeansV2.KMeansParametersV2 createParametersSchema() { return new KMeansV2.KMeansParametersV2(); }
  public KMeansModelOutputV2 createOutputSchema() { return new KMeansModelOutputV2(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public KMeansModel createImpl() {
    KMeansV2.KMeansParametersV2 p = ((KMeansV2.KMeansParametersV2)this.parameters);
    KMeansModel.KMeansParameters parms = p.createImpl();
    return new KMeansModel( key, parms, null );
  }
}
