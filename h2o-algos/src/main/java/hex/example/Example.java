package hex.example;

import java.util.Arrays;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.Log;

/** 
 *  Example model builder... building a trivial ExampleModel
 */
public class Example extends Job<ExampleModel> {

  final ExampleModel.ExampleParameters _parms; // All the parms

  // Called from Nano thread; start the Example Job on a F/J thread
  public Example( ExampleModel.ExampleParameters parms) {
    super(Key.make("ExampleModel"),"Example",parms._max_iters/*work is max iterations*/);
    _parms = parms;
    start(new ExampleDriver());
  }

  // ----------------------
  private class ExampleDriver extends H2OCountedCompleter<ExampleDriver> {

    @Override protected void compute2() {
      Frame fr = null;
      ExampleModel model = null;
      try {
        // Fetch & read-lock source frame
        Value val = DKV.get(_parms._src);
        if( val == null ) throw new IllegalArgumentException("Missing frame "+_parms._src);
        fr = val.get();
        fr.read_lock(_key);

        // The model to be built
        model = new ExampleModel(dest(), fr, _parms);
        model.delete_and_lock(_key);

        // ---
        // Run the main Example Loop
        // Stop after enough iterations
        for( ; model._iters < _parms._max_iters; model._iters++ ) {
          if( !isRunning() ) return; // Stopped/cancelled

          double[] maxs = new Max().doAll(fr)._maxs;

          // Fill in the model; denormalized centers
          model._maxs = maxs;
          model.update(_key); // Update model in K/V store
          update(1);          // One unit of work

          StringBuilder sb = new StringBuilder();
          sb.append("Example: iter: ").append(model._iters);
          Log.info(sb);
        }

      } catch( Throwable t ) {
        t.printStackTrace();
        cancel2(t);
        throw t;
      } finally {
        if( model != null ) model.unlock(_key);
        if( fr != null ) fr.unlock(_key);
        done();                 // Job done!
      }
      tryComplete();
    }
  }


  // -------------------------------------------------------------------------
  // Find max per-column
  private static class Max extends MRTask<Max> {
    // IN

    // OUT
    double[] _maxs;

    @Override public void map(Chunk[] cs) {
      _maxs = new double[cs.length];
      Arrays.fill(_maxs,-Double.MAX_VALUE);
      for( int col = 0; col < cs.length; col++ )
        for( int row = 0; row < cs[col].len(); row++ )
          _maxs[col] = Math.max(_maxs[col],cs[col].at0(row));
    }

    @Override public void reduce(Max that) {
      for( int col = 0; col < _maxs.length; col++ )
        _maxs[col] = Math.max(_maxs[col],that._maxs[col]);
    }
  }
}
