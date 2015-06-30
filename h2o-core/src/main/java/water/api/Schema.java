package water.api;

import hex.schemas.ModelBuilderSchema;
import org.reflections.Reflections;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.exceptions.H2ONotFoundArgumentException;
import water.fvec.Frame;
import water.util.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Base Schema class.  All REST API Schemas inherit from here.
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
 * Both the Schema and the Iced object may have children, or (more often) not.
 * Occasionally, e.g. in the case of schemas used only to handle HTTP request
 * parameters, there will not be a backing impl object and the Schema will be
 * parameterized by Iced.
 * <p>
 * Schemas have a State section (broken into Input, Output and InOut fields)
 * and an Adapter section.  The adapter methods fill the State to and from the
 * Iced impl objects and from HTTP request parameters.  In the simple case, where
 * the backing object corresponds 1:1 with the Schema and no adapting need be
 * done, the methods here in the Schema class will do all the work based on
 * reflection.
 * <p>
 * Methods here allow us to convert from Schema to Iced (impl) and back in a
 * flexible way.  The default behaviour is to map like-named fields back and
 * forth, often with some default type conversions (e.g., a Keyed object like a
 * Model will be automagically converted back and forth to a Key).
 * Subclasses can override methods such as fillImpl or fillFromImpl to
 * provide special handling when adapting from schema to impl object and back.
 * Usually they will want to call super to get the default behavior, and then
 * modify the results a bit (e.g., to map differently-named fields, or to
 * compute field values).
 * <p>
 * Schema Fields must have a single API annotation describing their direction
 * of operation and any other properties such as "required".  Fields are
 * API.Direction.OUTPUT by default.  Transient and static fields are ignored.
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
public class Schema<I extends Iced, S extends Schema<I,S>> extends Iced {
  protected transient Class<I> _impl_class = getImplClass(); // see getImplClass()
  private static final int HIGHEST_SUPPORTED_VERSION = 3;
  private static final int EXPERIMENTAL_VERSION = 99;
  public static final String EXCLUDE_FIELDS = "__exclude_fields";
  public static final String INCLUDE_FIELDS = "__include_fields";

  public static final class Meta extends Iced {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CAREFUL: This class has its own JSON serializer.  If you add a field here you probably also want to add it to the serializer!
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @API(help="Version number of this Schema.  Must not be changed after creation (treat as final).", direction=API.Direction.OUTPUT)
    public int schema_version;

    /** The simple schema (class) name, e.g. DeepLearningParametersV2, used in the schema metadata.  Must not be changed after creation (treat as final).  */
    @API(help="Simple name of this Schema.  NOTE: the schema_names form a single namespace.", direction=API.Direction.OUTPUT)
    public String schema_name;

    @API(help="Simple name of H2O type that this Schema represents.  Must not be changed after creation (treat as final).", direction=API.Direction.OUTPUT)
    public String schema_type; // subclasses can redefine this

    public Meta() {}
    public Meta(int version, String name, String type) {
      this.schema_version = version;
      this.schema_name = name;
      this.schema_type = type;
    }

    @Override
    public final water.AutoBuffer writeJSON_impl(water.AutoBuffer ab) {
      // Overridden because otherwise we get in a recursive loop trying to serialize this$0.
      ab.putJSON4("schema_version", schema_version)
        .put1(',').putJSONStr("schema_name", schema_name)
        .put1(',').putJSONStr("schema_type", schema_type);
      return ab;
    }
  }

  @API(help="Metadata on this schema instance, to make it self-describing.", direction=API.Direction.OUTPUT)
  public Meta __meta = null;

  // Registry which maps a simple schema name to its class.  NOTE: the simple names form a single namespace.
  // E.g., "DeepLearningParametersV2" -> hex.schemas.DeepLearningV2.DeepLearningParametersV2
  private static Map<String, Class<? extends Schema>> schemas = new HashMap<>();

