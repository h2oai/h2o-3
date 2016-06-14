package water.util;

import water.H2O;
import water.Iced;

import java.lang.reflect.*;

public class ReflectionUtils {
  /**
   * Reflection helper which returns the actual class for a type parameter, even if itself is parameterized.
   */
  public static <T> Class<T> findActualClassParameter(Class clz, int parm) {
    Class parm_class = null;

    if (clz.getGenericSuperclass() instanceof ParameterizedType) {
      Type[] handler_type_parms = ((ParameterizedType) (clz.getGenericSuperclass())).getActualTypeArguments();
      if (handler_type_parms[parm] instanceof Class) {
        // The handler's Iced class is not parameterized (the normal case):
        parm_class = (Class) handler_type_parms[parm];  // E.g., for a Schema [0] is the impl (Iced) type; [1] is the Schema type
      } else if (handler_type_parms[parm] instanceof TypeVariable) {
        // The handler's Iced class is parameterized, e.g. to handle multiple layers of Schema classes as in ModelsHandler:
        TypeVariable v = (TypeVariable) (handler_type_parms[parm]);
        Type t = v.getBounds()[0];  // [0] or [parm] ?
        if (t instanceof Class)
          parm_class = (Class) t;
        else if (t instanceof ParameterizedType)
          parm_class = (Class) ((ParameterizedType) t).getRawType();
      } else if (handler_type_parms[parm] instanceof ParameterizedType) {
        // The handler's Iced class is parameterized, e.g. to handle multiple layers of Schema classes as in ModelsHandler:
        parm_class = (Class) ((ParameterizedType) (handler_type_parms[parm])).getRawType(); // For a Key<Frame> this returns Key.class; see also getActualTypeArguments()
      } else {
        String msg = "Iced parameter for handler: " + clz + " uses a type parameterization scheme that we don't yet handle: " + handler_type_parms[parm];
        Log.warn(msg);
        throw H2O.fail(msg);
      }
    } else {
      // Superclass is not a ParameterizedType, so we just have Iced.
      parm_class = Iced.class; // If the handler isn't parameterized on the Iced class then this has to be Iced.
    }
    return (Class<T>) parm_class;
  }

  /**
   * Reflection helper which returns the actual class for a method's parameter.
   */
  public static Class findMethodParameterClass(Method method, int parm) {
    Class[] clzes = method.getParameterTypes();

    if (clzes.length <= parm)
      throw H2O.fail("Asked for the class of parameter number: " + parm + " of method: " + method + ", which only has: " + clzes.length + " parameters.");

    return clzes[parm];
  }

  /**
   * Reflection helper which returns the actual class for a method's parameter.
   */
  public static Class findMethodOutputClass(Method method) { return method.getReturnType(); }

  /**
   * Reflection helper which returns the actual class for a field which has a parameterized type.
   * E.g., DeepLearningV2's "parameters" class is in parent ModelBuilderSchema, and is parameterized
   * by type parameter P.
   */
  public static Class findActualFieldClass(Class clz, Field f) {
    // schema.getClass().getGenericSuperclass() instanceof ParameterizedType
    Type generic_type = f.getGenericType();
    if (! (generic_type instanceof TypeVariable))
      return f.getType();

    // field is a parameterized type
    // ((TypeVariable)schema.getClass().getField("parameters").getGenericType())
    TypeVariable[] tvs = clz.getSuperclass().getTypeParameters();
    TypeVariable tv = (TypeVariable)generic_type;
    String type_param_name = tv.getName();

    int which_tv = -1;
    for(int i = 0; i < tvs.length; i++)
      if (type_param_name.equals(tvs[i].getName()))
        which_tv = i;

    if (-1 == which_tv) {
      // We topped out in the type heirarchy, so just use the type from f.
      // E.g., this happens when getting the metadata for the parameters field of ModelSchema.
      // It has no generic parent, so we need to use the base class.
        return f.getType();
    }

    ParameterizedType generic_super = (ParameterizedType)clz.getGenericSuperclass();

    if (generic_super.getActualTypeArguments()[which_tv] instanceof Class)
      return (Class)generic_super.getActualTypeArguments()[which_tv];
    return findActualFieldClass(clz.getSuperclass(), f);
  }

  // Best effort conversion from an Object to a double
  public static double asDouble( Object o ) {
    if( o == null ) return Double.NaN;
    if( o instanceof Integer ) return ((Integer)o);
    if( o instanceof Long ) return ((Long)o);
    if( o instanceof Float ) return ((Float)o);
    if( o instanceof Double ) return ((Double)o);
    if( o instanceof Enum ) return ((Enum)o).ordinal();
    System.out.println("Do not know how to convert a "+o.getClass()+" to a double");
    throw H2O.fail();
  }

  /**
   *  Return the Field for the specified name.
   *  <p>
   *  Java reflection will either give you all the public fields all the way up the class hierarchy (getField()),
   *  or will give you all the private/protected/public only in the single class (getDeclaredField()).
   *  This method uses the latter but walks up the class hierarchy.
   */
  public static Field findNamedField(Object o, String field_name) {
    Class clz = o.getClass();
    Field f = null;
    do {
      try {
        f = clz.getDeclaredField(field_name);
        f.setAccessible(true);
        return f;
      }
      catch (NoSuchFieldException e) {
        // fall through and try our parent
      }

      clz = clz.getSuperclass();
    } while (clz != Object.class);
    return null;
  }
}
