package water.api;

import hex.Model;
import water.*;
import water.fvec.Frame;
import water.util.Log;
import water.util.MarkdownBuilder;
import water.util.PojoUtils;
import water.util.ReflectionUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


/**
 * Base Schema Class.  All REST API Schemas inherit from here.
 * <p>
 * The purpose of Schemas is to provide a stable, versioned interface to
 * the functionality in H2O, which allows the back end implementation to
 * change rapidly without breaking REST API clients such as the Web UI
 * and R binding.  Schemas also allow for functionality which exposes the
 * schema metadata to clients, allowing them to do service discovery and
 * to adapt dynamically to new H2O functionality (e.g., to be able to call
 * any ModelBuilder, even new ones written since the client was built,
 * without knowing any details about the specific algo).
 * <p>
 * Most schemas have a 1-to-1 mapping to an Iced implementation object.
 * Both may have children, or (more often) not.  Occasionally, e.g. in
 * the case of schemas used only to handle HTTP request parameters, there
 * will not be a backing impl object.
 * <p>
 * Schemas have a State section (broken into Input, Output and InOut fields)
 * and an Adapter section which fills the State to and from the Iced impl objects
 * and from HTTP request parameters.
 * <p>
 * Methods here allow us to convert from Schema to Iced (impl) and back in a
 * flexible way.  The default behaviour is to map like-named fields back and
 * forth, often with some default type conversions (e.g., a Keyed object like a
 * Model will be automagically converted back and forth to a Key).
 * Subclasses can override methods such as fillImpl or fillFromImpl to
 * provide special handling when adapting from schema to impl object and back.
 * <p>
 * Schema Fields must have a single API annotation describing in their direction
 * of operation (all fields will be output by default), and any other properties
 * such as "required".  Transient and static fields are ignored.
 * @see water.api.API for information on the field annotations
 * <p>
 * Some use cases:
 * <p>
 * To create a schema object and fill it from an existing impl object (the common case):<br>
 * {@code
 * S schema = schema(version);
 * schema.fillFromImpl(impl);
 * }
 * <p>
 * To create an impl object and fill it from an existing schema object (the common case):<br>
 * {@code
 * I impl = schema.createImpl(); // create an empty impl object and any empty children
 * schema.fillImpl(impl);        // fill the empty impl object and any children from the Schema and its children
 * }
 * <p>
 * or
 * {@code
 * I impl = schema.createAndFillImpl();  // helper which does schema.fillImpl(schema.createImpl())
 * }
 * <p>
 * To create a schema object filled from the default values of its impl class and then
 * overridden by HTTP request params:
 * {@code
 * S schema = schema(version);
 * I defaults = schema.createImpl();
 * schema.fillFromImpl(defaults);
 * schema.fillFromParms(parms);
 * }
 *
 */
public abstract class Schema<I extends Iced, S extends Schema<I,S>> extends Iced {
  private final transient int _version;
  final int getVersion() { return _version; }

  public Schema() {
    // Check version number
    String n = this.getClass().getSimpleName();
    assert n.charAt(n.length()-2)=='V' : "Schema classname does not end in a 'V' and a version #";
    _version = n.charAt(n.length()-1)-'0';
    assert 0 <= _version && _version <= 9 : "Schema classname does not contain version";
  }

  /**
   * Create an implementation object and any child objects but DO NOT fill them.
   * The purpose of a createImpl without a fillImpl is to be able to get the default
   * values for all the impl's fields.
   * <p>
   * For objects without children this method does all the required work. For objects
   * with children the subclass will need to override, e.g. by calling super.createImpl()
   * and then calling createImpl() on its children.
   * <p>
   * Note that impl objects for schemas which override this method don't need to have
   * a default constructor (e.g., a Keyed object constructor can still create and set
   * the Key), but they must not fill any fields which can be filled later from the schema.
   * <p>
   * TODO: We *could* handle the common case of children with the same field names here
   * by finding all of our fields that are themselves Schemas.
   */
  public I createImpl() {
    try {
      return (I)this.getImplClass().newInstance();
    }
    catch (Exception e) {
      String msg = "Exception instantiating implementation object of class: " + this.getImplClass().toString() + " for schema class: " + this.getClass();
      Log.err(msg + ": " + e);
      throw H2O.fail(msg, e);
    }
  }

