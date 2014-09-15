package water.api;

import water.UniqueId;
import water.util.BeanUtils;

/**
 * Some properties to mix in to Frame, Model and such to make them uniquely identifiable.
 * That is, we want to distinguish between different instances of a Model that have the
 * same key over time.
 */

public class UniqueIdBase extends Schema<UniqueId, UniqueIdBase> {

  @API(help="The keycreation timestamp for the object (if it's not null).")
  public String key;

  @API(help="The creation timestamp for the object.")
  public long creation_epoch_time_millis;

  @API(help="The id for the object.")
  public String id;

  // Version&Schema-specific filling into the implementation object
  public UniqueId createImpl() {
    UniqueId uid = new UniqueId(this.key, this.creation_epoch_time_millis, this.id);
    return uid;
  }

  // Version&Schema-specific filling from the implementation object
  public UniqueIdBase fillFromImpl(UniqueId uid) {
    BeanUtils.copyProperties(this, uid, BeanUtils.FieldNaming.CONSISTENT);
    return this;
  }
}
