package water.codegen.java;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import hex.genmodel.GenModel;
import hex.genmodel.annotations.CG;
import water.codegen.JCodeSB;
import water.codegen.SB;
import water.util.ReflectionUtils;

/**
 * Created by michal on 12/11/15.
 */
public class JCodeGenUtil {

  /** Transform given string to legal java Identifier (see Java grammar http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8) */
  public static String toJavaId(String s) {
    // Note that the leading 4 backslashes turn into 2 backslashes in the
    // string - which turn into a single backslash in the REGEXP.
    // "+-*/ !@#$%^&()={}[]|\\;:'\"<>,.?/"
    return s.replaceAll("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]",  "_");
  }

  public static ClassCodeGenerator klazz(String name) {
    return new ClassCodeGenerator(name);
  }

  public static ClassCodeGenerator klazz(String name, Class extendsFrom, Object source) {
    ClassCodeGenerator ccg = new ClassCodeGenerator(name)
        .withExtend(extendsFrom);

    // Attach all methods which can be simply delegated to model
    for (Method m : ReflectionUtils.findAllAbstractMethods(GenModel.class)) {
      CG.Delegate cg = m.getAnnotation(CG.Delegate.class);
      MethodCodeGenerator mcg = method(m);
      if (cg != null) { // Attach body
        Class returnType = m.getReturnType();
        // Read value from object
        Object value = ReflectionUtils.getValue(source, cg.target(), returnType);
        // Generate body
        mcg.withBody(s("return ").pj(value, returnType).p(";").nl());
      } // else leave body for corresponding model code generator
      ccg.withMethod(mcg);
    }
    return ccg;
  }

  public static MethodCodeGenerator ctor(String klazzName) {
    return method(klazzName).withReturnType(null);
  }

  public static MethodCodeGenerator method(String methodName) {
    return new MethodCodeGenerator(methodName);
  }

  private static MethodCodeGenerator method(Method m) {
    MethodCodeGenerator mcg = new MethodCodeGenerator(m.getName());
    mcg
        .withModifiers(m.getModifiers() ^ Modifier.ABSTRACT)
        .withOverride(true)
        .withReturnType(m.getReturnType());
    int cnt = 0;
    Annotation[][] paramAnnos = m.getParameterAnnotations();
    Class[] paramTypes = m.getParameterTypes();
    for (int i = 0; i < paramTypes.length; i++) {
      Class paramType = paramTypes[i];
      CG.Delegate cgAnno = find(paramAnnos[i], CG.Delegate.class);
      if (cgAnno != null) {
        mcg.withParams(paramType, cgAnno.target());
      } else {
        throw new RuntimeException("Method '" + m + "' does not annotate fields of abstract method! Please use @CG annotation!");
      }
      cnt++;
    }
    return mcg;
  }

  public static FieldCodeGenerator field(Class fieldType, String fieldName) {
    return new FieldCodeGenerator(fieldName).withType(fieldType);
  }

  public static FieldCodeGenerator field(String fieldName) {
    return new FieldCodeGenerator(fieldName);
  }

  public static ValueCodeGenerator VALUE(Object o, Class<?> clz) {
    if (clz == int.class) return VALUE((int) o);
    else if (clz == long.class) return VALUE((long) o);
    else if (clz == float.class) return VALUE((float) o);
    else if (clz == double.class) return VALUE((double) o);
    else if (clz == double[].class) return VALUE((double[]) o);
    else if (clz == int[].class) return VALUE((int[]) o);
    else if (clz.isArray()) return VALUE((Object[]) o);
    else return valueFallback(o, clz);
  }

