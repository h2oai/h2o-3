package water;

// Helper to fetch classForName strings from IDs from the leader
class FetchClazz extends DTask<FetchClazz> {
  private final int _id;

  // OUT
  String _clazz;

  private FetchClazz(int id) {
    super(H2O.FETCH_ACK_PRIORITY);
    _id=id; 
  }

  /**
   * Fetch class name for a given id from the leader
   * @param id class id
   * @return class name or null if leader doesn't have the id mapping
   */
  static String fetchClazz(int id) {
    return fetchClazz(H2O.CLOUD.leader(), id);
  }

  private static String fetchClazz(H2ONode node, int id) {
    return RPC.call(node, new FetchClazz(id)).get()._clazz;
  }

  @Override public void compute2() { 
    _clazz = TypeMap.classNameLocal(_id);
    tryComplete();
  }

}
