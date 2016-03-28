package water.codegen.java;

import java.io.Serializable;
import java.lang.reflect.Array;

import water.codegen.CodeGenerator;
import water.codegen.JCodeSB;
import water.util.ReflectionUtils;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static water.codegen.java.JCodeGenUtil.field;
import static water.codegen.java.JCodeGenUtil.klazz;
import static water.codegen.java.JCodeGenUtil.method;
import static water.codegen.java.JCodeGenUtil.s;


/**
 * Large array generator.
 *
 * FIXME: the use of `klazz`, `method`, `field` should be injected
 * and passed through parent generator.
 */
public class LargeArrayGenerator<T> extends ArrayGenerator<LargeArrayGenerator<T>> {

  final Object value;

  public LargeArrayGenerator(Object value, int off, int len) {
    super(off, len);
    this.value = value;
    this.type = value != null ? ReflectionUtils.getBasedComponentType(value) : null;
  }

  private static int getArrayDim(Object ary) {
    Class<?> klazz = ary.getClass();
    assert klazz.isArray() : "Value has to be array!";
    int dim = 0; // Dimension
    while (klazz.isArray()) {
      klazz = klazz.getComponentType();
      dim++;
    }
    // in theory we should compare type =:= klazz or at least klazz <: type
    return dim;
  }


  @Override
  public void generate(JCodeSB out) {
    generateArrayValue(out, value, off, len, prefix());
  }

  protected JCodeSB generateArrayValue(JCodeSB out, final Object ary, final int aryOff, final int aryLen, final String aryClassPrefix) {
    if (ary == null) {
      // End quickly if null is passed
      return out.p("null");
    }

    // Write value to the output
    out.p(aryClassPrefix).p(".VALUES");

    // Get dimension of ary
    final int dim = getArrayDim(ary);
    final Class<?> aryKlazz = ary.getClass();
    assert aryKlazz.isArray() : "only array should be here";
    Class<?> elemKlazz  = ary.getClass().getComponentType();
    System.out.println("---> " +elemKlazz);
    final boolean isNested = elemKlazz.isArray();

    // But offload generation of the field into multiple generators
    // Generate VALUES holder represented by a class
    // with a static field and static initializer
    ClassCodeGenerator topLevelCCG =
        klazz(aryClassPrefix)
            .withModifiers(modifiers)
            .withImplements(Serializable.class)
            .withField(field("VALUES")
                           .withModifiers(PUBLIC | STATIC | FINAL)
                           .withType(aryKlazz)
                           .withValue(s("new ").pj(type).p("[").p(aryLen).p("]").pbraces(dim-1)));
    // Add to top-level container handling encapsulation of this class (class level, compilation unit)
    classContainer().add(topLevelCCG);

    // Generate static initializer for VALUES field
    topLevelCCG.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.p("static {").ii(1).nl();
        int remain = aryLen;
        int cnt = 0, start = 0;
        while (remain > 0) {
          String subClzName = aryClassPrefix + "_" + cnt++;
          final int alen = Math.min(MAX_STRINGS_IN_CONST_POOL, remain);
          final int astart = start;
          ClassCodeGenerator subCcg =
              klazz(subClzName)
                  .withModifiers(modifiers)
                  .withImplements(Serializable.class)
                  .withMethod(
                      method("fill") // FIXME: at this point we know aprox size of constant pool items, however actual size depends on actual values
                          .withModifiers(PUBLIC | STATIC)
                          .withParams(aryKlazz, "sa")
                          .withBody(isNested ? fillNestedArray(subClzName, ary, alen, astart, aryOff)
                                             : String.class == type
                                               ? fillArrayWithPrimitive(asSA(ary), alen, astart, aryOff)
                                               : double.class == type ? fillArrayWithPrimitive(asDA(ary), alen, astart, aryOff) : null)
                  );
          // Append class generator to the top-level container
          classContainer().add(subCcg);
          out.ip(subClzName).p(".fill(VALUES);").nl();
          start += alen;
          remain -= alen;
        }

        out.di(1).nl().p("}");
      }
    });

    return out;
  }

  static String[] asSA(Object o) {
    return (String[]) o;
  }

  static double[] asDA(Object o) {
    return (double[]) o;
  }

  static int[] asIA(Object o) {
    return (int[]) o;
  }

  private static CodeGenerator fillArrayWithPrimitive(final double[] ary, final int alen, final int astart, final int aryOff) {
    return new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        for(int i = 0; i < alen; i++) {
          out.p("sa[").p(astart + i).p("] = ").pj(ary[astart + aryOff + i]).p(";").nl();
        }
      }
    };
  }

  private CodeGenerator fillArrayWithPrimitive(final String[] ary, final int alen, final int astart, final int aryOff) {
    return new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        for(int i = 0; i < alen; i++) {
          out.p("sa[").p(astart + i).p("] = ").pj(ary[astart + aryOff + i]).p(";").nl();
        }
      }
    };
  }

  private CodeGenerator fillNestedArray(final String clzName, final Object ary, final int alen, final int astart, final int aryOff) {
    return new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        for(int i = 0; i < alen; i++) {
          int idx = aryOff + astart + i;
          out.p("sa[").p(idx).p("] = ");
          Object aryAtIdx = Array.get(ary, idx);
          if (aryAtIdx != null) {
            int len = Array.getLength(aryAtIdx);
            generateArrayValue(out, aryAtIdx, 0, len, clzName + "_" + idx);
          } else {
            out.NULL();
          }
          out.p(";").nl();
        }
      }
    };
  }

  /** FIXME: remove it!!! Maximum number of string generated per class (static initializer) */
  public static int MAX_STRINGS_IN_CONST_POOL = 30;

}
