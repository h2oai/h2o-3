package water.api;

import water.Iced;
import water.util.DocGen;

public class UnlockKeysV3 extends RequestSchema<Iced, UnlockKeysV3> {
    @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
      ab.p("All keys have been unlocked.");
      return ab;
    }
}
