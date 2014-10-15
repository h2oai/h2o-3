package water.util;

import com.google.common.collect.Lists;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.lang.reflect.Field;
import java.util.List;

public class ReflectionUtils {
  /**
   * Reflection helper which returns all the Fields of a class of any accessibility
   * (public, protected, private); the cross between Class.getFields() and
   * Class.getDeclaredFields().
   *
   * Copied from http://stackoverflow.com/questions/16966629/what-is-the-difference-between-getfields-getdeclaredfields-in-java-reflection
   */
  public static Iterable<Field> getFieldsUpTo(@NotNull Class<?> startClass,
                                              @Nullable Class<?> exclusiveParent) {

    List<Field> currentClassFields = Lists.newArrayList(startClass.getDeclaredFields());
    Class<?> parentClass = startClass.getSuperclass();

    if (parentClass != null &&
        (exclusiveParent == null || !(parentClass.equals(exclusiveParent)))) {
      List<Field> parentClassFields =
        (List<Field>) getFieldsUpTo(parentClass, exclusiveParent);
      currentClassFields.addAll(parentClassFields);
    }

    return currentClassFields;
  }
}
