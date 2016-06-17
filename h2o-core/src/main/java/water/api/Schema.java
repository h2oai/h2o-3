package water.api;

import hex.schemas.ModelBuilderSchema;
import org.reflections.Reflections;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.exceptions.H2ONotFoundArgumentException;
import water.fvec.Frame;
import water.util.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;


/**
 * Base Schema class; all REST API Schemas inherit from here.
 * <p>
 * Schema is a primary interface of the REST APIs: all endpoints consume some schema object as an input, and produce
 * another schema object as an output (though some endpoints may return nothing).
 * <p>
 * Schemas, as an external interface, are required to be stable: fields may not be renamed or removed, their types or
 * meaning may not change, etc. It is allowed to add new fields to a schema, provided that they are optional, and
 * that their default values correspond to the old behavior of the endpoint. If these requirements cannot be met,
 * then a new version of a schema class must be created.
 * <p>
 * Many schemas are in direct correspondence with H2O objects. For example, JobV3 schema represents the Job object.
 * These "representative" Iced objects are called "implementation" or "impl", and are parametrized with type I.
 * Such representation is necessary in order to ensure stability of the interface: even as Job class evolves, the
 * interface of JobV3 schema must not. In the simplest case, when there is 1-to-1 correspondence between fields in
 * the impl class and in the schema, we use reflection magic to copy those fields over. The reflection magic is smart
 * enough to perform simple field name translations, and even certain type translations (like Keyed objects into Keys).
 * If there is no such correspondence, then special type adapters must be written. Right now this is done by
 * overriding the {@link #fillImpl(I) fillImpl} and {@link #fillFromImpl(I) fillFromImpl} methods. Usually they will
 * want to call super to get the default behavior, and then modify the results a bit (e.g., to map differently-named
 * fields, or to compute field values). Transient and static fields are ignored by the reflection magic.
 * <p>
 * There are also schemas that do not correspond to any H2O object. These are mostly the input schemas (schemas used
 * for inputs of api requests). Such schemas should be "implemented" by Iced.
 * <p>
 * All schemas are expected to be self-documenting, in the sense that all fields within those schemas should carry
 * detailed documentation about their meaning, as well as any additional hints about the field's usage. These should
 * be annotated using the {@link API @API} interface. If a schema contains a complicated object, then that object
 * itself should derive from Schema, so that its fields can also be properly documented. However if the internal
 * object is sufficiently simple (say, a Map), then it may be sufficient to document it as a whole and have it derived
 * from Iced, not from Schema.
 * <p>
 * Schema names (getSimpleName()) must be unique within an application. During Schema discovery and registration
 * there are checks to ensure this. Also there should be at most one Schema class per implementation class per
 * version (with the exception of schemas that are backed by Iced).
 * <p>
 * For V3 Schemas each field had a "direction" (input / output / both), which allowed us to use the same schema as
 * both input and output for an endpoint. This is no longer possible in V4: two separate schema classes for input /
 * output should be created.
 *
 * <h1>Usage</h1>
 * <p>
 * {@link Handler} creates an input schema from the body/parameters of the HTTP request (using
 * {@link #fillFromParms(Properties) fillFromParms()}, and passes it on to the corresponding handler method.
 * <p>
 * Each handler method may modify the input schema and return it as the output schema (common for V3 endpoints,
 * should be avoided in V4).
 * <p>
 * Alternatively, a handler method may create a new output schema object from scratch, or from an existing
 * implementation object.
 *
 * <h1>Internal details</h1>
 * <p>
 * Most Java developers need not be concerned with the details that follow, because the
 * framework will make these calls as necessary.
 * <p>
 * Some use cases:
 * <p>
 * To find and create an instance of the appropriate schema for an Iced object, with the
 * given version or the highest previous version:<pre>
 * Schema s = Schema.schema(6, impl);
 * </pre>
 * <p>
 * To create a schema object and fill it from an existing impl object (the common case):<pre>
 * S schema = MySchemaClass(version).fillFromImpl(impl);</pre>
 * or more generally:
 * <pre>
 * S schema = Schema(version, impl).fillFromImpl(impl);</pre>
 * To create an impl object and fill it from an existing schema object (the common case):
 * <pre>
 * I impl = schema.createImpl(); // create an empty impl object and any empty children
 * schema.fillImpl(impl);        // fill the empty impl object and any children from the Schema and its children</pre>
 * or
 * <pre>
 * I impl = schema.createAndFillImpl();  // helper which does schema.fillImpl(schema.createImpl())</pre>
 * <p>
 * Schemas that are used for HTTP requests are filled with the default values of their impl
 * class, and then any present HTTP parameters override those default values.
 * <p>
 * To create a schema object filled from the default values of its impl class and then
 * overridden by HTTP request params:
 * <pre>
 * S schema = MySchemaClass(version);
 * I defaults = schema.createImpl();
 * schema.fillFromImpl(defaults);
 * schema.fillFromParms(parms);
 * </pre>
 * or more tersely:
 * <pre>
 * S schema = MySchemaClass(version).fillFromImpl(schema.createImpl()).fillFromParms(parms);
 * </pre>
 * @param <I> "implementation" (Iced) class for this schema
 * @param <S> reference to self: this should always be the same class as being declared. For example:
 *            <pre>public class TimelineV3 extends Schema&lt;Timeline, TimelineV3&gt;</pre>
 */
