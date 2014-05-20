package water.schemas;

import java.util.Properties;
import water.Iced;
import water.H2O;
import water.api.Handler;

/** Base Schema Class
 *  
 *  All Schemas inherit from here.  Schemas have a State section (broken into
 *  Input fields and Output fields) and an Adapter section to fill the State to
 *  and from URLs and JSON.  The base Adapter logic is here, and will by
 *  default copy same-named fields to and from Schemas to concrete Iced objects.
 */
public abstract class Schema<H extends Handler<H,S>,S extends Schema<H,S>> extends Iced {
  private final transient int _version;
  protected final int getVersion() { return _version; }
  protected Schema() {
    // Check version number
    String n = this.getClass().getSimpleName();
    assert n.charAt(n.length()-2)=='V' : "Schema classname does not end in a 'V' and a version #";
    _version = n.charAt(n.length()-1)-'0';
    assert 0 <= _version && _version <= 9 : "Schema classname does not contain version";
  }

  // Version&Schema-specific filling into the handler
  abstract public S fillInto( H h );

  // Version&Schema-specific filling from the handler
  abstract public S fillFrom( H h );

  // Fill self from parms.  Limited to dumb primitive parsing and simple
  // reflective field filling.  Ignores fields not in the Schema.  Throws IAE
  // if the primitive parameter cannot be parsed as the primitive field type.
  public S fillFrom( Properties parms ) {
    throw H2O.unimpl();
  }


}
