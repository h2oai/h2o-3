package water.api;

import water.H2O;
import water.Iced;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2ONotFoundArgumentException;
import water.util.Log;
import water.util.Pair;
import water.util.ReflectionUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class SchemaServer {

  private static final int HIGHEST_SUPPORTED_VERSION = 4;
  private static final int EXPERIMENTAL_VERSION = 99;
  private static final int STABLE_VERSION = 3;
  private static int LATEST_VERSION = -1;
  private static boolean schemas_registered = false;

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


  /**
   * Get the highest schema version number that we've encountered during schema registration.
   */
  public static int getLatestVersion() { return LATEST_VERSION; }

  /**
   * Get the highest schema version that we support.  This bounds the search for a schema if we haven't yet
   * registered all schemas and don't yet know the latest_version.
   */
  public static int getHighestSupportedVersion() { return HIGHEST_SUPPORTED_VERSION; }

  /**
   * Combines getLatestVersion() and getHighestSupportedVersion().
   */
  public static int getLatestOrHighestSupportedVersion() {
    return LATEST_VERSION == -1? HIGHEST_SUPPORTED_VERSION : LATEST_VERSION;
  }

  /**
   * Get the experimental schema version, which indicates that a schema is not guaranteed to be stable between H2O
   * releases.
   */
  public static int getExperimentalVersion() { return EXPERIMENTAL_VERSION; }

  public static int getStableVersion() { return STABLE_VERSION; }


  public static void checkIfRegistered(Schema schema) {
    if (schemas_registered && !schema_to_iced.containsKey(schema.getSchemaName()))
      throw H2O.fail("Schema " + schema.getSchemaName() + " was instantiated before it was registered...\n"
                     + "Did you forget to add an entry into your META-INF/services/water.api.Schema file?");
  }


  /**
   * Register the given schema class.
   * @throws water.exceptions.H2OFailException if there is a name collision, if the type parameters are bad, or if
   *         the version is bad
   */
  private static void register(Schema schema) {
    Class clz = schema.getClass();
    synchronized(clz) {
      String clzname = clz.getSimpleName();

      // Was there a race to get here?  If so, return.
      Class<? extends Schema> existing = schemas.get(clzname);
      if (existing != null) {
        if (clz != existing)
          throw H2O.fail("Two schema classes have the same simpleName: " + clz + " and " + existing + ".");
        return;
      }

      // Check that the Schema has the correct type parameters:
      if (clz.getGenericSuperclass() instanceof ParameterizedType) {
        Type[] schema_type_parms = ((ParameterizedType) (clz.getGenericSuperclass())).getActualTypeArguments();
        if (schema_type_parms.length < 2)
          throw H2O.fail("Found a Schema that does not pass at least two type parameters.  Each Schema needs to be " +
              "parametrized on the backing class (if any, or Iced if not) and itself: " + clz);

        Class parm0 = ReflectionUtils.findActualClassParameter(clz, 0);
        Class parm1 = ReflectionUtils.findActualClassParameter(clz, 1);
        String clzstr = clzname + "<" + parm0.getSimpleName() + "," + parm1.getSimpleName() + ">";
        if (!Iced.class.isAssignableFrom(parm0))
          throw H2O.fail("Schema " + clzstr + " has bad type parameters: first arg should be a subclass of Iced");
        if (Schema.class.isAssignableFrom(parm0))
          throw H2O.fail("Schema " + clzstr + " has bad type parameters: first arg cannot be a Schema");
        if (!Schema.class.isAssignableFrom(parm1))
          throw H2O.fail("Schema " + clzstr + " has bad type parameters: second arg should be a subclass of Schema");
        if (!parm1.getSimpleName().equals(clzname))
          throw H2O.fail("Schema " + clzstr + " has bad type parameters: second arg should refer to the schema itself");

      } else {
        throw H2O.fail("Found a Schema that does not have a parametrized superclass.  Each Schema needs to be " +
            "parameterized on the backing class (if any, or Iced if not) and itself: " + clz);
      }

      // Check the version, and bump the LATEST_VERSION
      // NOTE: we now allow non-versioned schemas, for example base classes like ModelMetricsBaseV3, so that we can
      // fetch the metadata for them.
      int version = Schema.extractVersionFromSchemaName(clzname);
      if (version > HIGHEST_SUPPORTED_VERSION && version != EXPERIMENTAL_VERSION)
        throw H2O.fail("Found a schema with a version higher than the highest supported version; you probably want  " +
            "to bump the highest supported version: " + clz);
      if (version > LATEST_VERSION && version != EXPERIMENTAL_VERSION)
        synchronized (Schema.class) {
          if (version > LATEST_VERSION) LATEST_VERSION = version;
        }

      Class<? extends Iced> impl_class = ReflectionUtils.findActualClassParameter(clz, 0);
      Log.debug(String.format("Registering schema: %-40s  (v = %2d, impled by %s)",
                              clz.getCanonicalName(), version, impl_class.getCanonicalName()));
      schemas.put(clzname, clz);
      schema_to_iced.put(clzname, impl_class);

      // Check that it is possible to create a schema object
      try {
        // Validate the fields:
        SchemaMetadata meta = new SchemaMetadata(schema);
        for (SchemaMetadata.FieldMetadata field_meta : meta.fields) {
          String name = field_meta.name;

          // TODO: make a new @API field "ignore_naming_rules", and set to true for the names hardcoded below:
          //       After that these all checks could be eliminated...
          if (name.equals("__meta") || name.equals("__http_status") || name.equals("_exclude_fields") ||
              name.equals("__schema") || name.equals("_fields")) continue;
          if (name.equals("Gini")) continue; // proper name
          if (name.equals("pr_auc")) continue;
          if (name.endsWith("AUC")) continue; // trainAUC, validAUC

          // TODO: remove after we move these into a TwoDimTable:
          if ("f0point5".equals(name) || "f0point5_for_criteria".equals(name) || "f1_for_criteria".equals(name) ||
              "f2_for_criteria".equals(name)) continue;

          if (name.startsWith("_"))
            Log.warn("Found schema field which violates the naming convention; name starts with underscore: " +
                     meta.name + "." + name);
          // allow AUC but not residualDeviance
          // Note: class Word2VecParametersV3 is left as an exception, since it's already a published schema, and it
          // is not possible to alter its field names. However no other exceptions should be created!
          if (!name.equals(name.toLowerCase()) && !name.equals(name.toUpperCase()))
            if (!clzname.equals("Word2VecParametersV3"))
              Log.warn("Found schema field which violates the naming convention; name has mixed lowercase and " +
                       "uppercase characters: " + meta.name + "." + name);
        }
      } catch (Exception e) {
        throw H2O.fail("Failed to instantiate schema class " + clzname + " because: " + e);
      }

      if (impl_class != Iced.class) {
        Pair<String, Integer> versioned = new Pair<>(impl_class.getSimpleName(), version);
        // Check for conflicts
        // The check is invalid: there could be multiple schemas mapping to the same Iced object with the same version.
        // This is why all calls that depend on this mapping should ideally be eliminated (they cannot by
        // type-checked anyways, so they greatly increase bugginness of the code...)
//        if (iced_to_schema.containsKey(versioned))
//          throw H2O.fail("Found two schemas mapping to the same Iced class with the same version: " +
//              iced_to_schema.get(versioned) + " and " + clz + " both map to " +
//              "version: " + version + " of Iced class: " + impl_class);
        iced_to_schema.put(versioned, clz);
      }
    }
  }


  /**
   * Find all schemas using reflection and register them.
   */
  synchronized static public void registerAllSchemasIfNecessary(Schema ... schemas) {
    if (schemas_registered) return;
    long startTime = System.currentTimeMillis();
    for (Schema schema : schemas) {
      register(schema);
    }
        
    Log.info("Registered: " + schemas().size() + " schemas in " + (System.currentTimeMillis() - startTime) + "ms");
    schemas_registered = true;
  }

  /**
   * Return an immutable Map of all the schemas: schema_name -> schema Class.
   */
  public static Map<String, Class<? extends Schema>> schemas() {
    return Collections.unmodifiableMap(new HashMap<>(schemas));
  }

  /**
   * Lookup schema by name.
   * @throws H2ONotFoundArgumentException if an appropriate schema is not found
   */
  public static Class<? extends Schema> getSchema(String name) {
    Class<? extends Schema> clz = schemas.get(name);
    if (clz == null)
      throw new H2ONotFoundArgumentException("Failed to find schema for schema_name: " + name,
          "Failed to find schema for schema_name: " + name + "\n" +
          "Did you forget to add an entry into META-INF/services/water.api.Schema?");
    return clz;
  }

  /**
   * For a given version and type (Iced class simpleName) return the appropriate Schema
   * class, if any.
   * <p>
   * If a higher version is asked for than is available (e.g., if the highest version of
   * Frame is FrameV2 and the client asks for the schema for (Frame, 17) then FrameV2 will
   * be returned.  This compatibility lookup is cached.
   * @deprecated
   */
  public static Class<? extends Schema> schemaClass(int version, String type) {
    if (version < 1) return null;
    Class<? extends Schema> clz = iced_to_schema.get(new Pair<>(type, version));

    if (clz != null) return clz; // found!

    clz = schemaClass(version==EXPERIMENTAL_VERSION? HIGHEST_SUPPORTED_VERSION : version-1, type);
    if (clz != null) iced_to_schema.put(new Pair<>(type, version), clz); // found a lower-numbered schema: cache
    return clz;
  }

  /**
   * For a given version and Iced object return an appropriate Schema instance, if any.
   * @see #schema(int, java.lang.String)
   * @deprecated
   */
  public static Schema schema(int version, Iced impl) {
    if (version == -1) version = getLatestVersion();
    return schema(version, impl.getClass().getSimpleName());
  }

  /**
   * For a given version and Iced class return an appropriate Schema instance, if any.
   * @param version Version of the schema to create, or pass -1 to use the latest version.
   * @param impl_class Create schema corresponds to this implementation class.
   * @throws H2OIllegalArgumentException if Class.newInstance() throws
   * @see #schema(int, java.lang.String)
   * @deprecated
   */
  public static Schema schema(int version, Class<? extends Iced> impl_class) {
    if (version == -1) version = getLatestVersion();
    return schema(version, impl_class.getSimpleName());
  }

  /**
   * For a given version and type (Iced class simpleName) return an appropriate new Schema object, if any.
   * <p>
   * If a higher version is asked for than is available (e.g., if the highest version of
   * Frame is FrameV2 and the client asks for the schema for (Frame, 17) then an instance
   * of FrameV2 will be returned.  This compatibility lookup is cached.
   * @throws H2ONotFoundArgumentException if an appropriate schema is not found
   * @deprecated
   */
  private static Schema schema(int version, String type) {
    Class<? extends Schema> clz = schemaClass(version, type);
    if (clz == null) clz = schemaClass(EXPERIMENTAL_VERSION, type);

    if (clz == null)
      throw new H2ONotFoundArgumentException("Failed to find schema for version: " + version + " and type: " + type,
          "Failed to find schema for version: " + version + " and type: " + type + "\n" +
          "Did you forget to add an entry into META-INF/services/water.api.Schema?");
    return Schema.newInstance(clz);
  }

}
