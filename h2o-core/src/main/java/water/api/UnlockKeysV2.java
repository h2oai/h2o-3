package water.api;

import water.api.UnlockKeysHandler.UnlockKeys;
import water.util.DocGen;

public class UnlockKeysV2 extends Schema<UnlockKeys,UnlockKeysV2> {
    @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
      ab.p("All keys have been unlocked.");
      return ab;
    }
}
