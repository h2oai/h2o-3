package water.schemas;

import java.lang.reflect.*;
import java.util.Properties;
import water.*;
import water.api.Handler;


/** Base Schema Class
 *  
 *  All Schemas inherit from here.  Schemas have a State section (broken into
 *  Input fields and Output fields) and an Adapter section to fill the State to
 *  and from URLs and JSON.  The base Adapter logic is here, and will by
 *  default copy same-named fields to and from Schemas to concrete Iced objects.
 *
 *  Schema Fields must have a single API annotation describing in they are an
 *  input field or not (all fields will be output by default), and any extra
 *  requirements on the input (prior field dependencies and other validation
 *  checks).  Transient & Static fields are ignored.
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

  // Dupped args are handled by Nano, as 'parms' can only have a single arg
  // mapping for a given name.

  // Also does various sanity checks for broken Schemas.  Fields must not be
  // private.  Input fields get filled here, so must not be final.
  public S fillFrom( Properties parms ) {
    // Get passed-in fields, assign into Schema
    Class clz = getClass();
    for( String key : parms.stringPropertyNames() ) {
      try {
        Field f = clz.getDeclaredField(key); // No such field error, if parm is junk
        int mods = f.getModifiers();
        if( Modifier.isTransient(mods) || Modifier.isStatic(mods) )
          // Attemptint to set a transient or static; treat same as junk fieldname
          throw new IllegalArgumentException("Unknown argument "+key);
        // Only support a single annotation which is an API, and is required
        API api = (API)f.getAnnotations()[0]; 
        // Must have one of these set to be an input field
        if( api.validation().length()==0 && 
            api.values    ().length()==0 && 
            api.dependsOn ().length  ==0 ) 
          throw new IllegalArgumentException("Attempting to set output field "+key);

        // Primitive parse by field type
        Class fclz = f.getType();            // Field type
        String value = parms.getProperty(key);
        if( fclz.equals(String.class) ) {
          f.set(this,value);    // Strings already the right primitive type
        } else
          throw H2O.unimpl();
        
      } catch( NoSuchFieldException nsfe ) { // Convert missing-field to IAE
        throw new IllegalArgumentException("Unknown argument "+key);
      } catch( ArrayIndexOutOfBoundsException aioobe ) {
        // Come here if missing annotation
        throw new RuntimeException("Broken internal schema; missing API annotation: "+key);
      } catch( IllegalAccessException iae ) {
        // Come here if field is final or private
        throw new RuntimeException("Broken internal schema; cannot be private nor final: "+key);
      }
    }
    // Here every thing in 'parms' was set into some field - so we have already
    // checked for unknown or extra parms.

    // Confirm required fields are set
    do {
      for( Field f : clz.getDeclaredFields() ) {
        int mods = f.getModifiers();
        if( Modifier.isTransient(mods) || Modifier.isStatic(mods) )
          continue;             // Ignore transient & static
        API api = (API)f.getAnnotations()[0]; 
        if( api.validation().length() > 0 ) {
          // TODO: execute "validation language" in the BackEnd, which includes a "required check", if any
          if( parms.getProperty(f.getName()) == null )
            throw new IllegalArgumentException("Required field "+f.getName()+" not specified");
        }      
      }
      clz = clz.getSuperclass();
    } while( Iced.class.isAssignableFrom(clz.getSuperclass()) );
    return (S)this;
  }

}
