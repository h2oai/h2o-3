package water.api;

public class RemoveAllV1 extends Schema<RemoveAllHandler.RemoveAll,RemoveAllV1> {
  @Override public RemoveAllHandler.RemoveAll createImpl() { return new RemoveAllHandler.RemoveAll(); }
  @Override public RemoveAllV1 fillFromImpl(RemoveAllHandler.RemoveAll u) { return this; }
}