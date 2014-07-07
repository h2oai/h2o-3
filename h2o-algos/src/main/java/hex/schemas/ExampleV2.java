package hex.schemas;

import hex.example.Example;
import hex.example.ExampleModel;
import water.Key;
import water.api.API;
import water.api.JobPollV2;
import water.api.Schema;
import water.fvec.Frame;
import water.util.DocGen.HTML;

public class ExampleV2 extends Schema<ExampleHandler,ExampleV2> {

  // Input fields
  @API(help="Input source frame",required=true)
  public Key src;

  @API(help="Max Iterations")
  public int max_iters;

  @API(help="")
  double[] maxs;

  // Output fields
  @API(help="Job Key")
  Key job;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public ExampleV2 fillInto( ExampleHandler h ) {
    h._parms = new ExampleModel.ExampleParameters();
    h._parms._src = src;

    if( max_iters < 0 || max_iters > 9999999 ) throw new IllegalArgumentException("1<= max_iters && max_iters < 10000000");
    if( max_iters==0 ) max_iters = 1000; // Default is 1000 max_iters
    h._parms._max_iters = max_iters;

    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override public ExampleV2 fillFrom( ExampleHandler h ) {
    job = h._job._key;
    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("Example Started");
    String url = JobPollV2.link(job);
    return ab.href("Poll",url,url);
  }

  // Return a URL to invoke Example on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/Example?src="+fr._key; }
}
