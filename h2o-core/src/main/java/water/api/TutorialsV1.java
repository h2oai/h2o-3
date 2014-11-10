package water.api;

import water.api.TutorialsHandler.Tutorials;
import water.util.DocGen.HTML;

class TutorialsV1 extends Schema<Tutorials,TutorialsV1> {
  // This Schema has no inputs
  // This Schema has no outputs

  //==========================
  // Custom adapters go here

  @Override public HTML writeHTML_impl( HTML ab ) { return ab.p(Tutorials.HTML); }
}
