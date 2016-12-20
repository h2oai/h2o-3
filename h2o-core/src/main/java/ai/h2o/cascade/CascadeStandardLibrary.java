package ai.h2o.cascade;

import ai.h2o.cascade.core.Function;
import ai.h2o.cascade.core.IdList;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValFun;
import ai.h2o.cascade.vals.ValNull;
import ai.h2o.cascade.vals.ValNum;
import javassist.*;
import water.H2O;
import water.fvec.Frame;
import water.util.SB;
import water.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 *
 */
public class CascadeStandardLibrary implements ICascadeLibrary {
  private static CascadeStandardLibrary instance;
  private Map<String, Val> members;


  public static CascadeStandardLibrary instance() {
    if (instance == null) instance = new CascadeStandardLibrary();
    return instance;
  }

  public Map<String, Val> members() {
    return members;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  /** Full path of the directory where {@link Val} class is located (includes a trailing dot). */
  private static final String V = Val.class.getName().substring(0, Val.class.getName().length() - 3);

  /** Mapping from Java types to {@link Val.Type}s. */
  private static final Map<String, String> TYPE_MAP = new HashMap<>(12);
  static {
    TYPE_MAP.put("boolean", "BOOL");
    TYPE_MAP.put("int", "INT");
    TYPE_MAP.put("long", "INT");
    TYPE_MAP.put("double", "NUM");
    TYPE_MAP.put("java.lang.String", "STR");
    TYPE_MAP.put("double[]", "NUMS");
    TYPE_MAP.put("java.lang.String[]", "STRS");
    TYPE_MAP.put(Frame.class.getName(), "FRAME");
    TYPE_MAP.put(IdList.class.getName(), "IDS");
  }




  /**
   * Constructor is private to prevent construction more than 1 instance of the class.
   */
  private CascadeStandardLibrary() {
    members = new HashMap<>(100);

    // Constants
    members.put("true", new ValNum(1));
    members.put("True", new ValNum(1));
    members.put("TRUE", new ValNum(1));
    members.put("false", new ValNum(0));
    members.put("False", new ValNum(0));
    members.put("FALSE", new ValNum(0));
    members.put("NaN", new ValNum(Double.NaN));
    members.put("nan", new ValNum(Double.NaN));
    members.put("NA", new ValNum(Double.NaN));
    members.put("null", new ValNull());

    String[] frameCmds = {"ncols", "nrows"};
    for (String cmd : frameCmds)
      registerCommand("frame", cmd);
  }


  private void registerCommand(String pkg, String name) {
    String className = "ai.h2o.cascade.stdlib." + pkg + ".Fn" + StringUtils.capitalize(name);
    ClassPool cp = ClassPool.getDefault();
    try {
      CtClass cc = cp.get(className);
      augmentClass(cc);
      Class c = cc.toClass();
      Function f = (Function) c.newInstance();
      members.put(name, new ValFun(f));
    } catch (NotFoundException | CannotCompileException e) {
      throw H2O.fail("Woven class " + className + " cannot be compiled:\n" + e.getMessage());
    } catch (InstantiationException | IllegalAccessException e) {
      throw H2O.fail("Could not instantiate class " + className + ":\n" + e.getMessage());
    }
  }


  private void augmentClass(CtClass cc) {
    try {
      CtMethod[] applyMethods = getApplies(cc);
      String apply0Body = makeApply0(applyMethods);
      System.out.println("\nBody of apply0() method for " + cc.getName() + ":");
      System.out.println(apply0Body);
      System.out.println("\n");
      cc.addMethod(CtMethod.make(apply0Body, cc));
    } catch (RuntimeException e) {
      throw new RuntimeException("[In class " + cc.getName() + "]: " + e.getMessage());
    } catch (NotFoundException | CannotCompileException e) {
      throw H2O.fail(e.getMessage());
    }
  }


  /**
   * Find all {@code apply(...)} methods in the class {@code cc}, and return
   * their {@link CtMethod} records.
   */
  private CtMethod[] getApplies(CtClass cc) throws NotFoundException {
    ArrayList<CtMethod> res = new ArrayList<>(2);
    for (CtMethod method : cc.getDeclaredMethods()) {
      if (method.getName().equals("apply"))
        res.add(method);
    }
    if (res.isEmpty())
      throw new RuntimeException("Class " + cc.getName() + " does not define any apply() methods.");
    return res.toArray(new CtMethod[res.size()]);
  }


  private String makeApply0(CtMethod[] applyMethods) throws NotFoundException {
    assert applyMethods.length > 0;

    // Determine the smallest and the largest number of arguments in each {@code apply()} method.
    // If any method has varargs, then {@code maxArgs} will be -1.
    int minArgs = Integer.MAX_VALUE, maxArgs = 0;
    for (CtMethod method : applyMethods) {
      int nParams = method.getParameterTypes().length;
      minArgs = Math.min(minArgs, nParams);
      maxArgs = Math.max(maxArgs, nParams);
      if ((method.getModifiers() & Modifier.VARARGS) != 0)
        maxArgs = -1;
    }

    SB sb = new SB();
    sb.p("public ").p(V).p("Val apply0(").p(V).p("Val[] args) {\n");
    sb.p("  final int n = args.length;\n");
    sb.p("  argumentsCountCheck(n, ").p(minArgs).p(", ").p(maxArgs).p(");\n");
    sb.p("\n");
    for (CtMethod method : applyMethods) {
      CtClass[] params = method.getParameterTypes();
      CtClass retType = method.getReturnType();
      boolean isVararg = (method.getModifiers() & Modifier.VARARGS) != 0;
      if (minArgs == maxArgs) {
        writeArgumentTypeChecks(params, isVararg, retType, "  ", sb);
      } else {
        sb.p("  if (n ").p(isVararg? ">=" : "==").p(" ").p(params.length).p(") {\n");
        writeArgumentTypeChecks(params, isVararg, retType, "    ", sb);
        sb.p("  }\n");
      }
    }
    sb.p("  throw new java.lang.IllegalArgumentException(\"No matching signature\");\n");
    sb.p("}");
    return sb.toString();
  }


  private void writeArgumentTypeChecks(CtClass[] params, boolean isVararg, CtClass retType, String indent, SB sb) {
    SB argsList = new SB();
    int nRegularParams = params.length - (isVararg? 1 : 0);
    for (int i = 0; i < nRegularParams; i++) {
      String valType = getValType(params[i].getName());
      sb.p(indent).p("checkArg(args, ").p(i).p(", ").p(V).p("Val.Type.").p(valType).p(");\n");
      if (i > 0) argsList.p(", ");
      argsList.p("args[").p(i).p("].get").p(StringUtils.capitalize(valType)).p("()");
    }
    if (isVararg) {
      int k = params.length - 1;
      String javaType = params[k].getName();
      assert javaType.endsWith("[]");
      javaType = javaType.substring(0, javaType.length() - 2);
      String valType = getValType(javaType);
      String valTypeC = StringUtils.capitalize(valType);
      sb.p(indent).p(javaType).p("[] vararg = new ").p(javaType).p("[n - ").p(k).p("];\n");
      sb.p(indent).p("for (int i = ").p(k).p("; i < n; ++i) {\n");
      sb.p(indent).p("  checkArg(args, i, ").p(V).p("Val.Type.").p(valType).p(");\n");
      sb.p(indent).p("  vararg[i - ").p(k).p("] = args[i].get").p(valTypeC).p("();\n");
      sb.p(indent).p("}\n");
      if (k > 0) argsList.p(", ");
      argsList.p("vararg");
    }
    sb.p(indent).p(retType.getName()).p(" ret = apply(").p(argsList).p(");\n");
    Val.Type retValType = Val.Type.valueOf(getValType(retType.getName()));
    sb.p(indent).p("return new ").p(V).p(retValType.getValClassName()).p("(ret);\n");
  }


  /** Helper method to translate Java types into Val types using the {@link #TYPE_MAP}. */
  private String getValType(String javaType) {
    String valType = TYPE_MAP.get(javaType);
    if (valType == null)
      throw new RuntimeException("Parameter of type " + javaType + " is not supported in .apply() method.");
    return valType;
  }
}
