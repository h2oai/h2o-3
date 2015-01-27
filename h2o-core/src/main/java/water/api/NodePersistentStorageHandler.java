package water.api;

import water.H2O;
import water.init.NodePersistentStorage;
import water.init.NodePersistentStorage.NodePersistentStorageEntry;

import java.util.UUID;

public class NodePersistentStorageHandler extends Handler {
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

    s.entries = new NodePersistentStorageV1.NodePersistentStorageEntryV1[entries.length];
    int i = 0;
    for (NodePersistentStorageEntry entry : entries) {
      NodePersistentStorageV1.NodePersistentStorageEntryV1 e = new NodePersistentStorageV1.NodePersistentStorageEntryV1();
      e.category = entry._category;
      e.name = entry._name;
      e.size = entry._size;
      e.timestamp_millis = entry._timestamp_millis;

      s.entries[i] = e;
      i++;
    }

    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 delete(int version, NodePersistentStorageV1 s) {
    NodePersistentStorage nps = H2O.getNPS();
    nps.delete(s.category, s.name);
    return s;
  }

}
