package water;

class FetchClazzes extends DTask<FetchClazzes> {
  // OUT
  String[] _clazzes;
  private FetchClazzes() { super(); }
  public static String[] fetchClazzes() {
    String[] clazzes = RPC.call(H2O.CLOUD.leader(), new FetchClazzes()).get()._clazzes;
    assert clazzes != null;
    return clazzes;
  }
  @Override public void compute2() {
    _clazzes = TypeMap.CLAZZES;
    tryComplete();
  }
}
