package water;

import java.util.UUID;

/**
 * Some properties to mix in to Frame, Model and such to make them uniquely identifiable.
 * That is, we want to distinguish between different instances of a Model that have the
 * same key over time.
 */
public class UniqueId extends Iced {
  private final String _key ;
  private final long _creation_epoch_time_millis;
  private final String _id;

  /** ONLY to be used to deserializing persisted instances.  */
  public UniqueId(String key, long creation_epoch_time_millis, String id) {
    assert id != null;
    _key = key;
    _creation_epoch_time_millis = creation_epoch_time_millis;
    _id = id;
  }

  public UniqueId(Key key) { this(key==null ? null : key.toString(),System.currentTimeMillis(),UUID.randomUUID().toString()); }

  public String getKey() { return _key; }
  public long getCreationEpochTimeMillis() { return _creation_epoch_time_millis; }
  public String getId() { return _id; }

  public boolean equals(Object o) {
    if (!(o instanceof UniqueId)) return false;
    UniqueId other = (UniqueId)o;
    // NOTE: we must call this.getId() because subclasses can define the id in a way that's dynamic.
    return
      (_creation_epoch_time_millis == other._creation_epoch_time_millis) &&
      (getId().equals(other.getId()));
  }

  public int hashCode() {
    return 17 +
      37 * (int)_creation_epoch_time_millis +
      37 * getId().hashCode();
  }
}
