package ai.h2o.cascade.stdlib;

import ai.h2o.cascade.CascadeScope;
import ai.h2o.cascade.CascadeSession;
import ai.h2o.cascade.ICascadeLibrary;
import ai.h2o.cascade.core.CFrame;
import ai.h2o.cascade.core.Function;
import ai.h2o.cascade.core.IdList;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValFun;
import ai.h2o.cascade.vals.ValNull;
import ai.h2o.cascade.vals.ValNum;
import javassist.*;
import water.util.SB;
import water.util.StringUtils;

import java.util.*;


/**
 * Cascade Standard Library is the main body of functionality for the Cascade
 * language. This library also adds certain predefined constants, such as
 * {@code true, False, NaN, null}, etc.
 * <p>
 * This library is imported into the global scope when a new session is
 * started (see {@link CascadeSession}).
 * <p>
 * This library uses non-standard class loading strategy: each function's class
 * is inspected using Javassist, which tries to find all {@code ... apply(...)}
 * declared methods. Then we auto-generate the {@code Val apply0(Val[])} method
 * which is the one being actually called by the framework. This strategy
 * allows us to significantly reduce the amount of the boilerplate code,
 * compared to Rapids.
 * <p>
 * NOTE: care must be taken not to touch any of the functions within this
 * library before the library is first instantiated.
 */
public class StandardLibrary implements ICascadeLibrary {
  private static StandardLibrary instance;
  private Map<String, Val> members;

  /** Retrieve the singleton instance of this class. */
  public static StandardLibrary instance() {
    if (instance == null) instance = new StandardLibrary();
    return instance;
  }