  // Registry which maps a Schema simpleName to its Iced Class.
  // E.g., "DeepLearningParametersV2" -> hex.deeplearning.DeepLearning.DeepLearningParameters
  private static Map<String, Class<? extends Iced>> schema_to_iced = new HashMap<>();

  // Registry which maps an Iced simpleName (type) and schema_version to its Schema Class.
  // E.g., (DeepLearningParameters, 2) -> "DeepLearningParametersV2"
  //
  // Note that iced_to_schema gets lazily filled if a higher version is asked for than is
  // available (e.g., if the highest version of Frame is FrameV2 and the client asks for
  // the schema for (Frame, 17) then FrameV2 will be returned, and all the mappings between
  // 17 and 3 will get added to the Map.
  private static Map<Pair<String, Integer>, Class<? extends Schema>> iced_to_schema = new HashMap<>();

  public Schema() {
    String name = this.getClass().getSimpleName();
    int version = extractVersion(name);
    String type = _impl_class.getSimpleName();

    __meta = new Meta(version, name, type);

    if (null == schema_to_iced.get(name)) {
      Log.debug("Registering schema: " + name + " schema_version: " + version + " with Iced class: " + _impl_class.toString());
      if (null != schemas.get(name))
        throw H2O.fail("Found a duplicate schema name in two different packages: " + schemas.get(name) + " and: " + this.getClass().toString());

      schemas.put(name, this.getClass());
      schema_to_iced.put(name, _impl_class);

      if (_impl_class != Iced.class) {
        Pair versioned = new Pair(type, version);
        // Check for conflicts
        if (null != iced_to_schema.get(versioned)) {
          throw H2O.fail("Found two schemas mapping to the same Iced class with the same version: " + iced_to_schema.get(versioned) + " and: " + this.getClass().toString() + " both map to version: " + version + " of Iced class: " + _impl_class);
        }
        iced_to_schema.put(versioned, this.getClass());
      }
    }
  }

  protected static Pattern _version_pattern = null;
  /** Extract the version number from the schema class name.  Returns -1 if there's no version number at the end of the classname. */
  public static int extractVersion(String clz_name) {
    if (null == _version_pattern) // can't just use a static initializer
      _version_pattern = Pattern.compile(".*V(\\d+)");
    Matcher m =  _version_pattern.matcher(clz_name);
    if (! m.matches()) return -1;
    return Integer.valueOf(m.group(1));
  }

  public int getSchemaVersion() {
    return __meta.schema_version;
  }

  /** Helper to fetch the class name in a static initializer block. */
  private static class CurClassNameGetter extends SecurityManager {
    public Class getClz(){
      return getClassContext()[1];
    }
  }


  private static int latest_version = -1;
  public final static int getLatestVersion() {
    return latest_version;
  }

  // Bound the version search if we haven't yet registered all schemas
  public final static int getHighestSupportedVersion() {
    return HIGHEST_SUPPORTED_VERSION;
  }

  public final static int getExperimentalVersion() {
    return EXPERIMENTAL_VERSION;
  }

