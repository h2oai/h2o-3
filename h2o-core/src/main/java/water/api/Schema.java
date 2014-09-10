package water.api;

import water.Iced;
import water.Key;
import water.fvec.Frame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


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
public abstract class Schema<I extends Iced, S extends Schema<I,S>> extends Iced {
  private final transient int _version;
  final int getVersion() { return _version; }
  protected Schema() {
    // Check version number
    String n = this.getClass().getSimpleName();
    assert n.charAt(n.length()-2)=='V' : "Schema classname does not end in a 'V' and a version #";
    _version = n.charAt(n.length()-1)-'0';
    assert 0 <= _version && _version <= 9 : "Schema classname does not contain version";
  }

  // TODO: move the algos schemas into water.api (or vice-versa) and make these protected:

  // Version&Schema-specific filling into the implementation object
  abstract public I createImpl();

  // Version&Schema-specific filling from the implementation object
  abstract public S fillFromImpl(I i);

  // Version&Schema-specific filling of an already filled object from this schema
  public I fillFromSchema() { return (I)this; }

  // TODO: this really does not belong in the schema layer; it's a hack for the
  // TODO: old-school-web-UI
  // This Schema accepts a Frame as it's first & main argument, used by the
  // Frame Inspect & Parse pages to give obvious options for Modeling, Summary,
  // export-to-CSV etc options.  Return a URL or null if not appropriate.
  protected String acceptsFrame( Frame fr ) { return null; }

  // Fill self from parms.  Limited to dumb primitive parsing and simple
  // reflective field filling.  Ignores fields not in the Schema.  Throws IAE
  // if the primitive parameter cannot be parsed as the primitive field type.

  // Dupped args are handled by Nano, as 'parms' can only have a single arg
  // mapping for a given name.

  // Also does various sanity checks for broken Schemas.  Fields must not be
  // private.  Input fields get filled here, so must not be final.
  public S fillFromParms(Properties parms) {
    // Get passed-in fields, assign into Schema

    Map<String, Field> fields = new HashMap<>();
    try {
      Class clz = getClass();
      do {
        Field[] some_fields = clz.getDeclaredFields();

        for (Field f : some_fields)
          if (null == fields.get(f.getName()))
            fields.put(f.getName(), f);

        clz = clz.getSuperclass();
      } while (Iced.class.isAssignableFrom(clz.getSuperclass()));
    }
    catch (SecurityException e) {
        throw new RuntimeException("Exception accessing fields: " + e);
    }

    for( String key : parms.stringPropertyNames() ) {
      try {
        Field f = fields.get(key); // No such field error, if parm is junk

        if (null == f)
          throw new IllegalArgumentException("Unknown argument: " + key);

        int mods = f.getModifiers();
        if( Modifier.isTransient(mods) || Modifier.isStatic(mods) )
          // Attempting to set a transient or static; treat same as junk fieldname
          throw new IllegalArgumentException("Unknown argument: " + key);
        // Only support a single annotation which is an API, and is required
        API api = (API)f.getAnnotations()[0];
        // Must have one of these set to be an input field
        if( api.direction() == API.Direction.OUTPUT )
          throw new IllegalArgumentException("Attempting to set output field: " + key);

        // Primitive parse by field type
        f.set(this,parse(parms.getProperty(key),f.getType()));

      } catch( ArrayIndexOutOfBoundsException aioobe ) {
        // Come here if missing annotation
        throw new RuntimeException("Broken internal schema; missing API annotation for field: " + key);
      } catch( IllegalAccessException iae ) {
        // Come here if field is final or private
        throw new RuntimeException("Broken internal schema; field cannot be private nor final: " + key);
      }
    }
    // Here every thing in 'parms' was set into some field - so we have already
    // checked for unknown or extra parms.

    // Confirm required fields are set
    for( Field f : fields.values() ) {
      int mods = f.getModifiers();
      if( Modifier.isTransient(mods) || Modifier.isStatic(mods) )
        continue;             // Ignore transient & static
      API api = (API)f.getAnnotations()[0]; // TODO: is there a more specific way we can do this?
      if( api.required() ) {
        if( parms.getProperty(f.getName()) == null )
          throw new IllegalArgumentException("Required field "+f.getName()+" not specified");
      }
      // TODO: execute "validation language" in the BackEnd, which includes a "required check", if any
    }
    return (S)this;
  }

  // URL parameter parse
  private <E> Object parse( String s, Class fclz ) {
    if( fclz.equals(String.class) ) return s; // Strings already the right primitive type
    if( fclz.equals(int.class) ) return Integer.valueOf(s);
    if( fclz.equals(long.class) ) return Long.valueOf(s);
    if( fclz.equals(boolean.class) ) return Boolean.valueOf(s); // TODO: loosen up so 1/0 work?
    if( fclz.equals(byte.class) ) return Byte.valueOf(s);
    if( fclz.isArray() ) {      // An array?
      if( s.equals("null") ) return null;
      read(s,    0       ,'[',fclz);
      read(s,s.length()-1,']',fclz);
      String[] splits = s.substring(1,s.length()-1).split(",");
      Class<E> afclz = (Class<E>)fclz.getComponentType();
      E[] a= (E[])Array.newInstance(afclz,splits.length);
      for( int i=0; i<splits.length; i++ )
        a[i] = (E)parse(splits[i].trim(),afclz);
      return a;
    }
    if( fclz.equals(Key.class) )
      if( s==null || s.length()==0 ) throw new IllegalArgumentException("Missing key");
      else return Key.make(s);
    if( Enum.class.isAssignableFrom(fclz) )
      return Enum.valueOf(fclz,s);

    throw new RuntimeException("Unimplemented schema fill from "+fclz.getSimpleName());
  }
  private int read( String s, int x, char c, Class fclz ) {
    if( peek(s,x,c) ) return x+1;
    throw new IllegalArgumentException("Expected '"+c+"' while reading a "+fclz.getSimpleName()+", but found "+s);
  }
  private boolean peek( String s, int x, char c ) { return x < s.length() && s.charAt(x) == c; }
}
