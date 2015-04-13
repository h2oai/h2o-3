package hex.schemas;

import hex.kmeans.KMeansModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableBase;
//import water.util.DocGen.HTML;

public class KMeansModelV3 extends ModelSchema<KMeansModel, KMeansModelV3, KMeansModel.KMeansParameters, KMeansV3.KMeansParametersV3, KMeansModel.KMeansOutput, KMeansModelV3.KMeansModelOutputV3> {

  public static final class KMeansModelOutputV3 extends ModelOutputSchema<KMeansModel.KMeansOutput, KMeansModelOutputV3> {
    // Output fields; input fields are in the parameters list
    @API(help="Cluster Centers[k][features]")
    public TwoDimTableBase centers;

    @API(help="Cluster Centers[k][features] on Standardized Data")
    public TwoDimTableBase centers_std;
    
    @API(help="Cluster Size[k]")
    public long[/*k*/] size;

    @API(help="Within cluster Mean Square Error per cluster")
    public double[/*k*/] within_mse;   // Within-cluster MSE, variance

    @API(help="Average within cluster Mean Square Error")
    public double avg_within_ss;       // Average within-cluster MSE, variance

    @API(help="Average Mean Square Error to grand mean")
    public double avg_ss;    // Total MSE to grand mean centroid

    @API(help="Average between cluster Mean Square Error")
    public double avg_between_ss;

    @API(help="Iterations executed")
    public double iterations;

    @API(help="Number of categorical columns trained on")
    public int categorical_column_count;

  } // KMeansModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public KMeansV3.KMeansParametersV3 createParametersSchema() { return new KMeansV3.KMeansParametersV3(); }
  public KMeansModelOutputV3 createOutputSchema() { return new KMeansModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public KMeansModel createImpl() {
    KMeansModel.KMeansParameters parms = parameters.createImpl();
    return new KMeansModel( key.key(), parms, null );
  }
}