  /**
   * Obtain a map of the individual members of the class: most of them are
   * functions, but some are also (numeric) constants. Use this map to import
   * the library into the current scope.
   */
  @Override
  public Map<String, Val> members() {
    return members;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Registry of library members:
  //--------------------------------------------------------------------------------------------------------------------

  private StandardLibrary() {
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

    String[] coreCmds = {"fromdkv"};
    for (String cmd : coreCmds)
      registerCommand("core", cmd);
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Private (code weaving)
  //--------------------------------------------------------------------------------------------------------------------
  private static final boolean DEBUG = false;


  /**
   * Register command {@code name} within subpackage {@code pkg} as a member
   * of the library. The class for this command is resolved as
   * {@code stdlib.{pkg}.Fn{Name}}.
   * <p>
   * This method loads the command's class, and then adds the
   * {@code Val apply0(Val[])} method using {@link #augmentClass(CtClass)}.
   */
  private void registerCommand(String pkg, String name) {
    String className = "ai.h2o.cascade.stdlib." + pkg + ".Fn" + StringUtils.capitalize(name);
    ClassPool cp = ClassPool.getDefault();
    cp.importPackage("ai.h2o.cascade.vals");
    try {
      CtClass cc = cp.get(className);
      augmentClass(cc);
      Class c = cc.toClass();
      Function f = (Function) c.newInstance();
      members.put(name, new ValFun(f));
    } catch (NotFoundException | CannotCompileException e) {
      throw new RuntimeException("Woven class " + className + " cannot be compiled:\n" + e.getMessage());
    } catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException("Could not instantiate class " + className + ":\n" + e.getMessage());
    }
  }


  /**
   * Modify class {@code cc} by adding method {@code Val apply0(Val[])} before
   * it will be instantiated. The method being added is an implementation of
   * the method declared in the {@link Function} class. This method will be
   * called from {@link ai.h2o.cascade.asts.AstApply#exec(CascadeScope)} to
   * invoke the functionality of the function-class {@code cc}.
   *
   * <p>The job of the {@code Val apply0(Val[])} method is thus to convert the
   * "boxed" {@link Val} objects into concrete Java types, to verify that they
   * have the correct signature, and to dispatch to the appropriate
   * {@code apply(...)} method in the class {@code cc}. The generated method
   * aims to produce clear and understandable error messages in case any
   * runtime exception occurs.
   */
  private void augmentClass(CtClass cc) {
    try {
      MyMethodInfo[] applyMethods = getApplies(cc);
      if (applyMethods.length == 0) {
        throw new RuntimeException("Class " + cc.getName() + " does not define any apply() methods.");
      }
      String apply0Body = applyMethods.length == 1? makeApply0Single(applyMethods[0]) : makeApply0Multi(applyMethods);
      if (DEBUG) {
        System.out.println("\n[Source code of " + cc.getSimpleName() + ".apply0()]:\n");
        System.out.println(apply0Body);
        System.out.println("\n");
      }
      cc.addMethod(CtMethod.make(apply0Body, cc));
    } catch (RuntimeException e) {
      throw new RuntimeException("[In class " + cc.getName() + "]: " + e.getMessage());
    } catch (NotFoundException | CannotCompileException e) {
      throw new RuntimeException(e.getMessage());
    }
  }


  /**
   * Helper class which holds information about a {@link CtMethod}, avoiding
   * having to recalculate it in multiple places.
   */
  private static class MyMethodInfo {
    public CtClass[] params;
    public CtClass retType;
    public boolean isVararg;

    public MyMethodInfo(CtMethod method) throws NotFoundException {
      params = method.getParameterTypes();
      retType = method.getReturnType();
      isVararg = (method.getModifiers() & Modifier.VARARGS) != 0;
    }
  }


  /**
   * Find all {@code apply(...)} methods in the class {@code cc}, and return
   * their {@link MyMethodInfo} records.
   */
  private MyMethodInfo[] getApplies(CtClass cc) throws NotFoundException {
    ArrayList<MyMethodInfo> res = new ArrayList<>(2);
    for (CtMethod method : cc.getDeclaredMethods()) {
      if (method.getName().equals("apply"))
        res.add(new MyMethodInfo(method));
    }
    return res.toArray(new MyMethodInfo[res.size()]);
  }


  /**
   * Create the body of the {@code apply0(Val[])} method, and return it
   * as a string. This method works for the case when there is only one
   * {@code apply(...)} method in the target class.
   * <p>
   * When there is only one {@code apply(...)} method in the class (which is
   * the common case), then the type of each argument is known exactly, and
   * we can generate more specific error messages compared to the case of
   * multiple dispatch.
   */
  private String makeApply0Single(MyMethodInfo applyMethod) throws NotFoundException {
    final int nParams = applyMethod.params.length;

    SB sb = new SB();
    SB argsList = new SB();

    sb.p("public Val apply0(Val[] args) {\n");
    sb.p("  final int n = args.length;\n");
    if (applyMethod.isVararg) {
      // It is allowed for the vararg argument to have 0 elements, so the
      // smallest valid number of parameters is actually nParams - 1.
      sb.p("  argumentsCountCheck(n, ").p(nParams - 1).p(", ").p(-1).p(");\n");
    } else {
      sb.p("  argumentsCountCheck(n, ").p(nParams).p(", ").p(nParams).p(");\n");
    }

    for (int i = 0; i < nParams - (applyMethod.isVararg? 1 : 0); i++) {
      String valTYPE = getValType(applyMethod.params[i].getName());
      String valType = StringUtils.capitalize(valTYPE);
      sb.p("  checkArg(args, ").p(i).p(", Val.Type.").p(valTYPE).p(");\n");
      if (i > 0) argsList.p(", ");
      argsList.p("args[").p(i).p("].get").p(valType).p("()");
    }
    if (applyMethod.isVararg) {
      int k = nParams - 1;
      String javaType = applyMethod.params[k].getName();
      assert javaType.endsWith("[]");
      javaType = javaType.substring(0, javaType.length() - 2);
      String valTYPE = getValType(javaType);
      String valType = StringUtils.capitalize(valTYPE);
      sb.p("  ").p(javaType).p("[] vararg = new ").p(javaType).p("[n - ").p(k).p("];\n");
      sb.p("  for (int i = ").p(k).p("; i < n; ++i) {\n");
      sb.p("    checkArg(args, i, Val.Type.").p(valTYPE).p(");\n");
      sb.p("    vararg[i - ").p(k).p("] = args[i].get").p(valType).p("();\n");
      sb.p("  }\n");
      if (k > 0) argsList.p(", ");
      argsList.p("vararg");
    }

    String retTypeName = applyMethod.retType.getName();
    if (retTypeName.equals(Val.class.getName())) {
      sb.p("  return apply(").p(argsList).p(");\n");
    } else {
      Val.Type retValType = Val.Type.valueOf(getValType(retTypeName));
      sb.p("  ").p(retTypeName).p(" ret = apply(").p(argsList).p(");\n");
      sb.p("  return new ").p(retValType.getValClassName()).p("(ret);\n");
    }
    sb.p("}");
    return sb.toString();
  }


  /**
   * Create the body of the {@code apply0(Val[])} method, and return it
   * as a string. This method works for the case when there are multiple
   * {@code apply(...)} methods in the target class.
   * <p>
   * For example, suppose the target class defines 4 {@code apply()} methods:
   * <pre>{@code
   *   apply(Frame, int, boolean);
   *   apply(Frame, double);
   *   apply(Frame, double, String);
   *   apply(Frame, int, String[]);
   * }</pre>
   */
  private String makeApply0Multi(MyMethodInfo[] applyMethods) throws NotFoundException {
    assert applyMethods.length >= 2;

    // Determine the smallest and the largest number of arguments in each {@code apply()} method.
    // If any method has varargs, then {@code maxArgs} will be -1.
    int minArgs = Integer.MAX_VALUE, maxArgs = 0;
    for (MyMethodInfo method : applyMethods) {
      int nParams = method.params.length;
      minArgs = Math.min(minArgs, method.isVararg? nParams - 1 : nParams);
      maxArgs = method.isVararg || maxArgs == -1? -1 : Math.max(maxArgs, nParams);
    }

    SB sb = new SB();
    sb.p("public Val apply0(Val[] args) {\n");
    sb.p("  final int n = args.length;\n");
    sb.p("  argumentsCountCheck(n, ").p(minArgs).p(", ").p(maxArgs).p(");\n");

    List<MyMethodInfo> allMethods = Arrays.asList(applyMethods);
    throw new RuntimeException("Unimplemented");
//    if (minArgs == maxArgs) {
//    } else {
//      sb.p("  if (n ").p(isVararg? ">=" : "==").p(" ").p(params.length).p(") {\n");
//      writeArgumentTypeChecks(params, isVararg, retType, "    ", sb);
//      sb.p("  }\n");
//    }
//    sb.p("  throw new java.lang.IllegalArgumentException(\"No matching signature\");\n");
//    sb.p("}");
//    return sb.toString();
  }


  private void writeArgumentTypeChecks(CtClass[] params, boolean isVararg, CtClass retType, String indent, SB sb) {
    SB argsList = new SB();
    int nRegularParams = params.length - (isVararg? 1 : 0);
    for (int i = 0; i < nRegularParams; i++) {
      String valType = getValType(params[i].getName());
      sb.p(indent).p("checkArg(args, ").p(i).p(", Val.Type.").p(valType).p(");\n");
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
      sb.p(indent).p("  checkArg(args, i, Val.Type.").p(valType).p(");\n");
      sb.p(indent).p("  vararg[i - ").p(k).p("] = args[i].get").p(valTypeC).p("();\n");
      sb.p(indent).p("}\n");
      if (k > 0) argsList.p(", ");
      argsList.p("vararg");
    }
    sb.p(indent).p(retType.getName()).p(" ret = apply(").p(argsList).p(");\n");
    Val.Type retValType = Val.Type.valueOf(getValType(retType.getName()));
    sb.p(indent).p("return new ").p(retValType.getValClassName()).p("(ret);\n");
  }



  /** Mapping from Java types to {@link Val.Type}s. */
  private static final Map<String, String> TYPE_MAP = new HashMap<>(9);
  static {
    TYPE_MAP.put("boolean", "BOOL");
    TYPE_MAP.put("int", "INT");
    TYPE_MAP.put("long", "INT");
    TYPE_MAP.put("double", "NUM");
    TYPE_MAP.put("java.lang.String", "STR");
    TYPE_MAP.put("double[]", "NUMS");
    TYPE_MAP.put("java.lang.String[]", "STRS");
    TYPE_MAP.put(CFrame.class.getName(), "FRAME");
    TYPE_MAP.put(IdList.class.getName(), "IDS");
  }

  /** Helper method to translate Java types into Val types. */
  private String getValType(String javaType) {
    String valType = TYPE_MAP.get(javaType);
    if (valType == null)
      throw new RuntimeException("Parameter of type " + javaType + " is not supported in .apply() method.");
    return valType;
  }
}
