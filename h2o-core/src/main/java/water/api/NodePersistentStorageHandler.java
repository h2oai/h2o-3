package water.api;

import java.util.UUID;
import water.H2O;
import water.Iced;
import water.init.NodePersistentStorage;
import water.init.NodePersistentStorage.NodePersistentStorageEntry;

public class NodePersistentStorageHandler extends Handler<NodePersistentStorageHandler.APIPojo, NodePersistentStorageHandler.NodePersistentStorageV1> {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  @Override public void compute2() { throw H2O.fail(); }
  @Override protected NodePersistentStorageV1 schema(int version) { return new NodePersistentStorageV1(); }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 put(int version, NodePersistentStorageHandler.APIPojo obj) {
    NodePersistentStorage nps = H2O.getNPS();
    UUID uuid = java.util.UUID.randomUUID();
    obj._name = uuid.toString();
    nps.put(obj._category, obj._name, obj._value);
    return this.schema(version).fillFromImpl(obj);
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 put_with_name(int version, NodePersistentStorageHandler.APIPojo obj) {
    NodePersistentStorage nps = H2O.getNPS();
    nps.put(obj._category, obj._name, obj._value);
    return this.schema(version).fillFromImpl(obj);
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 get_as_string(int version, NodePersistentStorageHandler.APIPojo obj) {
    NodePersistentStorage nps = H2O.getNPS();
    obj._value = nps.get_as_string(obj._category, obj._name);
    return this.schema(version).fillFromImpl(obj);
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 list(int version, NodePersistentStorageHandler.APIPojo obj) {
    NodePersistentStorage nps = H2O.getNPS();
    NodePersistentStorageEntry[] entries = nps.list(obj._category);
    String[] arr = new String[entries.length];
    int i = 0;
    obj._names = arr;
    for (NodePersistentStorageEntry entry : entries) {
      obj._names[i] = entry._name;
      i++;
    }
    // obj._list = nps.list(obj._category);
    return this.schema(version).fillFromImpl(obj);
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public NodePersistentStorageV1 delete(int version, NodePersistentStorageHandler.APIPojo obj) {
    NodePersistentStorage nps = H2O.getNPS();
    nps.delete(obj._category, obj._name);
    return this.schema(version).fillFromImpl(obj);
  }

  public static class APIPojo extends Iced {
    String _category;
    String _name;
    String _value;
    String[] _names;
//    NodePersistentStorageEntry[] _entries;
  }

  public static class APIPojoListEntryV1 extends Schema<NodePersistentStorageEntry, APIPojoListEntryV1> {
    @API(help="Key name", required=true, direction=API.Direction.OUTPUT)
    String name;

    @API(help="Size in bytes of value", required=true, direction=API.Direction.OUTPUT)
    long size;

    @API(help="Epoch time in milliseconds of when the value was written", required=true, direction=API.Direction.OUTPUT)
    long timestamp_millis;
  }

  public static class NodePersistentStorageV1 extends Schema<APIPojo, NodePersistentStorageV1> {
    @API(help="Category name", required=false, direction=API.Direction.INOUT)
    String category;

    @API(help="Key name", required=false, direction=API.Direction.INOUT)
    String name;

    @API(help="Value", required=false, direction=API.Direction.INOUT)
    String value;

    @API(help="List of key names", required=false, direction=API.Direction.OUTPUT)
    String[] names;

//    @API(help="List of entries", required=false, direction=API.Direction.OUTPUT)
//    APIPojoListEntryV1[] entries;
  }
}
