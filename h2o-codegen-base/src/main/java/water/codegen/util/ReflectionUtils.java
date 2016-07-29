package water.codegen.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;

/**
 * Reflection utilities for code generator.
 */
public class ReflectionUtils {

  public static Method[] findAllMethods(Class clz, Class annoClass) {
    List<Method> methods = new LinkedList<>();
    for (Method m : clz.getMethods()) {
      Annotation anno = m.getAnnotation(annoClass);
      if (anno != null) methods.add(m);
    }
    return methods.toArray(new Method[methods.size()]);
  }

  public static Method[] findAllAbstractMethods(Class clz) {
    List<Method> methods = new LinkedList<>();
    for (Method m : clz.getMethods()) {
      if (Modifier.isAbstract(m.getModifiers())) {
        methods.add(m);
      }
    }
    // Note: toArray(new Field[0]) is faster than toArray(new Field[size])
    //       based on http://shipilev.net/blog/2016/arrays-wisdom-ancients/
    return methods.toArray(new Method[0]);
  }

  /**
   * Return all fields declared by a given class.
   *
   * It returns all fields including fields from parent classes
   * and private fields. It makes all field accessible.
   *
   * @param clz  class to query
   * @return  list of fields
   */
  public static Field[] findAllFields(Class clz) {
    return findAllFields(clz, true);
  }
  public static Field[] findAllFields(Class clz, boolean includeParent) {
    Field[] fields = new Field[0];
    do {
      Field[] tmp = clz.getDeclaredFields();
      for (Field f : tmp) f.setAccessible(true);
      fields = ArrayUtils.append(fields, tmp);
      clz = clz.getSuperclass();
    } while (clz != Object.class && includeParent);
    return fields;
  }

  /**
   * Simple query-based way how to get value of given field/returned by a method.
   *
   * @param o  source object to query
   * @param q  query
   * @param klazz  specify type of return type
   * @param <T>  return type
   * @return  return value referenced by query
   *
   * @throws Exception  in case that Java reflection subsystem throws exception
   */
  public static <T> T getValue(Object o, String q, Class<T> klazz) {
    Character startChar = null;
    if (q.startsWith("#")) {
      startChar = '#';
    } else if (q.startsWith(".")) {
      startChar = '.';
    } else {
      throw new RuntimeException("Wrong query: `" + q + "`. "
                                 + " - The query has to start with . or # !!!"
                                 + "\n Queried object: " + o.getClass()
                                 + "\n Return type of queried field: " + klazz);
    }
    int nextHash = q.indexOf('#', 1);
    int nextDot = q.indexOf('.', 1);
    int next = nextHash > 0 && nextDot > 0 ? Math.min(nextHash, nextDot) : Math.max(nextHash, nextDot);
    String head = next < 0 ? q.substring(1) : q.substring(1, next);
    String tail = next < 0 ? null : q.substring(next);
    Class clz = o.getClass();
    // Read value from object
    Object result;
    try {
      if (startChar == '#') {
        Method m = clz.getMethod(head);
        result = m.invoke(o);
      } else {
        Field f = clz.getField(head);
        f.setAccessible(true);
        result = f.get(o);
      }
    } catch (Exception e) {
      throw new RuntimeException("Wrong syntax in reflective query: " + q + ""
                                 + "\n Queried object: " + o.getClass()
                                 + "\n Return type of queried field: " + klazz);
    }

    return next < 0 ? (T) result : getValue(result, tail, klazz);
  }

  public static <T> T getValue(Object o, Field f, Class<T> t) {
    try {
      return (T) f.get(o);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  /** For array types it returns base component type else it return o.getClass().
   *
   * For example:
   *  for String[][][] returns String
   *  for String returns String
   *
   * @param clz any object
   * @return component type
   */
  public static Class<?> getBasedComponentType(Class clz) {
    Class result = clz.getComponentType();
    while (result.isArray()) result = result.getComponentType();
    return result;
  }
}
