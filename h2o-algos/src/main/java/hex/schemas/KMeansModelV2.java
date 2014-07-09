package hex.schemas;

import hex.kmeans.KMeansModel;
import water.H2O;
import water.Key;
import water.api.*;
import water.api.Handler;
import water.api.ModelBase;
//import water.util.DocGen.HTML;

public class KMeansModelV2 extends ModelBase<KMeansModel, KMeansModel.KMeansParameters, KMeansModel.KMeansOutput, KMeansModelV2> {
  // Input fields
  @API(help="KMeans Model to inspect",required=true)
  Key key;

  // TODO: put these into a ModelOutput class:
  // Output fields
  @API(help="Clusters[K][features]")
  double[/*K*/][/*features*/] clusters;

  @API(help="Rows[K]")
  long[/*K*/]  rows;

  @API(help="Mean Square Error per cluster")
  public double[/*K*/] mses;   // Per-cluster MSE, variance

  @API(help="Mean Square Error")
  public double mse;           // Total MSE, variance

  @API(help="Iterations executed")
  public double iters;

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the handler
  @Override public KMeansModel createImpl() {
    return (KMeansModel) this._model;
  }

  // Version&Schema-specific filling from the handler
  @Override public KMeansModelV2 fillFromImpl( KMeansModel kmm ) {
    // if( !(h instanceof InspectHandler) ) throw H2O.unimpl();
    // InspectHandler ih = (InspectHandler)h;
    // KMeansModel kmm = ih._val.get();
    clusters = kmm._output._clusters;
    rows = kmm._output._rows;
    mses = kmm._output._mses;
    mse = kmm._output._mse;
    iters = kmm._output._iters;
    return this;
  }
}
