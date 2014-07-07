package hex.schemas;

import hex.example.Example;
import hex.example.ExampleModel;
import hex.schemas.ExampleHandler.ExamplePojo;
import water.Key;
import water.api.*;
import water.fvec.Frame;
import water.util.DocGen.HTML;

public class ExampleV2 extends Schema<ExamplePojo,ExampleV2> {

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
  @Override public ExamplePojo createImpl() {
    ExamplePojo e = new ExamplePojo();
    e._parms = new ExampleModel.ExampleParameters();
    e._parms._src = src;

    if( max_iters < 0 || max_iters > 9999999 ) throw new IllegalArgumentException("1<= max_iters && max_iters < 10000000");
    if( max_iters==0 ) max_iters = 1000; // Default is 1000 max_iters
    e._parms._max_iters = max_iters;

    return e;
  }

  // Version&Schema-specific filling from the handler
  @Override public ExampleV2 fillFromImpl( ExamplePojo e ) {
    job = e._job._key;
    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("Example Started");
    String url = JobV2.link(job);
    return ab.href("Poll",url,url);
  }

  // Return a URL to invoke Example on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/Example?src="+fr._key; }
}
