package water.api;

import water.*;
import water.api.TutorialsHandler.Tutorials;
import water.util.DocGen.HTML;

class TutorialsV1 extends Schema<Tutorials,TutorialsV1> {
  // This Schema has no inputs
  // This Schema has no outputs

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public Tutorials createImpl() {
    return new Tutorials();                // No fields to fill
  }

  // Version&Schema-specific filling from the handler
  @Override public TutorialsV1 fillFromImpl(Tutorials t) {
    return this;                // No fields to fill
  }

  @Override public HTML writeHTML_impl( HTML ab ) { return ab.p(Tutorials.HTML); }
}
