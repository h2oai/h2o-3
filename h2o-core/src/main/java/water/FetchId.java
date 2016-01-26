package water;

// Helper to fetch class IDs from class Strings from the leader
class FetchId extends DTask<FetchId> {
  final String _clazz;
  int _id;
  private FetchId(String s) { super(H2O.FETCH_ACK_PRIORITY); _clazz=s; }
  static public int fetchId(String s) { return RPC.call(H2O.CLOUD.leader(), new FetchId(s)).get()._id; }
  @Override public void compute2() { _id = TypeMap.onIce(_clazz); tryComplete(); }
}
