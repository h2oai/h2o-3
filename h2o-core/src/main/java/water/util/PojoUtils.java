package water.util;

import water.*;
import water.api.FrameV3;
import water.api.KeyV3;
import water.api.Schema;
import water.api.SchemaServer;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2ONotFoundArgumentException;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * POJO utilities which cover cases similar to but not the same as Apache Commons PojoUtils.
 */
public class PojoUtils {
  public enum FieldNaming {
    CONSISTENT {
      @Override String toDest(String origin) { return origin; }
      @Override String toOrigin(String dest) { return dest; }
    },
    DEST_HAS_UNDERSCORES {
      @Override String toDest(String origin) { return "_" + origin; }
      @Override String toOrigin(String dest) { return dest.substring(1); }
    },
    ORIGIN_HAS_UNDERSCORES {
      @Override String toDest(String origin) { return origin.substring(1); }
      @Override String toOrigin(String dest) { return "_" + dest; }
    };

    /**
     * Return destination name based on origin name.
     * @param origin name of origin argument
     * @return  return a name of destination argument.
     */
    abstract String toDest(String origin);

    /**
     * Return name of origin parameter derived from name of origin parameter.
     * @param dest  name of destination argument.
     * @return  return a name of origin argument.
     */
    abstract String toOrigin(String dest);
  }


  /**
   * Copy properties "of the same name" from one POJO to the other.  If the fields are
   * named consistently (both sides have fields named "_foo" and/or "bar") this acts like
   * Apache Commons PojoUtils.copyProperties(). If one side has leading underscores and
   * the other does not then the names are conformed according to the field_naming
   * parameter.
   *
   * It is also able to map fields between external types like Schema to their corresponding
   * internal types.
   *
   * @param dest Destination POJO
   * @param origin Origin POJO
   * @param field_naming Are the fields named consistently, or does one side have underscores?
   */
  public static void copyProperties(Object dest, Object origin, FieldNaming field_naming) {
    copyProperties(dest, origin, field_naming, null);
  }

  /**
   * Copy properties "of the same name" from one POJO to the other.  If the fields are
   * named consistently (both sides have fields named "_foo" and/or "bar") this acts like
   * Apache Commons PojoUtils.copyProperties(). If one side has leading underscores and
   * the other does not then the names are conformed according to the field_naming
   * parameter.
   *
   * @param dest Destination POJO
   * @param origin Origin POJO
   * @param field_naming Are the fields named consistently, or does one side have underscores?
   * @param skip_fields Array of origin or destination field names to skip
   */
  public static void copyProperties(Object dest, Object origin, FieldNaming field_naming, String[] skip_fields) {
    copyProperties(dest, origin, field_naming, skip_fields, null);
  }

