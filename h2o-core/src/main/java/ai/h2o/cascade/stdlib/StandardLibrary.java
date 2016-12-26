package ai.h2o.cascade.stdlib;

import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.CascadeSession;
import ai.h2o.cascade.core.ICascadeLibrary;
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
 * Every function that's being written by the user should be registered here,
 * within the private constructor.
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

    String[] frameCmds = {"col", "ncols", "nrows"};
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
   * of the library. The class for this command is assumed to be
   * {@code stdlib.{pkg}.Fn{Name}}.
   * <p>
   * This method loads the command's class, and then adds the
   * {@code Val apply0(Val[])} method using {@link #augmentClass(CtClass)}.
   */
  private void registerCommand(String pkg, String name) {
    String className = "ai.h2o.cascade.stdlib." + pkg + ".Fn" + StringUtils.capitalize(name);
    ClassPool cp = ClassPool.getDefault();
    cp.importPackage("ai.h2o.cascade.vals");
    cp.importPackage("ai.h2o.cascade.core");
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
   * called from {@link ai.h2o.cascade.asts.AstApply#exec(Scope)} to
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
      List<MInfo> applyMethods = getApplies(cc);
      if (applyMethods.isEmpty()) {
        throw new RuntimeException("Class " + cc.getName() + " does not define any apply() methods.");
      }
      String apply0Body = makeApply0(applyMethods);
      if (DEBUG) {
        System.out.println("\n[Source code of " + cc.getSimpleName() + ".apply0()]:\n");
        System.out.println(apply0Body);
        System.out.println("\n");
      }
      cc.addMethod(CtMethod.make(apply0Body, cc));
    } catch (RuntimeException | AssertionError e) {
      throw new RuntimeException("[In class " + cc.getName() + "]: " + e.getMessage(), e);
    } catch (NotFoundException | CannotCompileException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }


  /**
   * Find all {@code apply(...)} methods in the class {@code cc}, and return
   * their {@link MInfo} records.
   */
  private List<MInfo> getApplies(CtClass cc) throws NotFoundException {
    List<MInfo> res = new LinkedList<>();
    for (CtMethod method : cc.getDeclaredMethods()) {
      if (method.getName().equals("apply"))
        res.add(new MInfo(method));
    }
    return res;
  }



  /**
   * Create the body of the {@code apply0(Val[])} method, and return it
   * as a string. This method works for the case when there are multiple
   * {@code apply(...)} methods in the target class.
   * <p>
   * For example, suppose the target class defines the following
   * {@code apply()} methods:
   * <pre>{@code
   *   apply()
   *   apply(CFrame, int, boolean);
   *   apply(CFrame, double);
   *   apply(CFrame, double, String);
   *   apply(CFrame, int, String[]);
   *   apply(CFrame, int, int, int, int, int);
   * }</pre>
   */
  private String makeApply0(List<MInfo> applyMethods) throws NotFoundException {
    SB sb = new SB();
    sb.p("public Val apply0(Val[] args) {\n");
    sb.p("  final int n = args.length;\n");
    generateNestedChecks(applyMethods, 0, 0, 255, "  ", "", sb);
    sb.p("}");
    return sb.toString();
  }


  /**
   * This function provides support for {@link #makeApply0(List)}. Its
   * job is to recursively build nested checkers for arguments type,
   * implementing the multiple dispatch mechanism.
   *
   * @param methods List of methods, with their first {@code iarg} arguments
   *                having same types.
   * @param iarg Index of the argument that should be tested next. The list of
   *             {@code methods} will have all the preceding arguments already
   *             checked. Thus it is guaranteed that {@code n >= iarg}.
   *             Note that for each method in the list, one of the 3 cases is
   *             possible: (1) either the method's signature has exactly
   *             {@code iarg} items, or (2) the method's signature has
   *             {@code iarg+1} arguments however the last one is a vararg,
   *             or (3) the method's {@code iarg}'th argument exists and is not
   *             a vararg.
   * @param indent Indentation string, for pretty-printing.
   * @param args Arguments string so far. This will be supplied to the final
   *             {@code apply()} method.
   * @param sb String Buffer to write to.
   */
  private void generateNestedChecks(
      List<MInfo> methods, int iarg, int minChecked, int maxChecked, String indent, String args, SB sb
  ) {
    // First we need to consider the case when there is a method whose number
    // of arguments is equal to {@code iarg} -- those methods can be dispatched
    // without any further checks if the number of arguments is correct.
    int num0Methods = 0;
    Iterator<MInfo> iter = methods.iterator();
    while (iter.hasNext()) {
      MInfo method = iter.next();
      if (method.maxNumArgs() == iarg) {
        iter.remove();  // remove the "0-method" from the list of methods
        num0Methods++;
        if (minChecked < iarg || maxChecked > iarg) {
          sb.p(indent).p("if (n == ").p(iarg).p(") {\n");
          writeReturnStatement(method, indent + "  ", args, sb);
          sb.p(indent).p("}\n");
        } else {
          writeReturnStatement(method, indent, args, sb);
          return;
        }
      }
    }
    assert num0Methods <= 1 : "Number of 0-methods is > 1";

    if (methods.size() == 1) {
      generateSingleMethodCheck(methods.get(0), iarg, minChecked, maxChecked, indent, args, sb);
      return;
    }
    sortMethods(methods, iarg);

    // Second, we will consider all methods where {@code iarg}th item is a
    // vararg. (Unlike in Java, in our implementation these take precedence
    // over the regular methods).
    iter = methods.iterator();
    int numVMethods = 0;
    while (iter.hasNext()) {
      MInfo method = iter.next();
      if (method.isVararg && method.minNumArgs() == iarg) {
        iter.remove();
        String flagName = "flag" + (numVMethods++);
        String varargClassname = method.argJavaName(iarg);
        String valType = method.argValType(iarg);
        sb.p(indent).p("boolean ").p(flagName).p(" = true;\n");
        sb.p(indent).p("for (int i = ").p(iarg).p("; i < n; ++i) {\n");
        sb.p(indent).p("  if (!args[i].is").p(valType).p("()) {\n");
        sb.p(indent).p("    ").p(flagName).p(" = false;\n");
        sb.p(indent).p("    break;\n");
        sb.p(indent).p("  }\n");
        sb.p(indent).p("}\n");
        sb.p(indent).p("if (").p(flagName).p(") {\n");
        sb.p(indent).p("  ").p(varargClassname).p("[] vararg = new ").p(varargClassname).p("[n - ").p(iarg).p("];\n");
        sb.p(indent).p("  for (int i = ").p(iarg).p("; i < n; ++i) {\n");
        sb.p(indent).p("    vararg[i - ").p(iarg).p("] = args[i].get").p(valType).p("();\n");
        sb.p(indent).p("  }\n");
        args = args.isEmpty()? "vararg" : args + ", vararg";
        writeReturnStatement(method, indent + "  ", args, sb);
        sb.p(indent).p("}\n");
      }
    }

    // Insert argument count check.
    if (!methods.isEmpty()) {
      int minArgs = Integer.MAX_VALUE, maxArgs = 0;
      for (MInfo method : methods) {
        minArgs = Math.min(minArgs, method.minNumArgs());
        maxArgs = Math.max(maxArgs, method.maxNumArgs());
      }
      assert minArgs >= iarg + 1 : "Unexpected minArgs: " + minArgs;
      if (minArgs > minChecked || maxArgs < maxChecked) {
        sb.p(indent).p("argumentsCountCheck(n, ").p(minArgs).p(", ").p(maxArgs).p(");\n");
        minChecked = minArgs;
        maxChecked = maxArgs;
      }
    }

    // Finally, write a series of if-checks for each possible type at the
    // {@code iarg}-th position.
    while (!methods.isEmpty()) {
      if (methods.size() == 1) {
        generateSingleMethodCheck(methods.get(0), iarg, minChecked, maxChecked, indent, args, sb);
        return;
      }
      String currType = methods.get(0).argValType(iarg);
      String nextArgs = args + (args.isEmpty()? "" : ", ") + "args[" + iarg + "].get" + currType + "()";
      List<MInfo> filteredMethods = new LinkedList<>();
      iter = methods.iterator();
      while (iter.hasNext()) {
        MInfo method = iter.next();
        if (method.argValType(iarg).equals(currType)) {
          iter.remove();
          filteredMethods.add(method);
        }
      }
      if (methods.isEmpty()) {
        sb.p(indent).p("checkArg(").p(iarg).p(", args[").p(iarg).p("], Val.Type.").p(currType.toUpperCase()).p(");\n");
        generateNestedChecks(filteredMethods, iarg + 1, minChecked, maxChecked, indent, nextArgs, sb);
      } else {
        sb.p(indent).p("if (args[").p(iarg).p("].is").p(currType).p("()) {\n");
        generateNestedChecks(filteredMethods, iarg + 1, minChecked, maxChecked, indent + "  ", nextArgs, sb);
        sb.p(indent).p("}\n");
      }
    }
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
  private String generateSingleMethodCheck(
      MInfo method, int iarg, int minChecked, int maxChecked, String indent, String args, SB sb
  ) {
    if (method.minNumArgs() > minChecked || method.maxNumArgs() < maxChecked)
      sb.p(indent).p("argumentsCountCheck(n, ").p(method.minNumArgs()).p(", ").p(method.maxNumArgs()).p(");\n");

    SB argsList = new SB(args);
    for (int i = iarg; i < method.minNumArgs(); i++) {
      sb.p(indent).p("checkArg(").p(i).p(", args[").p(i).p("], Val.Type.").p(method.argValTYPE(i)).p(");\n");
      if (i > 0) argsList.p(", ");
      argsList.p("args[").p(i).p("].get").p(method.argValType(i)).p("()");
    }
    if (method.isVararg) {
      int k = method.minNumArgs();
      String javaType = method.argJavaName(k);
      sb.p(indent).p(javaType).p("[] vararg = new ").p(javaType).p("[n - ").p(k).p("];\n");
      sb.p(indent).p("for (int i = ").p(k).p("; i < n; ++i) {\n");
      sb.p(indent).p("  checkArg(i, args[i], Val.Type.").p(method.argValTYPE(k)).p(");\n");
      sb.p(indent).p("  vararg[i - ").p(k).p("] = args[i].get").p(method.argValType(k)).p("();\n");
      sb.p(indent).p("}\n");
      if (k > 0) argsList.p(", ");
      argsList.p("vararg");
    }
    writeReturnStatement(method, indent, argsList.toString(), sb);
    return sb.toString();
  }


  /**
   * Write the "return" expression for the generated {@code apply0(Val[])}
   * function to the output stream {@code sb}. This method will handle return
   * types which are Java primitives, as well as produce correct code for the
   * {@code void} return type, or when the underlying {@code method} returns
   * a {@link Val}.
   *
   * @param method {@code apply(...)} method that the return statement should
   *               invoke.
   * @param indent Code indent level.
   * @param args Unwrapped arguments to the {@code apply(...)} method.
   * @param sb String buffer where to write the return statement.
   */
  private void writeReturnStatement(MInfo method, String indent, String args, SB sb) {
    String retClass = method.retValName();
    switch (retClass) {
      case "Val":
        sb.p(indent).p("return apply(").p(args).p(");\n");
        break;
      case "ValNull":
        sb.p(indent).p("apply(").p(args).p(");\n");
        sb.p(indent).p("return new ValNull();\n");
        break;
      default:
        sb.p(indent).p(method.retType.getSimpleName()).p(" ret = apply(").p(args).p(");\n");
        sb.p(indent).p("return new ").p(retClass).p("(ret);\n");
        break;
    }
  }


  /**
   * Sort the list of methods on their {@code level}'th element. It is
   * guaranteed that each method's signature will have at least {@code level+1}
   * arguments.
   *
   * <p>The sort order is such that booleans should be the first, followed by
   * ints, followed by all other types.
   */
  private void sortMethods(List<MInfo> methods, int level) {
    Collections.sort(methods, new MInfo.Comparator(level));
  }



  //--------------------------------------------------------------------------------------------------------------------
  // "Method Info" helper class
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Helper class which holds information about a {@link CtMethod}, avoiding
   * having to recalculate it in multiple places. In addition, it also provides
   * certain convenience accessors to method's properties.
   */
  private static class MInfo {
    public CtClass[] params;
    public CtClass retType;
    public boolean isVararg;

    public MInfo(CtMethod method) throws NotFoundException {
      params = method.getParameterTypes();
      retType = method.getReturnType();
      isVararg = (method.getModifiers() & Modifier.VARARGS) != 0;
    }

    /** Smallest number of arguments the method may take. */
    public int minNumArgs() {
      // It is allowed for the vararg argument to have 0 elements, so the
      // smallest valid number of parameters is actually nParams - 1.
      return params.length + (isVararg? -1 : 0);
    }

    /** Largest number of arguments the method may take. */
    public int maxNumArgs() {
      return isVararg? 255 : params.length;
    }

    /** Return Java class name of the method's return type. */
    public String retJavaName() {
      return retType.getName();
    }

    /** Return Val's class name corresponding to the method's return type. */
    public String retValName() {
      String retName = retJavaName();
      if (retName.equals(Val.class.getName())) return "Val";
      Val.Type retValType = Val.Type.valueOf(getValType(retName));
      return retValType.getValClassName();
    }

    public String argJavaName(int i) {
      if (isVararg && i == params.length - 1) {
        String name = params[i].getName();
        assert name.endsWith("[]");
        return name.substring(0, name.length() - 2);
      }
      return params[i].getName();
    }

    public String argValTYPE(int i) {
      return getValType(argJavaName(i));
    }

    public String argValType(int i) {
      return StringUtils.capitalize(argValTYPE(i));
    }


    /** Mapping from Java types to {@link Val.Type}s. */
    private static final Map<String, String> TYPE_MAP = new HashMap<>(9);
    static {
      TYPE_MAP.put("void", "NULL");
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
    private static String getValType(String javaType) {
      String valType = TYPE_MAP.get(javaType);
      if (valType == null)
        throw new RuntimeException("Parameter of type " + javaType + " is not supported in .apply() method.");
      return valType;
    }


    public static class Comparator implements java.util.Comparator<MInfo> {
      private int index;
      public Comparator(int i) {
        index = i;
      }
      @Override public int compare(MInfo o1, MInfo o2) {
        return key(o1) - key(o2);
      }
      private int key(MInfo mi) {
        switch (mi.argValTYPE(index)) {
          case "BOOL": return 2;
          case "INT": return 1;
          default: return 0;
        }
      }

    }
  }

}
