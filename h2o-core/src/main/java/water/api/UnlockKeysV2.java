package water.api;

import water.api.UnlockKeysHandler.UnlockKeys;
import water.util.DocGen;

public class UnlockKeysV2 extends Schema<UnlockKeys,UnlockKeysV2> {
    @Override public UnlockKeys createImpl() { return null; }
    @Override public UnlockKeysV2 fillFromImpl(UnlockKeys u ) { return this; }
    @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
      ab.p("All keys have been unlocked.");
      return ab;
    }
}