abstract public class Schema<I extends Iced, S extends Schema<I,S>> extends Iced {
  // These fields are declared transient so that they do not get included when a schema is serialized into JSON.
  private transient Class<I> _impl_class;
  private transient int _schema_version;
  private transient String _schema_name;
  private transient String _schema_type;


  /** Default constructor; triggers lazy schema registration.
   *  @throws water.exceptions.H2OFailException if there is a name collision or
   *          there is more than one schema which maps to the same Iced class */
  public Schema() {
    init_meta();
    SchemaServer.checkIfRegistered(this);
  }

  protected void init_meta() {
    if (_schema_name != null) return;
    _schema_name = this.getClass().getSimpleName();
    _schema_version = extractVersionFromSchemaName(_schema_name);
    _schema_type = getImplClass().getSimpleName();
  }

  /** Extract the version number from the schema class name.  Returns -1 if
   *  there's no version number at the end of the classname. */
  public static int extractVersionFromSchemaName(String clz_name) {
    int idx = clz_name.lastIndexOf('V');
    if (idx == -1) return -1;
    try { return Integer.valueOf(clz_name.substring(idx+1)); }
    catch( NumberFormatException ex) { return -1; }
  }

  /** Get the version number of this schema, for example 3 or 99. Note that 99
   *  is the "experimental" version, meaning that there are no stability
   *  guarantees between H2O versions.  */
  public int getSchemaVersion() { return _schema_version; }

  public String getSchemaName() { return _schema_name; }

  public String getSchemaType() { return _schema_type; }


  /**
   * Create an appropriate implementation object and any child objects but does not fill them.
   * The standard purpose of a createImpl without a fillImpl is to be able to get the default
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
   * TODO: We could handle the common case of children with the same field names here
   * by finding all of our fields that are themselves Schemas.
   */
  public I createImpl() {
    try { return getImplClass().newInstance(); }
    catch (Exception e) { throw H2O.fail("Exception making a newInstance",e); }
  }

