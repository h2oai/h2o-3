package hex.example;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.example.ExampleModel.ExampleOutput;
import hex.example.ExampleModel.ExampleParameters;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.util.Log;

import java.util.Arrays;

/**
 *  Example model builder... building a trivial ExampleModel
 */
public class Example extends ModelBuilder<ExampleModel,ExampleParameters,ExampleOutput> {
  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ ModelCategory.Unknown, }; }
  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; }
  // Called from Nano thread; start the Example Job on a F/J thread
  public Example( ExampleModel.ExampleParameters parms ) { super(parms); init(false); }
  @Override protected ExampleDriver trainModelImpl() { return new ExampleDriver(); }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the max_iterations. */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if( _parms._max_iterations < 1 || _parms._max_iterations > 9999999 )
      error("max_iterations", "must be between 1 and 10 million");
  }

  // ----------------------
  private class ExampleDriver extends Driver {
    @Override public void computeImpl() {
      ExampleModel model = null;
      try {
        init(true);

        // The model to be built
        model = new ExampleModel(_job._result, _parms, new ExampleModel.ExampleOutput(Example.this));
        model.delete_and_lock(_job);

        // ---
        // Run the main Example Loop
        // Stop after enough iterations
        for( ; model._output._iterations < _parms._max_iterations; model._output._iterations++ ) {
          if( stop_requested() ) break; // Stopped/cancelled

          double[] maxs = new Max().doAll(_parms.train())._maxs;

          // Fill in the model
          model._output._maxs = maxs;
          model.update(_job);   // Update model in K/V store
          _job.update(1);       // One unit of work

          StringBuilder sb = new StringBuilder();
          sb.append("Example: iter: ").append(model._output._iterations);
          Log.info(sb);
        }
      } finally {
        if( model != null ) model.unlock(_job);
      }
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
          _maxs[col] = Math.max(_maxs[col],cs[col].atd(row));
    }

    @Override public void reduce(Max that) {
      for( int col = 0; col < _maxs.length; col++ )
        _maxs[col] = Math.max(_maxs[col],that._maxs[col]);
    }
  }
}