  /**
   * Copy properties "of the same name" from one POJO to the other.  If the fields are
   * named consistently (both sides have fields named "_foo" and/or "bar") this acts like
   * Apache Commons PojoUtils.copyProperties(). If one side has leading underscores and
   * the other does not then the names are conformed according to the field_naming
   * parameter.
   *
   * @param dest Destination POJO
   * @param origin Origin POJO
   * @param field_naming Are the fields named consistently, or does one side have underscores?
   * @param skip_fields Array of origin or destination field names to skip
   * @param only_fields Array of origin or destination field names to include; ones not in this list will be skipped
   */
  public static void copyProperties(Object dest, Object origin, FieldNaming field_naming, String[] skip_fields, String[] only_fields) {
    if (null == dest || null == origin) return;

    Field[] dest_fields = Weaver.getWovenFields(dest  .getClass());
    Field[] orig_fields = Weaver.getWovenFields(origin.getClass());

    for (Field orig_field : orig_fields) {
      String origin_name = orig_field.getName();

      String dest_name = field_naming.toDest(origin_name);

      if (skip_fields != null && (ArrayUtils.contains(skip_fields, origin_name) || ArrayUtils.contains(skip_fields, dest_name)))
        continue;

      if (only_fields != null && ! (ArrayUtils.contains(only_fields, origin_name) || ArrayUtils.contains(only_fields, dest_name)))
        continue;

      try {
        Field dest_field = null;
        for( Field fd : dest_fields ) {
          if (fd.getName().equals(dest_name)) {
            dest_field = fd;
            break;
          }
        }

        if( dest_field != null ) {
          dest_field.setAccessible(true);
          orig_field.setAccessible(true);
          // Log.info("PojoUtils.copyProperties, origin field: " + orig_field + "; destination field: " + dest_field);
          if (null == orig_field.get(origin)) {
            //
            // Assigning null to dest.
            //
            dest_field.set(dest, null);
          } else if (dest_field.getType().isArray() && orig_field.getType().isArray() && (dest_field.getType().getComponentType() != orig_field.getType().getComponentType())) {
            //
            // Assigning an array to another array.
            //
            // You can't use reflection to set an int[] with an Integer[].  Argh.
            // TODO: other types of arrays. . .
            if (dest_field.getType().getComponentType() == double.class && orig_field.getType().getComponentType() == Double.class) {
              //
              // Assigning an Double[] to an double[]
              //
              double[] copy = (double[]) orig_field.get(origin);
              dest_field.set(dest, copy);
            } else if (dest_field.getType().getComponentType() == Double.class && orig_field.getType().getComponentType() == double.class) {
              //
              // Assigning an double[] to an Double[]
              //
              Double[] copy = (Double[]) orig_field.get(origin);
              dest_field.set(dest, copy);
            } else if (dest_field.getType().getComponentType() == int.class && orig_field.getType().getComponentType() == Integer.class) {
              //
              // Assigning an Integer[] to an int[]
              //
              int[] copy = (int[]) orig_field.get(origin);
              dest_field.set(dest, copy);
            } else if (dest_field.getType().getComponentType() == Integer.class && orig_field.getType().getComponentType() == int.class) {
              //
              // Assigning an int[] to an Integer[]
              //
              Integer[] copy = (Integer[]) orig_field.get(origin);
              dest_field.set(dest, copy);
            } else if (Schema.class.isAssignableFrom(dest_field.getType().getComponentType()) && (Schema.getImplClass((Class<?extends Schema>)dest_field.getType().getComponentType())).isAssignableFrom(orig_field.getType().getComponentType())) {
              //
              // Assigning an array of impl fields to an array of schema fields, e.g. a DeepLearningParameters[] into a DeepLearningParametersV2[]
              //
              Class dest_component_class = dest_field.getType().getComponentType();

              // NOTE: there can be a race on the source array, so shallow copy it.
              // If it has shrunk the elements might have dangling references.
              Iced[] orig_array = (Iced[]) orig_field.get(origin);
              int length = orig_array.length;
              Iced[] orig_array_copy = Arrays.copyOf(orig_array, length); // Will null pad if it has shrunk since calling length
              Schema[] translation = (Schema[]) Array.newInstance(dest_component_class, length);
              int version = ((Schema)dest).getSchemaVersion();

              // Look up the schema for each element of the array; if not found fall back to the schema for the base class.
              for (int i = 0; i < length; i++) {
                Iced impl = orig_array_copy[i];
                if (null == impl) {
                  translation[i++] = null; // also can happen if the array shrank between .length and the copy
                } else {
                  Schema s = null;
                  try {
                    s = SchemaServer.schema(version, impl);
                  } catch (H2ONotFoundArgumentException e) {
                    s = ((Schema) dest_field.getType().getComponentType().newInstance());
                  }
                  translation[i] = s.fillFromImpl(impl);
                }
              }
              dest_field.set(dest, translation);
            } else if (Schema.class.isAssignableFrom(orig_field.getType().getComponentType()) && Iced.class.isAssignableFrom(dest_field.getType().getComponentType())) {
              //
              // Assigning an array of schema fields to an array of impl fields, e.g. a DeepLearningParametersV2[] into a DeepLearningParameters[]
              //
              // We can't check against the actual impl class I, because we can't instantiate the schema base classes to get the impl class from an instance:
              // dest_field.getType().getComponentType().isAssignableFrom(((Schema)f.getType().getComponentType().newInstance()).getImplClass())) {
              Class dest_component_class = dest_field.getType().getComponentType();
              Schema[] orig_array = (Schema[]) orig_field.get(origin);
              int length = orig_array.length;
              Schema[] orig_array_copy = Arrays.copyOf(orig_array, length);
              Iced[] translation = (Iced[]) Array.newInstance(dest_component_class, length);
              for (int i = 0; i < length; i++) {
                Schema s = orig_array_copy[i];
                translation[i] = s == null ? null : s.createImpl();
              }
              dest_field.set(dest, translation);
            } else {
              throw H2O.fail("Don't know how to cast an array of: " + orig_field.getType().getComponentType() + " to an array of: " + dest_field.getType().getComponentType());
            }
            // end of array handling
          } else if (dest_field.getType() == Key.class && Keyed.class.isAssignableFrom(orig_field.getType())) {
            //
            // Assigning a Keyed (e.g., a Frame or Model) to a Key.
            //
            dest_field.set(dest, ((Keyed) orig_field.get(origin))._key);
          } else if (orig_field.getType() == Key.class && Keyed.class.isAssignableFrom(dest_field.getType())) {
            //
            // Assigning a Key (for e.g., a Frame or Model) to a Keyed (e.g., a Frame or Model).
            //
            Value v = DKV.get((Key) orig_field.get(origin));
            dest_field.set(dest, (null == v ? null : v.get()));
          } else if (KeyV3.class.isAssignableFrom(dest_field.getType()) && Keyed.class.isAssignableFrom(orig_field.getType())) {
            //
            // Assigning a Keyed (e.g., a Frame or Model) to a KeyV1.
            //
            dest_field.set(dest, KeyV3.make(((Class<? extends KeyV3>) dest_field.getType()), ((Keyed) orig_field.get(origin))._key));
          } else if (KeyV3.class.isAssignableFrom(orig_field.getType()) && Keyed.class.isAssignableFrom(dest_field.getType())) {
            //
            // Assigning a KeyV1 (for e.g., a Frame or Model) to a Keyed (e.g., a Frame or Model).
            //
            KeyV3 k = (KeyV3)orig_field.get(origin);
            Value v = DKV.get(Key.make(k.name));
            dest_field.set(dest, (null == v ? null : v.get()));
          } else if (KeyV3.class.isAssignableFrom(dest_field.getType()) && Key.class.isAssignableFrom(orig_field.getType())) {
            //
            // Assigning a Key to a KeyV1.
            //
            dest_field.set(dest, KeyV3.make(((Class<? extends KeyV3>) dest_field.getType()), (Key) orig_field.get(origin)));
          } else if (KeyV3.class.isAssignableFrom(orig_field.getType()) && Key.class.isAssignableFrom(dest_field.getType())) {
            //
            // Assigning a KeyV1 to a Key.
            //
            KeyV3 k = (KeyV3)orig_field.get(origin);
            dest_field.set(dest, (null == k.name ? null : Key.make(k.name)));
          } else if (dest_field.getType() == Pattern.class && String.class.isAssignableFrom(orig_field.getType())) {
            //
            // Assigning a String to a Pattern.
            //
            dest_field.set(dest, Pattern.compile((String) orig_field.get(origin)));
          } else if (orig_field.getType() == Pattern.class && String.class.isAssignableFrom(dest_field.getType())) {
            //
            // We are assigning a Pattern to a String.
            //
            dest_field.set(dest, orig_field.get(origin).toString());
          } else if (dest_field.getType() == FrameV3.ColSpecifierV3.class && String.class.isAssignableFrom(orig_field.getType())) {
            //
            // Assigning a String to a ColSpecifier.  Note that we currently support only the colname, not a frame name too.
            //
            dest_field.set(dest, new FrameV3.ColSpecifierV3((String) orig_field.get(origin)));
          } else if (orig_field.getType() == FrameV3.ColSpecifierV3.class && String.class.isAssignableFrom(dest_field.getType())) {
            //
            // We are assigning a ColSpecifierV2 to a String.  The column_name gets copied.
            //
            dest_field.set(dest, ((FrameV3.ColSpecifierV3)orig_field.get(origin)).column_name);
          } else if (Enum.class.isAssignableFrom(dest_field.getType()) && String.class.isAssignableFrom(orig_field.getType())) {
            //
            // Assigning a String into an enum field.
            //
            Class<Enum> dest_class = (Class<Enum>)dest_field.getType();
            dest_field.set(dest, Enum.valueOf(dest_class, (String) orig_field.get(origin)));
          } else if (Enum.class.isAssignableFrom(orig_field.getType()) && String.class.isAssignableFrom(dest_field.getType())) {
            //
            // Assigning an enum field into a String.
            //
            Object o = orig_field.get(origin);
            dest_field.set(dest, (o == null ? null : o.toString()));
          } else if (Schema.class.isAssignableFrom(dest_field.getType()) && Schema.getImplClass((Class<? extends Schema>) dest_field.getType()).isAssignableFrom(orig_field.getType())) {
            //
            // Assigning an impl field into a schema field, e.g. a DeepLearningParameters into a DeepLearningParametersV2.
            //
            dest_field.set(dest, SchemaServer.schema(/* ((Schema)dest).getSchemaVersion() TODO: remove HACK!! */ 3,
                (Class<? extends Iced>)orig_field.get(origin).getClass()).fillFromImpl((Iced) orig_field.get(origin)));
          } else if (Schema.class.isAssignableFrom(orig_field.getType()) && Schema.getImplClass((Class<? extends Schema>)orig_field.getType()).isAssignableFrom(dest_field.getType())) {
            //
            // Assigning a schema field into an impl field, e.g. a DeepLearningParametersV2 into a DeepLearningParameters.
            //
            Schema s = ((Schema)orig_field.get(origin));
            dest_field.set(dest, s.fillImpl(s.createImpl()));
          } else if ((Schema.class.isAssignableFrom(dest_field.getType()) && Key.class.isAssignableFrom(orig_field.getType()))) {
            //
            // Assigning an impl field fetched via a Key into a schema field, e.g. a DeepLearningParameters into a DeepLearningParametersV2.
            // Note that unlike the cases above we don't know the type of the impl class until we fetch in the body of the if.
            //
            Key origin_key = (Key) orig_field.get(origin);
            Value v = DKV.get(origin_key);
            if (null == v || null == v.get()) {
              dest_field.set(dest, null);
            } else {
              if (((Schema)dest_field.get(dest)).getImplClass().isAssignableFrom(v.get().getClass())) {
                Schema s = ((Schema)dest_field.get(dest));
                dest_field.set(dest, SchemaServer.schema(s.getSchemaVersion(), s.getImplClass()).fillFromImpl(v.get()));
              } else {
                Log.err("Can't fill Schema of type: " + dest_field.getType() + " with value of type: " + v.getClass() + " fetched from Key: " + origin_key);
                dest_field.set(dest, null);
              }
            }
          } else if (Schema.class.isAssignableFrom(orig_field.getType()) && Keyed.class.isAssignableFrom(dest_field.getType())) {
            //
            // Assigning a schema field into a Key field, e.g. a DeepLearningV2 into a (DeepLearningParameters) key.
            //
            Schema s = ((Schema)orig_field.get(origin));
            dest_field.set(dest, ((Keyed)s.fillImpl(s.createImpl()))._key);
          } else {
            //
            // Normal case: not doing any type conversion.
            //
            dest_field.set(dest, orig_field.get(origin));
          }
        }
      }
      catch (IllegalAccessException e) {
        Log.err("Illegal access exception trying to copy field: " + origin_name + " of class: " + origin.getClass() + " to field: " + dest_name + " of class: " + dest.getClass());
      }
      catch (InstantiationException e) {
        Log.err("Instantiation exception trying to copy field: " + origin_name + " of class: " + origin.getClass() + " to field: " + dest_name + " of class: " + dest.getClass());
      }
      catch (Exception e) {
        Log.err(e.getClass().getCanonicalName() + " Exception: " + origin_name + " of class: " + origin.getClass() + " to field: " + dest_name + " of class: " + dest.getClass());
        throw e;
      }
    }
  }