  protected I fillImpl(I impl, String[] fieldsToSkip) {
    PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.CONSISTENT, fieldsToSkip); // TODO: make field names in the impl classes consistent and remove
    PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES, fieldsToSkip);
    return impl;
  }

  /** Fill an impl object and any children from this schema and its children.
   *  If a schema doesn't need to adapt any fields if does not need to override
   *  this method. */
  public I fillImpl(I impl) {
    return fillImpl(impl, null);
  }

  /** Convenience helper which creates and fills an impl object from this schema. */
  final public I createAndFillImpl() {
    return this.fillImpl(this.createImpl());
  }

  /** Fill this Schema from the given implementation object. If a schema doesn't need to adapt any fields if does not need to override this method. */
  public S fillFromImpl(I impl) {
    return fillFromImpl(impl, null);
  }

  protected S fillFromImpl(I impl, String[] fieldsToSkip) {
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, fieldsToSkip);
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.CONSISTENT, fieldsToSkip);  // TODO: make field names in the impl classes consistent and remove
    //noinspection unchecked  (parameter <S> should be the derived class itself)
    return (S) this;
  }

  /** Return the class of the implementation type parameter I for the
   *  given Schema class. Used by the metadata facilities and the
   *  reflection-base field-copying magic in PojoUtils. */
  public static Class<? extends Iced> getImplClass(Class<? extends Schema> clz) {
    Class<? extends Iced> impl_class = ReflectionUtils.findActualClassParameter(clz, 0);
    if (null == impl_class)
      Log.warn("Failed to find an impl class for Schema: " + clz);
    return impl_class;
  }

  /** Return the class of the implementation type parameter I for this Schema.
   *  Used by generic code which deals with arbitrary schemas and their backing
   *  impl classes.  Never returns null. */
  public Class<I> getImplClass() {
    return _impl_class != null ? _impl_class : (_impl_class = ReflectionUtils.findActualClassParameter(this.getClass(), 0));
  }

  /**
   * Fill this Schema object from a set of parameters.
   *
   * @param parms  parameters - set of tuples (parameter name, parameter value)
   * @return this schema
   *
   * @see #fillFromParms(Properties, boolean)
   */
  public S fillFromParms(Properties parms) {
    return fillFromParms(parms, true);
  }
  /**
   * Fill this Schema from a set of (generally HTTP) parameters.
   * <p>
   * Using reflection this process determines the type of the target field and
   * conforms the types if possible.  For example, if the field is a Keyed type
   * the name (ID) will be looked up in the DKV and mapped appropriately.
   * <p>
   * The process ignores parameters which are not fields in the schema, and it
   * verifies that all fields marked as required are present in the parameters
   * list.
   * <p>
   * It also does various sanity checks for broken Schemas, for example fields must
   * not be private, and since input fields get filled here they must not be final.
   * @param parms Properties map of parameter values
   * @param checkRequiredFields  perform check for missing required fields
   * @return this schema
   * @throws H2OIllegalArgumentException for bad/missing parameters
   */
  public S fillFromParms(Properties parms, boolean checkRequiredFields) {
    // Get passed-in fields, assign into Schema
    Class thisSchemaClass = this.getClass();

    Map<String, Field> fields = new HashMap<>();
    Field current = null; // declare here so we can print in catch{}
    try {
      Class clz = thisSchemaClass;
      do {
        Field[] some_fields = clz.getDeclaredFields();

        for (Field f : some_fields) {
          current = f;
          if (null == fields.get(f.getName()))
            fields.put(f.getName(), f);
        }

        clz = clz.getSuperclass();
      } while (Iced.class.isAssignableFrom(clz.getSuperclass()));
    }
    catch (SecurityException e) {
        throw H2O.fail("Exception accessing field: " + current + " in class: " + this.getClass() + ": " + e);
    }

    for( String key : parms.stringPropertyNames() ) {
      try {
        Field f = fields.get(key); // No such field error, if parm is junk

        if (null == f) {
          throw new H2OIllegalArgumentException("Unknown parameter: " + key, "Unknown parameter in fillFromParms: " + key + " for class: " + this.getClass().toString());
        }

        int mods = f.getModifiers();
        if( Modifier.isTransient(mods) || Modifier.isStatic(mods) ) {
          // Attempting to set a transient or static; treat same as junk fieldname
          throw new H2OIllegalArgumentException(
                  "Bad parameter for field: " + key + " for class: " + this.getClass().toString(),
                  "Bad parameter definition for field: " + key + " in fillFromParms for class: " + this.getClass().toString() + " (field was declared static or transient)");
        }
        // Only support a single annotation which is an API, and is required
        Annotation[] apis = f.getAnnotations();
        if( apis.length == 0 ) throw H2O.fail("Broken internal schema; missing API annotation for field: " + key);
        API api = (API)apis[0];
        // Must have one of these set to be an input field
        if( api.direction() == API.Direction.OUTPUT ) {
          throw new H2OIllegalArgumentException(
                  "Attempting to set output field: " + key + " for class: " + this.getClass().toString(),
                  "Attempting to set output field: " + key + " in fillFromParms for class: " + this.getClass().toString() + " (field was annotated as API.Direction.OUTPUT)");
        }
        // Parse value and set the field
        setField(this, f, key, parms.getProperty(key), api.required(), thisSchemaClass);
      } catch( IllegalAccessException iae ) {
        // Come here if field is final or private
        throw H2O.fail("Broken internal schema; field cannot be private nor final: " + key);
      }
    }
    // Here every thing in 'parms' was set into some field - so we have already
    // checked for unknown or extra parms.

    // Confirm required fields are set
    if (checkRequiredFields) {
      for (Field f : fields.values()) {
        int mods = f.getModifiers();
        if (Modifier.isTransient(mods) || Modifier.isStatic(mods))
          continue;             // Ignore transient & static
        try {
          API api = (API) f.getAnnotations()[0]; // TODO: is there a more specific way we can do this?
          if (api.required()) {
            if (parms.getProperty(f.getName()) == null) {
              IcedHashMap.IcedHashMapStringObject values = new IcedHashMap.IcedHashMapStringObject();
              values.put("schema", this.getClass().getSimpleName());
              values.put("argument", f.getName());
              throw new H2OIllegalArgumentException(
                  "Required field " + f.getName() + " not specified",
                  "Required field " + f.getName() + " not specified for schema class: " + this.getClass(),
                  values);
            }
          }
        } catch (ArrayIndexOutOfBoundsException e) {
          throw H2O.fail("Missing annotation for API field: " + f.getName());
        }
      }
    }
    //noinspection unchecked  (parameter <S> should be the derived class itself)
    return (S) this;
  }

  /**
   * Safe method to set the field on given schema object
   * @param o  schema object to modify
   * @param f  field to modify
   * @param key  name of field to modify
   * @param value  string-based representation of value to set
   * @param required  is field required by API
   * @param thisSchemaClass  class of schema handling this (can be null)
   * @throws IllegalAccessException
   */
  public static <T extends Schema>  void setField(T o, Field f, String key, String value, boolean required, Class thisSchemaClass) throws IllegalAccessException {
    // Primitive parse by field type
    Object parse_result = parse(key, value, f.getType(), required, thisSchemaClass);
    if (parse_result != null && f.getType().isArray() && parse_result.getClass().isArray() && (f.getType().getComponentType() != parse_result.getClass().getComponentType())) {
      // We have to conform an array of primitives.  There's got to be a better way. . .
      if (parse_result.getClass().getComponentType() == int.class && f.getType().getComponentType() == Integer.class) {
        int[] from = (int[])parse_result;
        Integer[] copy = new Integer[from.length];
        for (int i = 0; i < from.length; i++)
          copy[i] = from[i];
        f.set(o, copy);
      } else if (parse_result.getClass().getComponentType() == Integer.class && f.getType().getComponentType() == int.class) {
        Integer[] from = (Integer[])parse_result;
        int[] copy = new int[from.length];
        for (int i = 0; i < from.length; i++)
          copy[i] = from[i];
        f.set(o, copy);
      } else if (parse_result.getClass().getComponentType() == Double.class && f.getType().getComponentType() == double.class) {
        Double[] from = (Double[])parse_result;
        double[] copy = new double[from.length];
        for (int i = 0; i < from.length; i++)
          copy[i] = from[i];
        f.set(o, copy);
      } else if (parse_result.getClass().getComponentType() == Float.class && f.getType().getComponentType() == float.class) {
        Float[] from = (Float[])parse_result;
        float[] copy = new float[from.length];
        for (int i = 0; i < from.length; i++)
          copy[i] = from[i];
        f.set(o, copy);
      } else {
        throw H2O.fail("Don't know how to cast an array of: " + parse_result.getClass().getComponentType() + " to an array of: " + f.getType().getComponentType());
      }
    } else {
      f.set(o, parse_result);
    }
  }

  static <E> Object parsePrimitve(String s, Class fclz) {
    if (fclz.equals(String.class)) return s; // Strings already the right primitive type
    if (fclz.equals(int.class)) return parseInteger(s, int.class);
    if (fclz.equals(long.class)) return parseInteger(s, long.class);
    if (fclz.equals(short.class)) return parseInteger(s, short.class);
    if (fclz.equals(boolean.class)) {
      if (s.equals("0")) return Boolean.FALSE;
      if (s.equals("1")) return Boolean.TRUE;
      return Boolean.valueOf(s);
    }
    if (fclz.equals(byte.class)) return parseInteger(s, byte.class);
    if (fclz.equals(double.class)) return Double.valueOf(s);
    if (fclz.equals(float.class)) return Float.valueOf(s);
    //FIXME: if (fclz.equals(char.class)) return Character.valueOf(s);
    throw H2O.fail("Unknown primitive type to parse: " + fclz.getSimpleName());
  }

  // URL parameter parse
  static <E> Object parse(String field_name, String s, Class fclz, boolean required, Class schemaClass) {
    if (fclz.isPrimitive() || String.class.equals(fclz)) {
      try {
        return parsePrimitve(s, fclz);
      } catch (NumberFormatException ne) {
        String msg = "Illegal argument for field: " + field_name + " of schema: " +  schemaClass.getSimpleName() + ": cannot convert \"" + s + "\" to type " + fclz.getSimpleName();
        throw new H2OIllegalArgumentException(msg);
      }
    }
    // An array?
    if (fclz.isArray()) {
      // Get component type
      Class<E> afclz = (Class<E>) fclz.getComponentType();
      // Result
      E[] a = null;
      // Handle simple case with null-array
      if (s.equals("null") || s.length() == 0) return null;
      // Splitted values
      String[] splits; // "".split(",") => {""} so handle the empty case explicitly
      if (s.startsWith("[") && s.endsWith("]") ) { // It looks like an array
        read(s, 0, '[', fclz);
        read(s, s.length() - 1, ']', fclz);
        String inside = s.substring(1, s.length() - 1).trim();
        if (inside.length() == 0)
          splits = new String[]{};
        else
          splits = splitArgs(inside);
      } else { // Lets try to parse single value as an array!
        // See PUBDEV-1955
        splits = new String[] { s.trim() };
      }

      // Can't cast an int[] to an Object[].  Sigh.
      if (afclz == int.class) { // TODO: other primitive types. . .
        a = (E[]) Array.newInstance(Integer.class, splits.length);
      } else if (afclz == double.class) {
        a = (E[]) Array.newInstance(Double.class, splits.length);
      } else if (afclz == float.class) {
        a = (E[]) Array.newInstance(Float.class, splits.length);
      } else {
        // Fails with primitive classes; need the wrapper class.  Thanks, Java.
        a = (E[]) Array.newInstance(afclz, splits.length);
      }

      for (int i = 0; i < splits.length; i++) {
        if (String.class == afclz || KeyV3.class.isAssignableFrom(afclz)) {
          // strip quotes off string values inside array
          String stripped = splits[i].trim();

          if ("null".equals(stripped.toLowerCase()) || "na".equals(stripped.toLowerCase())) {
            a[i] = null;
            continue;
          }

          // Quotes are now optional because standard clients will send arrays of length one as just strings.
          if (stripped.startsWith("\"") && stripped.endsWith("\"")) {
            stripped = stripped.substring(1, stripped.length() - 1);
          }

          a[i] = (E) parse(field_name, stripped, afclz, required, schemaClass);
        } else {
          a[i] = (E) parse(field_name, splits[i].trim(), afclz, required, schemaClass);
        }
      }
      return a;
    }

    if (fclz.equals(Key.class))
      if ((s == null || s.length() == 0) && required) throw new H2OKeyNotFoundArgumentException(field_name, s);
      else if (!required && (s == null || s.length() == 0)) return null;
      else
        return Key.make(s.startsWith("\"") ? s.substring(1, s.length() - 1) : s); // If the key name is in an array we need to trim surrounding quotes.

    if (KeyV3.class.isAssignableFrom(fclz)) {
      if ((s == null || s.length() == 0) && required) throw new H2OKeyNotFoundArgumentException(field_name, s);
      if (!required && (s == null || s.length() == 0)) return null;

      return KeyV3.make(fclz, Key.make(s.startsWith("\"") ? s.substring(1, s.length() - 1) : s)); // If the key name is in an array we need to trim surrounding quotes.
    }

    // Enums can match either 1:1 or all lower or all upper case
    if (Enum.class.isAssignableFrom(fclz)) {
      try {
        return Enum.valueOf(fclz, s);
      } catch (Throwable t1) {
        try {
          return Enum.valueOf(fclz, s.toLowerCase());
        } catch (Throwable t2) {
          return Enum.valueOf(fclz, s.toUpperCase());
        }
      }
    }

    // TODO: these can be refactored into a single case using the facilities in Schema:
    if (FrameV3.class.isAssignableFrom(fclz)) {
      if ((s == null || s.length() == 0) && required) throw new H2OKeyNotFoundArgumentException(field_name, s);
      else if (!required && (s == null || s.length() == 0)) return null;
      else {
        Value v = DKV.get(s);
        if (null == v) return null; // not required
        if (!v.isFrame()) throw H2OIllegalArgumentException.wrongKeyType(field_name, s, "Frame", v.get().getClass());
        return new FrameV3((Frame) v.get()); // TODO: version!
      }
    }

    if (JobV3.class.isAssignableFrom(fclz)) {
      if ((s == null || s.length() == 0) && required) throw new H2OKeyNotFoundArgumentException(s);
      else if (!required && (s == null || s.length() == 0)) return null;
      else {
        Value v = DKV.get(s);
        if (null == v) return null; // not required
        if (!v.isJob()) throw H2OIllegalArgumentException.wrongKeyType(field_name, s, "Job", v.get().getClass());
        return new JobV3().fillFromImpl((Job) v.get()); // TODO: version!
      }
    }

    // TODO: for now handle the case where we're only passing the name through; later we need to handle the case
    // where the frame name is also specified.
    if (FrameV3.ColSpecifierV3.class.isAssignableFrom(fclz)) {
      return new FrameV3.ColSpecifierV3(s);
    }

    if (ModelSchema.class.isAssignableFrom(fclz))
      throw H2O.fail("Can't yet take ModelSchema as input.");
    /*
      if( (s==null || s.length()==0) && required ) throw new IllegalArgumentException("Missing key");
      else if (!required && (s == null || s.length() == 0)) return null;
      else {
      Value v = DKV.get(s);
      if (null == v) return null; // not required
      if (! v.isModel()) throw new IllegalArgumentException("Model argument points to a non-model object.");
      return v.get();
      }
    */
    throw H2O.fail("Unimplemented schema fill from " + fclz.getSimpleName());
  } // parse()

  /**
   * Helper functions for parse()
   **/

  /**
   * Parses a string into an integer data type specified by parameter return_type. Accepts any format that
   * is accepted by java's BigDecimal class.
   *  - Throws a NumberFormatException if the evaluated string is not an integer or if the value is too large to
   *    be stored into return_type without overflow.
   *  - Throws an IllegalAgumentException if return_type is not an integer data type.
   **/
  static private <T> T parseInteger(String s, Class<T> return_type) {
    try {
      java.math.BigDecimal num = new java.math.BigDecimal(s);
      T result = (T) num.getClass().getDeclaredMethod(return_type.getSimpleName() + "ValueExact", new Class[0]).invoke(num);
      return result;
    } catch (InvocationTargetException ite) {
      throw new NumberFormatException("The expression's numeric value is out of the range of type " + return_type.getSimpleName());
    } catch (NoSuchMethodException nsme) {
      throw new IllegalArgumentException(return_type.getSimpleName() + " is not an integer data type");
    } catch (IllegalAccessException iae) {
      throw H2O.fail("Cannot parse expression as " + return_type.getSimpleName() + " (Illegal Access)");
    }
  }
  
  static private int read( String s, int x, char c, Class fclz ) {
    if( peek(s,x,c) ) return x+1;
    throw new IllegalArgumentException("Expected '"+c+"' while reading a "+fclz.getSimpleName()+", but found "+s);
  }
  static private boolean peek( String s, int x, char c ) { return x < s.length() && s.charAt(x) == c; }

  // Splits on commas, but ignores commas in double quotes.  Required
  // since using a regex blow the stack on long column counts
  // TODO: detect and complain about malformed JSON
  private static String[] splitArgs(String argStr) {
    StringBuilder sb = new StringBuilder(argStr);
    StringBuilder arg = new StringBuilder();
    List<String> splitArgList = new ArrayList<String> ();
    boolean inDoubleQuotes = false;
    boolean inSquareBrackets = false; // for arrays of arrays

    for (int i=0; i < sb.length(); i++) {
      if (sb.charAt(i) == '"' && !inDoubleQuotes && !inSquareBrackets) {
        inDoubleQuotes = true;
        arg.append(sb.charAt(i));
      } else if (sb.charAt(i) == '"' && inDoubleQuotes && !inSquareBrackets) {
        inDoubleQuotes = false;
        arg.append(sb.charAt(i));
      } else if (sb.charAt(i) == ',' && !inDoubleQuotes && !inSquareBrackets) {
        splitArgList.add(arg.toString());
        // clear the field for next word
        arg.setLength(0);
      } else if (sb.charAt(i) == '[') {
        inSquareBrackets = true;
        arg.append(sb.charAt(i));
      } else if (sb.charAt(i) == ']') {
        inSquareBrackets = false;
        arg.append(sb.charAt(i));
      } else {
        arg.append(sb.charAt(i));
      }
    }
    if (arg.length() > 0)
      splitArgList.add(arg.toString());

    return splitArgList.toArray(new String[splitArgList.size()]);
  }

  /**
   * Returns a new Schema instance.  Does not throw, nor returns null.
   * @return New instance of Schema Class 'clz'.
   */
  public static <T extends Schema> T newInstance(Class<T> clz) {
    try { return clz.newInstance(); }
    catch (Exception e) { throw H2O.fail("Failed to instantiate schema of class: " + clz.getCanonicalName(),e); }
  }

  /**
   * For a given schema_name (e.g., "FrameV2") return an appropriate new schema object (e.g., a water.api.Framev2).
   */
  protected static Schema newInstance(String schema_name) {
    return Schema.newInstance(SchemaServer.getSchema(schema_name));
  }


  /**
   * Generate Markdown documentation for this Schema possibly including only the input or output fields.
   * @throws H2ONotFoundArgumentException if reflection on a field fails
   */
  public StringBuffer markdown(boolean include_input_fields, boolean include_output_fields) {
    return markdown(new SchemaMetadata(this), include_input_fields, include_output_fields);
  }

  /**
   * Generate Markdown documentation for this Schema, given we already have the metadata constructed.
   * @throws H2ONotFoundArgumentException if reflection on a field fails
   */
  public StringBuffer markdown(SchemaMetadata meta, boolean include_input_fields, boolean include_output_fields) {
    MarkdownBuilder builder = new MarkdownBuilder();

    builder.comment("Preview with http://jbt.github.io/markdown-editor");
    builder.heading1("schema ", this.getClass().getSimpleName());
    builder.hline();
    // builder.paragraph(metadata.summary);

    // TODO: refactor with Route.markdown():

    // fields
    boolean first; // don't print the table at all if there are no rows

    try {
      if (include_input_fields) {
        first = true;
        builder.heading2("input fields");

        for (SchemaMetadata.FieldMetadata field_meta : meta.fields) {
          if (field_meta.direction == API.Direction.INPUT || field_meta.direction == API.Direction.INOUT) {
            if (first) {
              builder.tableHeader("name", "required?", "level", "type", "schema?", "schema", "default", "description", "values", "is member of frames", "is mutually exclusive with");
              first = false;
            }
            builder.tableRow(
                    field_meta.name,
                    String.valueOf(field_meta.required),
                    field_meta.level.name(),
                    field_meta.type,
                    String.valueOf(field_meta.is_schema),
                    field_meta.is_schema ? field_meta.schema_name : "", (null == field_meta.value ? "(null)" : field_meta.value.toString()), // Something better for toString()?
                    field_meta.help,
                    (field_meta.values == null || field_meta.values.length == 0 ? "" : Arrays.toString(field_meta.values)),
                    (field_meta.is_member_of_frames == null ? "[]" : Arrays.toString(field_meta.is_member_of_frames)),
                    (field_meta.is_mutually_exclusive_with == null ? "[]" : Arrays.toString(field_meta.is_mutually_exclusive_with))
            );
          }
        }
        if (first)
          builder.paragraph("(none)");
      }

      if (include_output_fields) {
        first = true;
        builder.heading2("output fields");
        for (SchemaMetadata.FieldMetadata field_meta : meta.fields) {
          if (field_meta.direction == API.Direction.OUTPUT || field_meta.direction == API.Direction.INOUT) {
            if (first) {
              builder.tableHeader("name", "type", "schema?", "schema", "default", "description", "values", "is member of frames", "is mutually exclusive with");
              first = false;
            }
            builder.tableRow(
                    field_meta.name,
                    field_meta.type,
                    String.valueOf(field_meta.is_schema),
                    field_meta.is_schema ? field_meta.schema_name : "",
                    (null == field_meta.value ? "(null)" : field_meta.value.toString()), // something better than toString()?
                    field_meta.help,
                    (field_meta.values == null || field_meta.values.length == 0 ? "" : Arrays.toString(field_meta.values)),
                    (field_meta.is_member_of_frames == null ? "[]" : Arrays.toString(field_meta.is_member_of_frames)),
                    (field_meta.is_mutually_exclusive_with == null ? "[]" : Arrays.toString(field_meta.is_mutually_exclusive_with)));
          }
        }
        if (first)
          builder.paragraph("(none)");
      }

      // TODO: render examples and other stuff, if it's passed in
    }
    catch (Exception e) {
      IcedHashMap.IcedHashMapStringObject values = new IcedHashMap.IcedHashMapStringObject();
      values.put("schema", this);
      // TODO: This isn't quite the right exception type:
      throw new H2OIllegalArgumentException("Caught exception using reflection on schema: " + this,
                                            "Caught exception using reflection on schema: " + this + ": " + e,
                                            values);
    }
    return builder.stringBuffer();
  }
}