  public static ValueCodeGenerator VALUE(final long v) {
    return new ValueCodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.pj(v);
      }
    };
  }

  public static ValueCodeGenerator VALUE(final int v) {
    return new ValueCodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.pj(v);
      }
    };
  }

  public static ValueCodeGenerator VALUE(final float v) {
    return new ValueCodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.pj(v);
      }
    };
  }

  public static ValueCodeGenerator VALUE(final double v) {
    return new ValueCodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.pj(v);
      }
    };
  }

  public static <T> ValueCodeGenerator VALUE(T[] ary) {
    return new LargeArrayGenerator(new JavaArrayWrapper(ary), 0, ary != null ? ary.length : 0);
  }

  public static ValueCodeGenerator VALUE(double[] ary) {
    // FIXME: zde se potrebuju rozhodnout podle aktualni context
    // Ale nyni se muzem rozhodnout primo
    return new LargeArrayGenerator(new JavaArrayWrapper(ary), 0, ary != null ? ary.length : 0);
  }

  public static ValueCodeGenerator VALUE(int[] ary) {
    // FIXME: zde se potrebuju rozhodnout podle aktualni context
    // Ale nyni se muzem rozhodnout primo
    return new LargeArrayGenerator(new JavaArrayWrapper(ary), 0, ary != null ? ary.length : 0);
  }

  public static ValueCodeGenerator VALUE(ArrayWrapper aaw) {
    return new LargeArrayGenerator(aaw, 0, aaw.getLen());
  }

  private static ValueCodeGenerator valueFallback(final Object o, final Class<?> klazz) {
    return new ValueCodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.pj(o, klazz);
      }
    };
  }


  public static SB s(String s) {
    return new SB(s);
  }

  public static SB s(Class c) {
    return new SB().pj(c);
  }

  public static SB s(int n) {
    return new SB().pj(n);
  }

  public static SB s() {
    return new SB();
  }

  private static <T, X> X find(T[] ary, Class<X> klazz) {
    for (int i = 0; i < ary.length; i++) {
      if (klazz.isAssignableFrom(ary[i].getClass()))
        return (X) ary[i];
    }
    return null;
  }

  public static String asString(Class type) {
    Package pack = type.getPackage();
    if (pack != null && pack.getName().startsWith("java.lang"))
      return type.getSimpleName();
    else
      return type.getCanonicalName();
  }

  public abstract static class ArrayWrapper {
    final Class type;
    final Class aryType;
    final int dim;
    final boolean nested;
    final boolean isNull;

    protected ArrayWrapper(Class aryType) {
      this.aryType = aryType != null ? aryType : null;
      this.type = aryType != null ? ReflectionUtils.getBasedComponentType(aryType) : null;
      this.dim = aryType != null ? getArrayDim(aryType) : -1;
      this.nested = aryType != null ? aryType.getComponentType().isArray() : false;
      this.isNull = aryType == null;
    }

    public Class getType() { return type; }
    public Class getArrayType() { return aryType; }
    public int getDim() { return dim; }
    public Object get() { return this; }
    public abstract Object get(int idx);
    public boolean isNested() { return nested; }
    public abstract int getLen();
    public boolean isNull() { return isNull; };
  }

  public static class JavaArrayWrapper extends ArrayWrapper {
    final Object value;
    final int len;

    public JavaArrayWrapper(Object value, Class aryType) {
      super(value != null ? aryType : null);
      this.value = value;
      this.len = value != null ? Array.getLength(value) : 0;
    }

    public JavaArrayWrapper(Object value) {
      this(value, value != null ? value.getClass() : null);
    }

    @Override
    public Object get() {
      return this.value;
    }

    @Override
    public Object get(int idx) {
      return isNested() ? new JavaArrayWrapper(Array.get(value, idx), value.getClass().getComponentType()) : Array.get(value, idx);
    }

    @Override
    public int getLen() {
      return len;
    }
  }

  static int getArrayDim(Class klazz) {
    assert klazz.isArray() : "Value has to be array!";
    int dim = 0; // Dimension
    while (klazz.isArray()) {
      klazz = klazz.getComponentType();
      dim++;
    }
    // in theory we should compare type =:= klazz or at least klazz <: type
    return dim;
  }

}