  /**
   * Null out fields in this schema and its children as specified by parameters __exclude_fields and __include_fields.
   * <b>NOTE: modifies the scheme tree in place.</b>
   */
  public static void filterFields(Object o, String includes, String excludes) {
    if (null == excludes || "".equals(excludes))
      return;

    if (null != includes) // not yet implemented
      throw new H2OIllegalArgumentException("_include_fields", "filterFields", includes);

    String[] exclude_paths = excludes.split(",");
    for (String path : exclude_paths) {
      // for each path. . .

      int slash = path.indexOf("/");
      if (-1 == slash || slash == path.length()) { // NOTE: handles trailing slash
        // we've hit the end: null the field, if it exists
        Field f = ReflectionUtils.findNamedField(o, path);
        if (null == f) throw new H2OIllegalArgumentException("_exclude_fields", "filterFields", path);

        try {
          f.set(o, null);
        }
        catch (IllegalAccessException e) {
          throw new H2OIllegalArgumentException("_exclude_fields", "filterFields", path);
        }
      } // hit the end of the path
      else {
        String first = path.substring(0, slash);
        String rest = path.substring(slash + 1);

        Field f = ReflectionUtils.findNamedField(o, first);
        if (null == f) throw new H2OIllegalArgumentException("_exclude_fields", "filterFields", path);

        if (f.getType().isArray() && Object.class.isAssignableFrom(f.getType().getComponentType())) {
          // recurse into the children with the "rest" of the path
          try {
            Object[] field_value = (Object[]) f.get(o);
            for (Object child : field_value) {
              filterFields(child, null, rest);
            }
          }
          catch (IllegalAccessException e) {
            throw new H2OIllegalArgumentException("_exclude_fields", "filterFields", path);
          }
        } else if (Object.class.isAssignableFrom(f.getType())) {
          // recurse into the child with the "rest" of the path
          try {
            Object field_value = f.get(o);
            filterFields(field_value, null, rest);
          }
          catch (IllegalAccessException e) {
            throw new H2OIllegalArgumentException("_exclude_fields", "filterFields", path);
          }
        } else {
          throw new H2OIllegalArgumentException("_exclude_fields", "filterFields", path);
        }
      } // need to recurse
    } // foreach exclude_paths
  }

