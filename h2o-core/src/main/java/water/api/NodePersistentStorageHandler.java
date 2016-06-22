package water.api;

import water.H2O;
import water.api.schemas3.NodePersistentStorageV3;
import water.init.NodePersistentStorage;
import water.init.NodePersistentStorage.NodePersistentStorageEntry;

import java.util.UUID;

public class NodePersistentStorageHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV3 configured(int version, NodePersistentStorageV3 s) {
    NodePersistentStorage nps = H2O.getNPS();
    s.configured = nps.configured();
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV3 exists(int version, NodePersistentStorageV3 s) {
    NodePersistentStorage nps = H2O.getNPS();
    if (s.name != null) {
      s.exists = nps.exists(s.category, s.name);
    }
    else {
      s.exists = nps.exists(s.category);
    }
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV3 put(int version, NodePersistentStorageV3 s) {
    NodePersistentStorage nps = H2O.getNPS();
    UUID uuid = java.util.UUID.randomUUID();
    s.name = uuid.toString();
    nps.put(s.category, s.name, s.value);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV3 put_with_name(int version, NodePersistentStorageV3 s) {
    NodePersistentStorage nps = H2O.getNPS();
    nps.put(s.category, s.name, s.value);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV3 get_as_string(int version, NodePersistentStorageV3 s) {
    NodePersistentStorage nps = H2O.getNPS();
    s.value = nps.get_as_string(s.category, s.name);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV3 list(int version, NodePersistentStorageV3 s) {
    NodePersistentStorage nps = H2O.getNPS();
    NodePersistentStorageEntry[] entries = nps.list(s.category);

    s.entries = new NodePersistentStorageV3.NodePersistentStorageEntryV3[entries.length];
    int i = 0;
    for (NodePersistentStorageEntry entry : entries) {
      NodePersistentStorageV3.NodePersistentStorageEntryV3 e = new NodePersistentStorageV3.NodePersistentStorageEntryV3();
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
  public NodePersistentStorageV3 delete(int version, NodePersistentStorageV3 s) {
    NodePersistentStorage nps = H2O.getNPS();
    nps.delete(s.category, s.name);
    return s;
  }

}
