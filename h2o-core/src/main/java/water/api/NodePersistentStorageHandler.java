package water.api;

import water.H2O;
import water.init.NodePersistentStorage;
import water.init.NodePersistentStorage.NodePersistentStorageEntry;

import java.util.UUID;

public class NodePersistentStorageHandler extends Handler {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 put(int version, NodePersistentStorageV1 s) {
    NodePersistentStorage nps = H2O.getNPS();
    UUID uuid = java.util.UUID.randomUUID();
    s.name = uuid.toString();
    nps.put(s.category, s.name, s.value);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 put_with_name(int version, NodePersistentStorageV1 s) {
    NodePersistentStorage nps = H2O.getNPS();
    nps.put(s.category, s.name, s.value);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 get_as_string(int version, NodePersistentStorageV1 s) {
    NodePersistentStorage nps = H2O.getNPS();
    s.value = nps.get_as_string(s.category, s.name);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 list(int version, NodePersistentStorageV1 s) {
    NodePersistentStorage nps = H2O.getNPS();
    NodePersistentStorageEntry[] entries = nps.list(s.category);
    String[] arr = new String[entries.length];
    int i = 0;
    s.names = arr;
    for (NodePersistentStorageEntry entry : entries) {
      s.names[i] = entry._name;
      i++;
    }
    // s.list = nps.list(s.category);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 delete(int version, NodePersistentStorageV1 s) {
    NodePersistentStorage nps = H2O.getNPS();
    nps.delete(s.category, s.name);
    return s;
  }
/*
  public static class APIPojoListEntryV1 extends Schema<NodePersistentStorageEntry, APIPojoListEntryV1> {
    @API(help="Key name", required=true, direction=API.Direction.OUTPUT)
    String name;

    @API(help="Size in bytes of value", required=true, direction=API.Direction.OUTPUT)
    long size;

    @API(help="Epoch time in milliseconds of when the value was written", required=true, direction=API.Direction.OUTPUT)
    long timestamp_millis;
  }
*/
}