  public static boolean equals(Object a, Field fa, Object b, Field fb) {
    try {
      Object va = fa.get(a);
      Object vb = fb.get(b);
      return va == null ? vb == null : va.equals(vb);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
      return false;
    }
  }

  public static void setField(Object o, String fieldName, Object value, FieldNaming objectNamingConvention) {
    String destFieldName = null;
    switch (objectNamingConvention) {
      case CONSISTENT: destFieldName = fieldName; break;
      case DEST_HAS_UNDERSCORES:
        if (fieldName.startsWith("_"))
          destFieldName = fieldName;
        else
          destFieldName = "_" + fieldName;
        break;
      case ORIGIN_HAS_UNDERSCORES:
        if (fieldName.startsWith("_"))
          destFieldName = fieldName.substring(1);
        else
          throw new IllegalArgumentException("Wrong combination of options!");
        break;
    }
    setField(o, destFieldName, value);
  }

  /**
   * Set given field to given value on given object.
   *
   * @param o  object to modify
   * @param fieldName  name of field to set
   * @param value  value to write the the field
   */
  public static void setField(Object o, String fieldName, Object value) {
    try {
      Field f = o.getClass().getField(fieldName);
      f.set(o, value);
    } catch (NoSuchFieldException e) {
      throw new IllegalArgumentException("Field " + fieldName + " not found!", e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("Field=" + fieldName + " cannot be set to value=" + value, e);
    }
  }

  /**
   * Returns field value.
   *
   * @param o  object to read field value from
   * @param name  name of field to read
   * @return  returns field value
   *
   * @throws java.lang.IllegalArgumentException  when o is <code>null</code>, or field is not found,
   * or field cannot be read.
   */
  public static Object getFieldValue(Object o, String name, FieldNaming fieldNaming) {
    if (o == null) throw new IllegalArgumentException("Cannot get the field from null object!");
    String destName = fieldNaming.toDest(name);
    try {
      Field f = o.getClass().getField(destName); // failing with fields declared in superclasses
      return f.get(o);
    } catch (NoSuchFieldException e) {
      throw new IllegalArgumentException("Field not found: '" + name + "/" + destName + "' on object " + o);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("Cannot get value of the field: '" + name + "/" + destName + "' on object " + o);
    }
  }

  /**
   * Helper for Arrays.equals().
   */
  public static boolean arraysEquals(Object a, Object b) {
    if (a == null || ! a.getClass().isArray())
      throw new H2OIllegalArgumentException("a", "arraysEquals", a);
    if (b == null || ! b.getClass().isArray())
      throw new H2OIllegalArgumentException("b", "arraysEquals", b);
    if (a.getClass().getComponentType() != b.getClass().getComponentType())
      throw new H2OIllegalArgumentException("Can't compare arrays of different types: " + a.getClass().getComponentType() + " and: " + b.getClass().getComponentType());

    if (a.getClass().getComponentType() == boolean.class) return Arrays.equals((boolean[])a, (boolean[])b);
    if (a.getClass().getComponentType() == Boolean.class) return Arrays.equals((Boolean[])a, (Boolean[])b);

    if (a.getClass().getComponentType() == char.class) return Arrays.equals((char[])a, (char[])b);
    if (a.getClass().getComponentType() == short.class) return Arrays.equals((short[])a, (short[])b);
    if (a.getClass().getComponentType() == Short.class) return Arrays.equals((Short[])a, (Short[])b);
    if (a.getClass().getComponentType() == int.class)     return Arrays.equals((int[])a, (int[])b);
    if (a.getClass().getComponentType() == Integer.class) return Arrays.equals((Integer[])a, (Integer[])b);
    if (a.getClass().getComponentType() == float.class)   return Arrays.equals((float[])a, (float[])b);
    if (a.getClass().getComponentType() == Float.class)   return Arrays.equals((Float[])a, (Float[])b);
    if (a.getClass().getComponentType() == double.class)  return Arrays.equals((double[])a, (double[])b);
    if (a.getClass().getComponentType() == Double.class)  return Arrays.equals((Double[])a, (Double[])b);
    return Arrays.deepEquals((Object[])a, (Object[])b);
  }

  /**
   * Same as Objects.equals(a, b) -- copied here because Objects class does not exist in Java6 (if we ever drop
   * support for Java6, this method can be removed).
   */
  public static boolean equals(Object a, Object b) {
    return (a == b) || (a != null && b != null && b.equals(a));
  }

}
