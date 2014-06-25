package water.api;

import water.util.DocGen;

public class UnlockKeysV2 extends Schema<UnlockKeysHandler,UnlockKeysV2> {
    @Override protected UnlockKeysV2 fillInto(UnlockKeysHandler handler) { return this; }
    @Override public UnlockKeysV2 fillFrom(UnlockKeysHandler handler) { return this; }
    @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
      ab.p("All keys have been unlocked.");
      return ab;
    }
}
