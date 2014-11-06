package water.api;

import water.H2O;
import water.Quantiles;
import water.util.Log;

public class QuantilesHandler extends Handler<Quantiles,QuantilesV1> {

  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  @Override public void compute2() { throw H2O.unimpl(); }

  protected void init(Quantiles q) throws IllegalArgumentException {
    if( q.source_key == null ) throw new IllegalArgumentException("Source key is missing");
    if( q.column == null )     throw new IllegalArgumentException("Column is missing");
    if( q.column.isEnum() )    throw new IllegalArgumentException("Column is an enum");
    if(! ((q.interpolation_type == 2) || (q.interpolation_type == 7)) ) {
      throw new IllegalArgumentException("Unsupported interpolation type. Currently only allow 2 or 7");
    }
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public QuantilesV1 quantiles(int version, Quantiles q) {
    init(q);
    String[] names = new String[1];

    boolean multiPass;
    Quantiles[] qbins;

    // just take one here.
    // it's array because summary2 might use with a single pass list
    // and an exec single pass approx could pass a threshold list
    double [] quantiles_to_do = new double[1];
    quantiles_to_do[0] = q.quantile;

    double approxResult;
    double exactResult;
    q.result_single = Double.NaN;
    q.result = Double.NaN;
    boolean done = false;
    // approx (fully independent from the multipass)
    qbins = null;
    if ( q.multiple_pass == 0 || q.multiple_pass == 2 ) {
      multiPass = false;
      q.result_single = Double.NaN;
      if ( q.multiple_pass == 0) q.result = Double.NaN;

      // These are used as initial params, and setup for the next iteration
      // be sure to set again if multiple qbins are created
      double valStart = q.column.min();
      double valEnd = q.column.max();
      // quantile doesn't matter for the map/reduce binning
      qbins = new Quantiles.BinTask2(q.max_qbins, valStart, valEnd).doAll(q.column)._qbins;
      Log.debug("Q_ for approx. valStart: " + valStart + " valEnd: " + valEnd);

      // Have to get this internal state, and copy this state for the next iteration
      // in order to multipass
      // I guess forward as params to next iteration
      // while ( (iteration <= maxIterations) && !done ) {
      //  valStart   = newValStart;
      //  valEnd     = newValEnd;

      // These 3 are available for viewing, but not necessary to iterate
      //  valRange   = newValRange;
      //  valBinSize = newValBinSize;
      //  valLowCnt  = newValLowCnt;

      q.interpolation_type_used = q.interpolation_type;
      q.quantile_requested = quantiles_to_do[0];
      if ( qbins != null ) { // if it's enum it will be null?
        qbins[0].finishUp(q.column, quantiles_to_do, q.interpolation_type, multiPass);
        q.column_name = names[0]; // the string name, not the param
        q.iterations = 1;
        done = qbins[0]._done;
        approxResult = qbins[0]._pctile[0];
        q.interpolated = qbins[0]._interpolated;
      }
      else {
        q.column_name = "";
        q.iterations = 0;
        done = false;
        approxResult = Double.NaN;
        q.interpolated = false;
      }

      q.result_single = approxResult;
      // only the best result if we only ran the approx
      if ( q.multiple_pass == 0 ) q.result = approxResult;

      // if max_qbins is set to 2? hmm. we won't resolve if max_qbins = 1
      // interesting to see how we resolve (should we disallow < 1000? (accuracy issues) but good for test)
    }

    if ( q.multiple_pass == 1 || q.multiple_pass == 2 ) {
      final int MAX_ITERATIONS = 16;
      multiPass = true;
      exactResult = Double.NaN;
      double valStart = q.column.min();
      double valEnd = q.column.max();

      for (int b = 0; b < MAX_ITERATIONS; b++) {
        // we did an approximation pass above we could reuse it for the first pass here?
        // quantile doesn't matter for the map/reduce binning
        // cleaned up things so no multipass behavior in qbins..all in finishUp:w
        // so can reuse the qbins from the approx pass above (if done)
        if ( !(q.multiple_pass==2 && b==0) ) {
          qbins = new Quantiles.BinTask2(q.max_qbins, valStart, valEnd).doAll(q.column)._qbins;
        }
        q.iterations = b + 1;
        if ( qbins == null ) break;
        else {
          qbins[0].finishUp(q.column, quantiles_to_do, q.interpolation_type, multiPass);
          Log.debug("\nQ_ multipass iteration: "+q.iterations+" valStart: "+valStart+" valEnd: "+valEnd);
          double valBinSize = qbins[0]._valBinSize;
          Log.debug("Q_ valBinSize: "+valBinSize);

          valStart = qbins[0]._newValStart;
          valEnd = qbins[0]._newValEnd;
          done = qbins[0]._done;
          if ( done ) break;
        }
      }

      q.interpolation_type_used = q.interpolation_type;
      q.quantile_requested = quantiles_to_do[0];
      if ( qbins != null ) { // if it's enum it will be null?
        q.column_name = names[0]; // string name, not the param
        done = qbins[0]._done;
        exactResult = qbins[0]._pctile[0];
        q.interpolated = qbins[0]._interpolated;
      }
      else {
        // enums must come this way. Right now we don't seem
        // to create everything for the normal response, if we reject an enum col.
        // should fix that. For now, just hack it to not look for stuff
        q.column_name = "";
        q.iterations = 0;
        done = false;
        exactResult = Double.NaN;
        q.interpolated = false;
      }

      // all done with it
      qbins = null;
      // always the best result if we ran here
      q.result = exactResult;
    }
    return schema(version).fillFromImpl(q);
  }

  @Override protected QuantilesV1 schema(int version) { return new QuantilesV1(); }
}
