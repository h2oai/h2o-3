package water.api;

import water.Quantiles;
import water.util.Log;

public class QuantilesHandler extends Handler {

  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  protected void init(Quantiles q) throws IllegalArgumentException {
    if( q._source_key == null ) throw new IllegalArgumentException("Source key is missing");
    if( q._column == null )     throw new IllegalArgumentException("Column is missing");
    if( q._column.isEnum() )    throw new IllegalArgumentException("Column is an enum");
    if(! ((q._interpolation_type == 2) || (q._interpolation_type == 7)) ) {
      throw new IllegalArgumentException("Unsupported interpolation type. Currently only allow 2 or 7");
    }
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public QuantilesV1 quantiles(int version, QuantilesV1 s) {
    Quantiles q = s.createAndFillImpl();
    init(q);
    String[] names = new String[1];

    boolean multiPass;
    Quantiles[] qbins;

    // just take one here.
    // it's array because summary2 might use with a single pass list
    // and an exec single pass approx could pass a threshold list
    double [] quantiles_to_do = new double[1];
    quantiles_to_do[0] = q._quantile;

    double approxResult;
    double exactResult;
    q._result_single = Double.NaN;
    q._result = Double.NaN;
    boolean done = false;
    // approx (fully independent from the multipass)
    qbins = null;
    if ( q._multiple_pass == 0 || q._multiple_pass == 2 ) {
      multiPass = false;
      q._result_single = Double.NaN;
      if ( q._multiple_pass == 0) q._result = Double.NaN;

      // These are used as initial params, and setup for the next iteration
      // be sure to set again if multiple qbins are created
      double valStart = q._column.min();
      double valEnd = q._column.max();
      // quantile doesn't matter for the map/reduce binning
      qbins = new Quantiles.BinningTask(q._max_qbins, valStart, valEnd).doAll(q._column)._qbins;
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

      q._interpolation_type_used = q._interpolation_type;
      q._quantile_requested = quantiles_to_do[0];
      if ( qbins != null ) { // if it's enum it will be null?
        qbins[0].finishUp(q._column, quantiles_to_do, q._interpolation_type, multiPass);
        q._column_name = names[0]; // the string name, not the param
        q._iterations = 1;
        done = qbins[0]._done;
        approxResult = qbins[0]._pctile[0];
        q._interpolated = qbins[0]._interpolated;
      }
      else {
        q._column_name = "";
        q._iterations = 0;
        done = false;
        approxResult = Double.NaN;
        q._interpolated = false;
      }

      q._result_single = approxResult;
      // only the best result if we only ran the approx
      if ( q._multiple_pass == 0 ) q._result = approxResult;

      // if max_qbins is set to 2? hmm. we won't resolve if max_qbins = 1
      // interesting to see how we resolve (should we disallow < 1000? (accuracy issues) but good for test)
    }

    if ( q._multiple_pass == 1 || q._multiple_pass == 2 ) {
      final int MAX_ITERATIONS = 16;
      multiPass = true;
      exactResult = Double.NaN;
      double valStart = q._column.min();
      double valEnd = q._column.max();

      for (int b = 0; b < MAX_ITERATIONS; b++) {
        // we did an approximation pass above we could reuse it for the first pass here?
        // quantile doesn't matter for the map/reduce binning
        // cleaned up things so no multipass behavior in qbins..all in finishUp:w
        // so can reuse the qbins from the approx pass above (if done)
        if ( !(q._multiple_pass ==2 && b==0) ) {
          qbins = new Quantiles.BinningTask(q._max_qbins, valStart, valEnd).doAll(q._column)._qbins;
        }
        q._iterations = b + 1;
        if ( qbins == null ) break;
        else {
          qbins[0].finishUp(q._column, quantiles_to_do, q._interpolation_type, multiPass);
          Log.debug("\nQ_ multipass iteration: "+q._iterations +" valStart: "+valStart+" valEnd: "+valEnd);
          double valBinSize = qbins[0]._valBinSize;
          Log.debug("Q_ valBinSize: "+valBinSize);

          valStart = qbins[0]._newValStart;
          valEnd = qbins[0]._newValEnd;
          done = qbins[0]._done;
          if ( done ) break;
        }
      }

      q._interpolation_type_used = q._interpolation_type;
      q._quantile_requested = quantiles_to_do[0];
      if ( qbins != null ) { // if it's enum it will be null?
        q._column_name = names[0]; // string name, not the param
        done = qbins[0]._done;
        exactResult = qbins[0]._pctile[0];
        q._interpolated = qbins[0]._interpolated;
      }
      else {
        // enums must come this way. Right now we don't seem
        // to create everything for the normal response, if we reject an enum col.
        // should fix that. For now, just hack it to not look for stuff
        q._column_name = "";
        q._iterations = 0;
        done = false;
        exactResult = Double.NaN;
        q._interpolated = false;
      }

      // all done with it
      qbins = null;
      // always the best result if we ran here
      q._result = exactResult;
    }
    return s.fillFromImpl(q);
  }
}
