package hex.schemas;

import hex.example.Example;
import hex.example.ExampleModel;
import hex.schemas.ExampleHandler.ExamplePojo;
import water.Key;
import water.api.*;
import water.fvec.Frame;
import water.util.BeanUtils;
import water.util.DocGen.HTML;

import java.util.Properties;

public class ExampleV2 extends Schema<ExamplePojo,ExampleV2> {

  // Input fields
  @API(help="Model builder parameters.")
  ExampleV2Parameters parameters;

  // Output fields
  @API(help = "Job Key")
  Key job;


  public static final class ExampleV2Parameters extends ModelParametersBase<ExampleModel.ExampleParameters, ExampleV2Parameters> {
    // Input fields
    @API(help="Input source frame",required=true)
    public Key src;

    @API(help="Max Iterations")
    public int max_iters;

    // TODO: refactor into ModelParametersBase
    @Override public ExampleModel.ExampleParameters createImpl() {
      ExampleModel.ExampleParameters impl = new ExampleModel.ExampleParameters();
      BeanUtils.copyProperties(impl, this, BeanUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    // TODO: refactor into ModelParametersBase
    @Override public ExampleV2Parameters fillFromImpl(ExampleModel.ExampleParameters parms) {
      BeanUtils.copyProperties(this, parms, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }
  } // ExampleV2Parameters


  //==========================
  // Custom adapters go here

  // TODO: move into builder superclass:
  public ExampleV2 fillFromParms(Properties parms) {
    this.parameters = new ExampleV2Parameters();
    this.parameters.fillFromParms(parms);
    return this;
  }

  // Version&Schema-specific filling into the impl
  @Override public ExamplePojo createImpl() {
    if( parameters.max_iters < 0 || parameters.max_iters > 9999999 ) throw new IllegalArgumentException("1<= max_iters && max_iters < 10000000");
    if( parameters.max_iters == 0 ) parameters.max_iters = 1000; // Default is 1000 max_iters

    ExamplePojo e = new ExamplePojo();
    e._parms = parameters.createImpl();
    return e;
  }

  // Version&Schema-specific filling from the impl
  @Override public ExampleV2 fillFromImpl( ExamplePojo e ) {
    parameters = new ExampleV2Parameters();
    parameters.fillFromImpl(e._parms);

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
