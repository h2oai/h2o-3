package water.api;

import water.Iced;
import water.util.DocGen;

public class UnlockKeysV2 extends Schema<Iced, UnlockKeysV2> {
    @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
      ab.p("All keys have been unlocked.");
      return ab;
    }
}
