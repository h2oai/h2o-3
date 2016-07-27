package water.codegen.java;

import hex.tree.CompressedTree;
import hex.tree.drf.DRFModel;
import water.codegen.JCodeSB;
import water.codegen.java.mixins.DRFMixin;
import water.codegen.java.mixins.ForestMixin;
import water.codegen.java.mixins.SharedTreeModelMixin;

import static water.codegen.java.JCodeGenUtil.s;

/**
 * FIXME:
 */
public class DRFModelPOJOCodeGen extends POJOModelCodeGenerator<DRFModelPOJOCodeGen, DRFModel> {

  public static DRFModelPOJOCodeGen codegen(DRFModel model) {
    return new DRFModelPOJOCodeGen(model);
  }

  protected DRFModelPOJOCodeGen(DRFModel model) {
    super(model);
  }

  @Override
  protected DRFModelPOJOCodeGen buildImpl(final CompilationUnitGenerator cucg, final ClassCodeGenerator ccg) {
    ccg.withMixin(model, SharedTreeModelMixin.class);
    ccg.withMixin(model, DRFMixin.class);

    // Implements scoreImpl method
    ccg.method("scoreImpl").withBody(new CodeGeneratorB() {
      @Override
      public CodeGeneratorB build() {
        for (int t = 0; t < model._output._treeKeys.length; t++) {
          // The tree forest tree generator
          ClassCodeGenerator fccg = new ClassCodeGenerator(forestName(t))
              .withMixin(ForestMixin.class);
          final int treeIdx = t;
          final int nclass = model._output.nclasses();
          // Calls Trees
          fccg.method("score0").withBody(new CodeGeneratorB() {
            @Override
            public CodeGeneratorB build() {
              // Generate tree representing class
              ClassGenContainer topLevelClassContainer = ccg.classContainer(this);
              for (int c = 0; c < nclass; c++)
                if( !model.binomialOpt() || !(c==1 && nclass==2) ) {
                  String treeJavaClassName = treeName(treeIdx, c);
                  CompressedTree compressedTree = model._output.ctree(treeIdx, c);
                  new TreePOJOCodeGenVisitor(
                      model._output._names,
                      compressedTree,
                      topLevelClassContainer,
                      treeJavaClassName).build();
                }
              return super.build();
            }

            @Override
            public void generate(JCodeSB out) {
              for(int c = 0; c < nclass; c++)
                if( !model.binomialOpt() || !(c==1 && nclass==2) ) {
                  out.p("preds[").p(nclass == 1 ? 0 : c + 1).p("] += ").p(treeName(treeIdx, c)).p(".score0(data);").nl();
                }
            }
          });
          // Get reference for class container and put a new class generator inside
          ccg.classContainer(this).add(fccg);
        }
        return this;
      }

      @Override
      public void generate(JCodeSB out) {
        for (int t = 0; t < model._output._treeKeys.length; t++) {
          out.p(forestName(t)).p(".score0(data, preds);").nl();
        }
        out.p("return preds;").nl();
      }

      private String forestName(int treeIdx) {
        return "Forest_" + treeIdx;
      }

      private String treeName(int treeIdx, int classIdx) {
        return "Tree_" + treeIdx + "_class_" + classIdx;
      }
    }).withParentheses(true);

    return self();
  }

}