  /** Register the given schema class. */
  public static void register(Class<? extends Schema> clz) {
    synchronized(clz) {
      // Was there a race to get here?  If so, return.
      Class<? extends Schema> existing = schemas.get(clz.getSimpleName());
      if (null != existing) {
        if (clz != existing)
          throw H2O.fail("Two schema classes have the same simpleName; this is not supported: " + clz + " and " + existing + ".");
        return;
      }

      // Check that the Schema has the correct type parameters:
      if (clz.getGenericSuperclass() instanceof ParameterizedType) {
        Type[] schema_type_parms = ((ParameterizedType) (clz.getGenericSuperclass())).getActualTypeArguments();
        if (schema_type_parms.length < 2)
          throw H2O.fail("Found a Schema that does not pass at least two type parameters.  Each Schema needs to be parameterized on the backing class (if any, or Iced if not) and itself: " + clz);
        Class parm0 = ReflectionUtils.findActualClassParameter(clz, 0);
        if (!Iced.class.isAssignableFrom(parm0))
          throw H2O.fail("Found a Schema with bad type parameters.  First parameter is a subclass of Iced.  Each Schema needs to be parameterized on the backing class (if any, or Iced if not) and itself: " + clz + ".  Second parameter is of class: " + parm0);
        if (Schema.class.isAssignableFrom(parm0))
          throw H2O.fail("Found a Schema with bad type parameters.  First parameter is a subclass of Schema.  Each Schema needs to be parameterized on the backing class (if any, or Iced if not) and itself: " + clz + ".  Second parameter is of class: " + parm0);

        Class parm1 = ReflectionUtils.findActualClassParameter(clz, 1);
        if (!Schema.class.isAssignableFrom(parm1))
          throw H2O.fail("Found a Schema with bad type parameters.  Second parameter is not a subclass of Schema.  Each Schema needs to be parameterized on the backing class (if any, or Iced if not) and itself: " + clz + ".  Second parameter is of class: " + parm1);
      } else {
        throw H2O.fail("Found a Schema that does not have a parameterized superclass.  Each Schema needs to be parameterized on the backing class (if any, or Iced if not) and itself: " + clz);
      }

      int version = extractVersion(clz.getSimpleName());
      if (version > getHighestSupportedVersion() && version != EXPERIMENTAL_VERSION)
        throw H2O.fail("Found a schema with a version higher than the highest supported version; you probably want to bump the highest supported version: " + clz);

      // NOTE: we now allow non-versioned schemas, for example base classes like ModelMetricsBase, so that we can fetch the metadata for them.
      if (version > -1 && version != EXPERIMENTAL_VERSION) {
        // Track highest version of all schemas; only valid after all are registered at startup time.
        if (version > HIGHEST_SUPPORTED_VERSION)
          throw H2O.fail("Found a schema with a version greater than the highest supported version of: " + getHighestSupportedVersion() + ": " + clz);

        if (version > latest_version) {
          synchronized (Schema.class) {
            if (version > latest_version) latest_version = version;
          }
        }
      }

      Schema s = null;
      try {
        s = clz.newInstance();
      } catch (Exception e) {
        Log.err("Failed to instantiate schema class: " + clz + " because: " + e);
      }
      if (null != s) {
        Log.debug("Registered Schema: " + clz.getSimpleName());

        // Validate the fields:
        SchemaMetadata meta = new SchemaMetadata(s);

        for (SchemaMetadata.FieldMetadata field_meta : meta.fields) {
          String name = field_meta.name;

          if ("__meta".equals(name) || "__http_status".equals(name) || "_exclude_fields".equals(name) || "_include_fields".equals(name))
            continue;
          if ("Gini".equals(name)) // proper name
            continue;

          if (name.endsWith("AUC")) // trainAUC, validAUC
            continue;

          // TODO: remove after we move these into a TwoDimTable:
          if ("F0point5".equals(name) || "F0point5_for_criteria".equals(name) || "F1_for_criteria".equals(name) || "F2_for_criteria".equals(name))
            continue;

          if (name.startsWith("_"))
            Log.warn("Found schema field which violates the naming convention; name starts with underscore: " + meta.name + "." + name);
          if (!name.equals(name.toLowerCase()) && !name.equals(name.toUpperCase())) // allow AUC but not residualDeviance
            Log.warn("Found schema field which violates the naming convention; name has mixed lowercase and uppercase characters: " + meta.name + "." + name);
        }
      }
    }
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
      return this.getImplClass().newInstance();
    }
    catch (Exception e) {
      String msg = "Exception instantiating implementation object of class: " + this.getImplClass().toString() + " for schema class: " + this.getClass();
      Log.err(msg + ": " + e);
      throw H2O.fail(msg, e);
    }
  }

  /** Fill an impl object and any children from this schema and its children. */
  public I fillImpl(I impl) {
    PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.CONSISTENT); // TODO: make consistent and remove
    PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
    return impl;
  }

  /** Convenience helper which creates and fill an impl. */
  final public I createAndFillImpl() {
    return (I)this.fillImpl(this.createImpl());
  }

  // TODO: we need to pass in the version from the request so the superclass can create versioned sub-schemas.  See the *Base
  /** Version and Schema-specific filling from the implementation object. */
  public S fillFromImpl(I impl) {
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.CONSISTENT);  // TODO: make consistent and remove
    return (S)this;
  }

  public static Class<? extends Iced> getImplClass(Class<? extends Schema> clz) {
    Class<? extends Iced> impl_class = (Class<? extends Iced>)ReflectionUtils.findActualClassParameter(clz, 0);
    if (null == impl_class)
      Log.warn("Failed to find an impl class for Schema: " + clz);
    return impl_class;
  }

  public Class<I> getImplClass() {
    if (null == _impl_class)
      _impl_class = (Class<I>) ReflectionUtils.findActualClassParameter(this.getClass(), 0);

    if (null == _impl_class)
      Log.warn("Failed to find an impl class for Schema: " + this.getClass());

    return _impl_class;
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
    Field current = null; // declare here so we can print in catch{}
    try {
      Class clz = getClass();
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
        API api = (API)f.getAnnotations()[0];
        // Must have one of these set to be an input field
        if( api.direction() == API.Direction.OUTPUT ) {
          throw new H2OIllegalArgumentException(
                  "Attempting to set output field: " + key + " for class: " + this.getClass().toString(),
                  "Attempting to set output field: " + key + " in fillFromParms for class: " + this.getClass().toString() + " (field was annotated as API.Direction.OUTPUT)");
        }

        // Primitive parse by field type
        Object parse_result = parse(key, parms.getProperty(key),f.getType(), api.required());
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
          } else if (parse_result.getClass().getComponentType() == Double.class && f.getType().getComponentType() == double.class) {
            Double[] from = (Double[])parse_result;
            double[] copy = new double[from.length];
            for (int i = 0; i < from.length; i++)
              copy[i] = from[i];
            f.set(this, copy);
          } else if (parse_result.getClass().getComponentType() == Float.class && f.getType().getComponentType() == float.class) {
            Float[] from = (Float[])parse_result;
            float[] copy = new float[from.length];
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
        throw H2O.fail("Broken internal schema; missing API annotation for field: " + key);
      } catch( IllegalAccessException iae ) {
        // Come here if field is final or private
        throw H2O.fail("Broken internal schema; field cannot be private nor final: " + key);
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
          if (parms.getProperty(f.getName()) == null) {
            IcedHashMap.IcedHashMapStringObject values = new IcedHashMap.IcedHashMapStringObject();
            values.put("schema", this.getClass().getSimpleName());
            values.put("argument", f.getName());
            throw new H2OIllegalArgumentException("Required field " + f.getName() + " not specified",
                    "Required field " + f.getName() + " not specified for schema class: " + this.getClass(),
                    values);
          }
        }
      }
      catch (ArrayIndexOutOfBoundsException e) {
        throw H2O.fail("Missing annotation for API field: " + f.getName());
      }
    }
    return (S)this;
  }

  // URL parameter parse
  private <E> Object parse( String field_name, String s, Class fclz, boolean required ) {
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

      String inside = s.substring(1,s.length() -1).trim();
      String[] splits; // "".split(",") => {""} so handle the empty case explicitly
      if (inside.length() == 0)
        splits = new String[] {};
      else
        splits = splitArgs(inside);
      Class<E> afclz = (Class<E>)fclz.getComponentType();
      E[] a = null;
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

      for( int i=0; i<splits.length; i++ ) {
        if (String.class == afclz || KeyV3.class.isAssignableFrom(afclz)) {
          // strip quotes off string values inside array
          String stripped = splits[i].trim();

          if ("null".equals(stripped)) {
            a[i] = null;
          } else if (! stripped.startsWith("\"") || ! stripped.endsWith("\"")) {
            String msg = "Illegal argument for field: " + field_name + " of schema: " + this.getClass().getSimpleName() + ": string and key arrays' values must be double quoted, but the client sent: " + stripped;

            IcedHashMap.IcedHashMapStringObject values = new IcedHashMap.IcedHashMapStringObject();
            values.put("function", fclz.getSimpleName() + ".fillFromParms()");
            values.put("argument", field_name);
            values.put("value", stripped);

            throw new H2OIllegalArgumentException(msg, msg, values);
          }

          stripped = stripped.substring(1, stripped.length() - 1);
          a[i] = (E) parse(field_name, stripped, afclz, required);
        } else {
          a[i] = (E) parse(field_name, splits[i].trim(), afclz, required);
        }
      }
      return a;
    }

    if( fclz.equals(Key.class) )
      if( (s==null || s.length()==0) && required ) throw new H2OKeyNotFoundArgumentException(field_name, s);
      else if (!required && (s == null || s.length() == 0)) return null;
      else return Key.make(s.startsWith("\"") ? s.substring(1, s.length() - 1) : s); // If the key name is in an array we need to trim surrounding quotes.

    if( KeyV3.class.isAssignableFrom(fclz) ) {
      if ((s == null || s.length() == 0) && required) throw new H2OKeyNotFoundArgumentException(field_name, s);
      if (!required && (s == null || s.length() == 0)) return null;

      return KeyV3.make(fclz, Key.make(s.startsWith("\"") ? s.substring(1, s.length() - 1) : s)); // If the key name is in an array we need to trim surrounding quotes.
    }

    if( Enum.class.isAssignableFrom(fclz) )
      return Enum.valueOf(fclz,s);

    // TODO: these can be refactored into a single case using the facilities in Schema:
    if( FrameV3.class.isAssignableFrom(fclz) )
      if( (s==null || s.length()==0) && required ) throw new H2OKeyNotFoundArgumentException(field_name, s);
      else if (!required && (s == null || s.length() == 0)) return null;
      else {
        Value v = DKV.get(s);
        if (null == v) return null; // not required
        if (! v.isFrame()) throw H2OIllegalArgumentException.wrongKeyType(field_name, s, "Frame", v.get().getClass());
        return new FrameV3((Frame) v.get()); // TODO: version!
      }

    if( JobV3.class.isAssignableFrom(fclz) )
      if( (s==null || s.length()==0) && required ) throw new H2OKeyNotFoundArgumentException(s);
      else if (!required && (s == null || s.length() == 0)) return null;
      else {
        Value v = DKV.get(s);
        if (null == v) return null; // not required
        if (! v.isJob()) throw H2OIllegalArgumentException.wrongKeyType(field_name, s, "Job", v.get().getClass());
        return new JobV3().fillFromImpl((Job) v.get()); // TODO: version!
      }

    // TODO: for now handle the case where we're only passing the name through; later we need to handle the case
    // where the frame name is also specified.
    if ( FrameV3.ColSpecifierV3.class.isAssignableFrom(fclz)) {
        return new FrameV3.ColSpecifierV3(s);
    }

    if( ModelSchema.class.isAssignableFrom(fclz) )
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

    throw H2O.fail("Unimplemented schema fill from "+fclz.getSimpleName());
  }
  private int read( String s, int x, char c, Class fclz ) {
    if( peek(s,x,c) ) return x+1;
    throw new IllegalArgumentException("Expected '"+c+"' while reading a "+fclz.getSimpleName()+", but found "+s);
  }
  private boolean peek( String s, int x, char c ) { return x < s.length() && s.charAt(x) == c; }

  // Splits on commas, but ignores commas in double quotes.  Required
  // since using a regex blow the stack on long column counts
  // TODO: detect and complain about malformed JSON
  private static String[] splitArgs(String argStr) {
    StringBuffer sb = new StringBuffer (argStr);
    StringBuffer arg = new StringBuffer ();
    List<String> splitArgList = new ArrayList<String> ();
    boolean inDoubleQuotes = false;

    for (int i=0; i < sb.length(); i++) {
      if (sb.charAt (i) == '"' && !inDoubleQuotes) {
        inDoubleQuotes = true;
        arg.append(sb.charAt(i));
      } else if (sb.charAt(i) == '"' && inDoubleQuotes) {
        inDoubleQuotes = false;
        arg.append(sb.charAt(i));
      } else if (sb.charAt(i) == ',' && !inDoubleQuotes) {
        splitArgList.add(arg.toString());
        // clear the field for next word
        arg.setLength(0);
      } else {
        arg.append(sb.charAt(i));
      }
    }
    if (arg.length() > 0)
      splitArgList.add(arg.toString());

    return splitArgList.toArray(new String[splitArgList.size()]);
  }

  private static boolean schemas_registered = false;
  /**
   * Find all schemas using reflection and register them.
   */
  synchronized static public void registerAllSchemasIfNecessary() {
    if (schemas_registered) return;
    // if (!Paxos._cloudLocked) return; // TODO: It's never getting locked. . . :-(

    long before = System.currentTimeMillis();

    // Microhack to effect Schema.register(Schema.class), which is
    // normally not allowed because it has no version:
    new Schema();

    String[] packages = new String[] { "water", "hex", /* Disallow schemas whose parent is in another package because it takes ~4s to do the getSubTypesOf call: "" */};

    // For some reason when we're run under Hadoop Reflections is failing to find some of the classes unless we're extremely explicit here:
    Class<? extends Schema> clzs[] = new Class[] { Schema.class, ModelBuilderSchema.class, ModelSchema.class, ModelOutputSchema.class, ModelParametersSchema.class };

    for (String pkg :  packages) {
      Reflections reflections = new Reflections(pkg);

      for (Class<? extends Schema> clz : clzs) {
        // NOTE: Reflections sees ModelOutputSchema but not ModelSchema. Another bug to work around:
        Log.debug("Registering: " + clz.toString() + " in package: " + pkg);
        if (!Modifier.isAbstract(clz.getModifiers()))
          Schema.register(clz);

        // Register the subclasses:
        Log.debug("Registering subclasses of: " + clz.toString() + " in package: " + pkg);
        for (Class<? extends Schema> schema_class : reflections.getSubTypesOf(clz))
          if (!Modifier.isAbstract(schema_class.getModifiers()))
            Schema.register(schema_class);
      }
    }

    schemas_registered = true;
    Log.info("Registered: " + Schema.schemas().size() + " schemas in: " + (System.currentTimeMillis() - before) + "mS");
  }

  /**
   * Return an immutable Map of all the schemas: schema_name -> schema Class.
   */
  public static Map<String, Class<? extends Schema>> schemas() {
    return Collections.unmodifiableMap(new HashMap<>(schemas));
  }

  /**
   * For a given version and Iced object return the appropriate Schema class, if any.
   * @see #schemaClass(int, java.lang.String)
   */
  public static Class<? extends Schema> schemaClass(int version, Iced impl) {
    return schemaClass(version, impl.getClass().getSimpleName());
  }

  /**
   * For a given version and Iced class return the appropriate Schema class, if any.f
   * @see #schemaClass(int, java.lang.String)
   */
  public static Class<? extends Schema> schemaClass(int version, Class<? extends Iced> impl_class) {
    return schemaClass(version, impl_class.getSimpleName());
  }

  /**
   * For a given version and type (Iced class simpleName) return the appropriate Schema
   * class, if any.
   * <p>
   * If a higher version is asked for than is available (e.g., if the highest version of
   * Frame is FrameV2 and the client asks for the schema for (Frame, 17) then FrameV2 will
   * be returned.  This compatibility lookup is cached.
   */
  public static Class<? extends Schema> schemaClass(int version, String type) {
    if (version < 1) return null;

    Class<? extends Schema> clz = iced_to_schema.get(new Pair(type, version));

    if (null != clz) return clz; // found!

    clz = schemaClass(version - 1, type);

    if (null != clz) iced_to_schema.put(new Pair(type, version), clz); // found a lower-numbered schema: cache
    return clz;
  }

  /**
   * For a given schema_name (e.g., "FrameV2") return the schema class (e.g., water.api.Framev2).
   */
  public static Class<? extends Schema>  schemaClass(String schema_name) {
    return schemas.get(schema_name);
  }

  /**
   * For a given version and Iced object return an appropriate Schema instance, if any.
   * @see #schema(int, java.lang.String)
   */
  public static Schema schema(int version, Iced impl) {
    return schema(version, impl.getClass().getSimpleName());
  }

  /**
   * For a given version and Iced class return an appropriate Schema instance, if any.
   * @see #schema(int, java.lang.String)
   */
  public static Schema schema(int version, Class<? extends Iced> impl_class) {
    return schema(version, impl_class.getSimpleName());
  }

  // FIXME: can be parameterized by type: public static <T extends Schema> T newInstance(Class<T> clz)
  public static Schema newInstance(Class<? extends Schema> clz) {
    Schema s = null;
    try {
      s = clz.newInstance();
    }
    catch (Exception e) {
      H2O.fail("Failed to instantiate schema of class: " + clz.getCanonicalName());
    }
    return s;
  }

  /**
   * For a given version and type (Iced class simpleName) return an appropriate new Schema
   * object, if any.
   * <p>
   * If a higher version is asked for than is available (e.g., if the highest version of
   * Frame is FrameV2 and the client asks for the schema for (Frame, 17) then an instance
   * of FrameV2 will be returned.  This compatibility lookup is cached.
   */
  public static Schema schema(int version, String type) {
    Class<? extends Schema> clz = schemaClass(version, type);
    if (null == clz) throw new H2ONotFoundArgumentException("Failed to find schema for version: " + version + " and type: " + type,
                                                            "Failed to find schema for version: " + version + " and type: " + type);
    return Schema.newInstance(clz);
  }

  /**
   * For a given schema_name (e.g., "FrameV2") return an appropriate new schema object (e.g., a water.api.Framev2).
   */
  public static Schema schema(String schema_name) {
    Class<? extends Schema> clz = schemas.get(schema_name);
    if (null == clz) throw new H2ONotFoundArgumentException("Failed to find schema for schema_name: " + schema_name,
                                                            "Failed to find schema for schema_name: " + schema_name);
    return Schema.newInstance(clz);
  }

  /**
   * For a given schema class (e.g., water.api.FrameV2) return a new instance (e.g., a water.api.Framev2).
   */
  public static Schema schema(Class<? extends Schema> clz) {
    return Schema.newInstance(clz);
  }

  /**
   * Generate Markdown documentation for this Schema.
   */
  public StringBuffer markdown(StringBuffer appendToMe) {
    return markdown(appendToMe, true, true);
  }

  /**
   * Generate Markdown documentation for this Schema possibly including only the input or output fields.
   */
  public StringBuffer markdown(StringBuffer appendToMe, boolean include_input_fields, boolean include_output_fields) {
    return markdown(new SchemaMetadata(this), appendToMe, include_input_fields, include_output_fields);
  }

  public StringBuffer markdown(SchemaMetadata meta, StringBuffer appendToMe) {
    return markdown(meta, appendToMe, true, true);
  }

  /**
   * Generate Markdown documentation for this Schema, given we already have the metadata constructed.
   */
  public StringBuffer markdown(SchemaMetadata meta , StringBuffer appendToMe, boolean include_input_fields, boolean include_output_fields) {
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

    if (null != appendToMe)
      appendToMe.append(builder.stringBuffer());

    return builder.stringBuffer();
  } // markdown()

}
