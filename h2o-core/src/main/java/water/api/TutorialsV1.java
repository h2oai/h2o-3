package water.api;

import water.*;
import water.util.DocGen.HTML;

class TutorialsV1 extends Schema<TutorialsHandler,TutorialsV1> {
  // This Schema has no inputs
  // This Schema has no outputs


  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override protected TutorialsV1 fillInto( TutorialsHandler h ) {
    return this;                // No fields to fill
  }

  // Version&Schema-specific filling from the handler
  @Override protected TutorialsV1 fillFrom( TutorialsHandler h ) {
    return this;                // No fields to fill
  }

  @Override public HTML writeHTML_impl( HTML ab ) { return ab.p(TutorialsHandler.HTML); }
}
