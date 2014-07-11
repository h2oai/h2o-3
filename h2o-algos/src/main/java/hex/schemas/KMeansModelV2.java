package hex.schemas;

import hex.kmeans.KMeansModel;
import water.DKV;
import water.H2O;
import water.Key;
import water.api.*;
import water.api.Handler;
import water.api.ModelBase;
import water.fvec.Frame;
import water.util.BeanUtils;
//import water.util.DocGen.HTML;

public class KMeansModelV2 extends ModelBase<KMeansModel, KMeansModel.KMeansParameters, KMeansModel.KMeansOutput, KMeansModelV2> {

  // TODO: move this?
  // Input fields
  @API(help="KMeans Model to inspect",required=true)
  Key key;


  public static final class KMeansModelV2Output extends ModelOutputBase<KMeansModel.KMeansOutput, KMeansModelV2Output> {
    // Output fields
    @API(help="Clusters[K][features]")
    public double[/*K*/][/*features*/] clusters;

    @API(help="Rows[K]")
    public long[/*K*/]  rows;

    @API(help="Mean Square Error per cluster")
    public double[/*K*/] mses;   // Per-cluster MSE, variance

    @API(help="Mean Square Error")
    public double mse;           // Total MSE, variance

    @API(help="Iterations executed")
    public double iters;

    @Override public KMeansModel.KMeansOutput createImpl() {
      KMeansModel.KMeansOutput impl = new KMeansModel.KMeansOutput();
      BeanUtils.copyProperties(impl, this, BeanUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    // Version&Schema-specific filling from the handler
    @Override public KMeansModelV2Output fillFromImpl( KMeansModel.KMeansOutput impl) {
      // TODO: Weh?
      // if( !(h instanceof InspectHandler) ) throw H2O.unimpl();
      // InspectHandler ih = (InspectHandler)h;
      // KMeansModel kmm = ih._val.get();
      BeanUtils.copyProperties(this, impl, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }


  } // KMeansModelV2Output


  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the handler
  @Override public KMeansModel createImpl() {
    KMeansV2.KMeansV2Parameters p = ((KMeansV2.KMeansV2Parameters)this.parameters);
    KMeansModel.KMeansParameters parms = p.createImpl();
    return new KMeansModel( key, (Frame)DKV.get(p.src).get(), parms, 0 );
  }

  // Version&Schema-specific filling from the handler
  @Override public KMeansModelV2 fillFromImpl( KMeansModel kmm ) {
    parameters = new KMeansV2.KMeansV2Parameters();
    parameters.fillFromImpl(kmm._parms);

    output = new KMeansModelV2.KMeansModelV2Output();
    output.fillFromImpl(kmm._output);
    return this;
  }
}
