package hex.example;

import hex.ModelBuilder;
import hex.schemas.ExampleV2;
import hex.schemas.ModelBuilderSchema;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.Log;

import java.util.Arrays;

/**
 *  Example model builder... building a trivial ExampleModel
 */
public class Example extends ModelBuilder<ExampleModel,ExampleModel.ExampleParameters,ExampleModel.ExampleOutput> {

  // Called from Nano thread; start the Example Job on a F/J thread
  public Example( ExampleModel.ExampleParameters parms) {
    super("Example",parms);
    _parms = parms;
  }

  public ModelBuilderSchema schema() { return new ExampleV2(); }

  @Override public Example train() {
    return (Example)start(new ExampleDriver(), _parms._max_iters);
  }

  // ----------------------
  private class ExampleDriver extends H2OCountedCompleter<ExampleDriver> {

    @Override protected void compute2() {
      Frame fr = null;
      ExampleModel model = null;
      try {
        // Fetch & read-lock source frame
        fr = _parms._training_frame.get();
        fr.read_lock(_key);

        // The model to be built
        model = new ExampleModel(dest(), fr, _parms, new ExampleModel.ExampleOutput());
        model.delete_and_lock(_key);

        // ---
        // Run the main Example Loop
        // Stop after enough iterations
        for( ; model._output._iters < _parms._max_iters; model._output._iters++ ) {
          if( !isRunning() ) return; // Stopped/cancelled

          double[] maxs = new Max().doAll(fr)._maxs;

          // Fill in the model; denormalized centers
          model._output._maxs = maxs;
          model.update(_key); // Update model in K/V store
          update(1);          // One unit of work

          StringBuilder sb = new StringBuilder();
          sb.append("Example: iter: ").append(model._output._iters);
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
        for( int row = 0; row < cs[col]._len; row++ )
          _maxs[col] = Math.max(_maxs[col],cs[col].at0(row));
    }

    @Override public void reduce(Max that) {
      for( int col = 0; col < _maxs.length; col++ )
        _maxs[col] = Math.max(_maxs[col],that._maxs[col]);
    }
  }
}