  /** Fill an impl object and any children from this schema and its children. */
  public I fillImpl(I impl) {
    PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.CONSISTENT);
    PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
    return impl;
  }

  /** Convenience helper which creates and fill an impl. */
  final public I createAndFillImpl() {
    return this.fillImpl(this.createImpl());
  }

  // TODO: we need to pass in the version from the request so the superclass can create versioned sub-schemas.  See the *Base
  /** Version and Schema-specific filling from the implementation object. */
  public S fillFromImpl(I impl) {
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.CONSISTENT);
    return (S)this;
  }

  public Class<? extends Iced> getImplClass() {
    return ReflectionUtils.findActualClassParameter(this.getClass(), 0);
  }

  // TODO: this really does not belong in the schema layer; it's a hack for the
  // TODO: old-school-web-UI
  // This Schema accepts a Frame as it's first & main argument, used by the
  // Frame Inspect & Parse pages to give obvious options for Modeling, Summary,
  // export-to-CSV etc options.  Return a URL or null if not appropriate.
  @Deprecated
  protected String acceptsFrame( Frame fr ) { return null; }

  // Fill self from parms.  Limited to dumb primitive parsing and simple
  // reflective field filling.  Ignores fields not in the Schema.  Throws IAE
  // if the primitive parameter cannot be parsed as the primitive field type.

  // Dupped args are handled by Nano, as 'parms' can only have a single arg
  // mapping for a given name.

  // Also does various sanity checks for broken Schemas.  Fields must not b
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
          throw new IllegalArgumentException("Unknown argument (not found): " + key);

        int mods = f.getModifiers();
        if( Modifier.isTransient(mods) || Modifier.isStatic(mods) )
          // Attempting to set a transient or static; treat same as junk fieldname
          throw new IllegalArgumentException("Unknown argument (transient or static): " + key);
        // Only support a single annotation which is an API, and is required
        API api = (API)f.getAnnotations()[0];
        // Must have one of these set to be an input field
        if( api.direction() == API.Direction.OUTPUT )
          throw new IllegalArgumentException("Attempting to set output field: " + key);

        // Primitive parse by field type
        Object parse_result = parse(parms.getProperty(key),f.getType(), api.required());
        if (parse_result != null && f.getType().isArray() && parse_result.getClass().isArray() && (f.getType().getComponentType() != parse_result.getClass().getComponentType())) {
          // We have to conform an array of primitives.  There's got to be a better way. . .
          if (parse_result.getClass().getComponentType() == int.class && f.getType().getComponentType() == Integer.class) {
            int[] from = (int[])parse_result;
            Integer[] copy = new Integer[from.length];
            for (int i = 0; i < from.length; i++)
              copy[i] = from[i];
            f.set(this, copy);
          } else if (parse_result.getClass().getComponentType() == Integer.class && f.getType().getComponentType() == int.class) {
            Integer[] from = (Integer[])parse_result;
            int[] copy = new int[from.length];
            for (int i = 0; i < from.length; i++)
              copy[i] = from[i];
            f.set(this, copy);
          } else {
            throw H2O.fail("Don't know how to cast an array of: " + parse_result.getClass().getComponentType() + " to an array of: " + f.getType().getComponentType());
          }
        } else {
          f.set(this, parse_result);
        }
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
      try {
        API api = (API) f.getAnnotations()[0]; // TODO: is there a more specific way we can do this?
        if (api.required()) {
          if (parms.getProperty(f.getName()) == null)
            throw new IllegalArgumentException("Required field " + f.getName() + " not specified");
        }
      }
      catch (ArrayIndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Missing annotation for API field: " + f.getName());
      }
    }
    return (S)this;
  }

  // URL parameter parse
  private <E> Object parse( String s, Class fclz, boolean required ) {
    if( fclz.equals(String.class) ) return s; // Strings already the right primitive type
    if( fclz.equals(int.class) ) return Integer.valueOf(s);
    if( fclz.equals(long.class) ) return Long.valueOf(s);
    if( fclz.equals(boolean.class) ) return Boolean.valueOf(s); // TODO: loosen up so 1/0 work?
    if( fclz.equals(byte.class) ) return Byte.valueOf(s);
    if( fclz.equals(double.class) ) return Double.valueOf(s);
    if( fclz.equals(float.class) ) return Float.valueOf(s);
    if( fclz.isArray() ) {      // An array?
      if( s.equals("null") || s.length() == 0) return null;
      read(s,    0       ,'[',fclz);
      read(s,s.length()-1,']',fclz);
      String[] splits = s.substring(1,s.length()-1).split(",");
      Class<E> afclz = (Class<E>)fclz.getComponentType();
      E[] a = null;
      // Can't cast an int[] to an Object[].  Sigh.
      if (afclz == int.class) { // TODO: other primitive types. . .
        a = (E[]) Array.newInstance(Integer.class, splits.length);
      } else {
        // Fails with primitive classes; need the wrapper class.  Thanks, Java.
        a = (E[]) Array.newInstance(afclz, splits.length);
      }

      for( int i=0; i<splits.length; i++ )
        a[i] = (E)parse(splits[i].trim(),afclz, required);
      return a;
    }
    if( fclz.equals(Key.class) )
      if( (s==null || s.length()==0) && required ) throw new IllegalArgumentException("Missing key");
      else if (!required && (s == null || s.length() == 0)) return null;
      else if (!required) return Key.make(s);
      else return Key.make(s);

    if( Enum.class.isAssignableFrom(fclz) )
      return Enum.valueOf(fclz,s);

    if( Frame.class.isAssignableFrom(fclz) )
      if( (s==null || s.length()==0) && required ) throw new IllegalArgumentException("Missing key");
      else if (!required && (s == null || s.length() == 0)) return null;
      else {
        Value v = DKV.get(s);
        if (null == v) return null; // not required
        if (! v.isFrame()) throw new IllegalArgumentException("Frame argument points to a non-frame object.");
        return v.get();
      }

    if( Model.class.isAssignableFrom(fclz) )
      if( (s==null || s.length()==0) && required ) throw new IllegalArgumentException("Missing key");
      else if (!required && (s == null || s.length() == 0)) return null;
      else {
        Value v = DKV.get(s);
        if (null == v) return null; // not required
        if (! v.isModel()) throw new IllegalArgumentException("Model argument points to a non-model object.");
        return v.get();
      }

    throw new RuntimeException("Unimplemented schema fill from "+fclz.getSimpleName());
  }
  private int read( String s, int x, char c, Class fclz ) {
    if( peek(s,x,c) ) return x+1;
    throw new IllegalArgumentException("Expected '"+c+"' while reading a "+fclz.getSimpleName()+", but found "+s);
  }
  private boolean peek( String s, int x, char c ) { return x < s.length() && s.charAt(x) == c; }

  /**
   * Generate Markdown documentation for this Schema.
   */
  public StringBuffer markdown(StringBuffer appendToMe) {
    return markdown(new SchemaMetadata(this), appendToMe);
  }

  /**
   * Generate Markdown documentation for this Schema, given we already have the metadata constructed.
   */
  public StringBuffer markdown(SchemaMetadata meta , StringBuffer appendToMe) {
    MarkdownBuilder builder = new MarkdownBuilder();

    builder.comment("Preview with http://jbt.github.io/markdown-editor");
    builder.heading1("schema ", this.getClass().getSimpleName());
    builder.hline();
    // builder.paragraph(metadata.summary);

    // TODO: refactor with Route.markdown():

    // fields
    boolean first; // don't print the table at all if there are no rows

    first = true;
    builder.heading2("input fields");
    try {
      for (SchemaMetadata.FieldMetadata field_meta : meta.fields) {
        if (field_meta.direction == API.Direction.INPUT || field_meta.direction == API.Direction.INOUT) {
          if (first) {
            builder.tableHeader("name", "required?", "level", "type", "schema?", "default", "description", "values");
            first = false;
          }
          builder.tableRow(field_meta.name, String.valueOf(field_meta.required), field_meta.level.name(), field_meta.type, String.valueOf(field_meta.is_schema), field_meta.value, field_meta.help, (field_meta.values == null || field_meta.values.length == 0 ? "" : Arrays.toString(field_meta.values)));
        }
      }
      if (first)
        builder.paragraph("(none)");

      first = true;
      builder.heading2("output fields");
      for (SchemaMetadata.FieldMetadata field_meta : meta.fields) {
        if (field_meta.direction == API.Direction.OUTPUT || field_meta.direction == API.Direction.INOUT) {
          if (first) {
            builder.tableHeader("name", "type", "schema?", "default", "description", "values");
            first = false;
          }
          builder.tableRow(field_meta.name, field_meta.type, String.valueOf(field_meta.is_schema), field_meta.value, field_meta.help, (field_meta.values == null || field_meta.values.length == 0 ? "" : Arrays.toString(field_meta.values)));
        }
      }
      if (first)
        builder.paragraph("(none)");

      // TODO: render examples and other stuff, if it's passed in
    }
    catch (Exception e) {
      throw H2O.fail("Caught exception using reflection on schema: " + this + ": " + e);
    }

    if (null != appendToMe)
      appendToMe.append(builder.stringBuffer());

    return builder.stringBuffer();
  } // markdown()

}
