package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableBase;

public class KMeansModelV3 extends ModelSchema<KMeansModel, KMeansModelV3, KMeansModel.KMeansParameters, KMeansV3.KMeansParametersV3, KMeansModel.KMeansOutput, KMeansModelV3.KMeansModelOutputV3> {

  public static final class KMeansModelOutputV3 extends ModelOutputSchema<KMeansModel.KMeansOutput, KMeansModelOutputV3> {
    // Output fields; input fields are in the parameters list
    @API(help="Cluster Centers[k][features]")
    public TwoDimTableBase centers;

    @API(help="Cluster Centers[k][features] on Standardized Data")
    public TwoDimTableBase centers_std;

    @Override public KMeansModelOutputV3 fillFromImpl(KMeansModel.KMeansOutput impl) {
      KMeansModelOutputV3 kmv3 = super.fillFromImpl(impl);
      kmv3.centers = new TwoDimTableBase().fillFromImpl(KMeans.createCenterTable(impl, false));
      if (impl._centers_std_raw != null)
        kmv3.centers_std = new TwoDimTableBase().fillFromImpl(KMeans.createCenterTable(impl, true));
      return kmv3;
    }

  } // KMeansModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public KMeansV3.KMeansParametersV3 createParametersSchema() { return new KMeansV3.KMeansParametersV3(); }
  public KMeansModelOutputV3 createOutputSchema() { return new KMeansModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public KMeansModel createImpl() {
    KMeansModel.KMeansParameters parms = parameters.createImpl();
    return new KMeansModel( model_id.key(), parms, null );
  }
}
