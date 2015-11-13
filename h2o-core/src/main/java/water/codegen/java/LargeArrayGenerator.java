package water.codegen.java;

import water.codegen.CodeGenerator;
import water.codegen.JCodeSB;
import water.codegen.SB;
import water.codegen.SimpleCodeGenerator;

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

  final T[] value;

  public LargeArrayGenerator(String prefix, T[] value) {
    this(prefix, value, 0, value != null ? value.length : 0);
  }

  public LargeArrayGenerator(String prefix, T[] value, int off, int len) {
    super(off, len);
    this.value = value;
    withPrefix(prefix);
  }

  private int getArrayInfo(Object ary) {
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
    int dim = getArrayInfo(this.value);

    // Write value to the output
    out.p(prefix).p(".VALUES");

    // But offload generation of the field into multiple generators
    // Generate VALUES holder represented by a class
    // with a static field and static initializer
    ClassCodeGenerator topLevelCCG =
        klazz(prefix)
         .withModifiers(modifiers)
         .withImplements("java.io.Serializable")
         .withField(field("VALUES")
                        .withModifiers(PUBLIC | STATIC | FINAL)
                        .withType(s(type).pbraces(dim).toString())
                        .withValue(s("new ").p(type).p("[").p(len).p("]").pbraces(dim-1)));
    // Add to top-level container handling encapsulation of this class (class level, compilation unit)
    classContainer.add(topLevelCCG);

    // Now generate a class which will fill the VALUES field
    // Append ad-hoc generator for static block
    topLevelCCG.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        // FIXME: track size of static block to avoid hitting size limit of function
        out.p("static {").ii(1).nl();
        int remain = len;
        int cnt = 0, start = 0;
        while (remain > 0) {
          String subClzName = prefix + "_" + cnt++;
          final int alen = Math.min(MAX_STRINGS_IN_CONST_POOL, remain);
          final int astart = start;
          ClassCodeGenerator subCcg =
              klazz(subClzName)
                  .withModifiers(modifiers)
                  .withImplements("java.io.Serializable")
                  .withMethod(
                      method("fill") // FIXME: at this point we know aprox size of constant pool items, however actual size depends on actual values
                          .withModifiers(PUBLIC | STATIC)
                          .withParams(type + "[]", "sa")
                          .withBody(new CodeGenerator() {
                            @Override
                            public void generate(JCodeSB out) {
                              for(int i = 0; i < alen; i++) {
                                out.p("sa[").p(astart + i).p("] = ").pobj(value[astart + off + i]).p(";").nl();
                              }
                            }
                          }));
          // Append class generator to the top-level container
          classContainer.add(subCcg);
          out.ip(subClzName).p(".fill(VALUES);").nl();
          start += alen;
          remain -= alen;
        }

        out.di(1).nl().p("}").nl();
      }
    });
  }



  /** FIXME: remove it!!! Maximum number of string generated per class (static initializer) */
  public static int MAX_STRINGS_IN_CONST_POOL = 30;

}
