package water;

// Helper to fetch classForName strings from IDs from the leader
class FetchClazz extends DTask<FetchClazz> {
  final int _id;
  String _clazz;
  private FetchClazz(int id) { _id=id; }
  public static String fetchClazz(int id) {
    String clazz = RPC.call(H2O.CLOUD.leader(), new FetchClazz(id)).get()._clazz;
    assert clazz != null : "No class matching id "+id;
    return clazz;
  }
  @Override public void compute2() {
    _clazz = TypeMap.className(_id); tryComplete();
  }
  @Override public byte priority() { return H2O.FETCH_ACK_PRIORITY; }
}
