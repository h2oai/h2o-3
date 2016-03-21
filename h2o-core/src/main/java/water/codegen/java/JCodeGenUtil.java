package water.codegen.java;

import javassist.compiler.CodeGen;

import java.util.List;

import water.codegen.CodeGenerator;
import water.codegen.JCodeSB;
import water.codegen.SB;

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

  public static MethodCodeGenerator ctor(String klazzName) {
    return method(klazzName);
  }

  public static MethodCodeGenerator method(String methodName) {
    return new MethodCodeGenerator(methodName);
  }

  public static FieldCodeGenerator field(String fieldType, String fieldName) {
    return new FieldCodeGenerator(fieldName).withType(fieldType);
  }

  public static FieldCodeGenerator field(String fieldName) {
    return new FieldCodeGenerator(fieldName);
  }

  public static <T> LargeArrayGenerator larray(String name, T[] value) {
    return new LargeArrayGenerator(name, value, 0, value != null ? value.length : 0);
  }

  public static <T> LargeArrayGenerator larray(String name, T[] value, int off, int len) {
    return new LargeArrayGenerator(name, value, off, len);
  }

  public static SB s(String s) {
    return new SB(s);
  }

  public static SB s(Class c) {
    return new SB().pj(c);
  }

  public static SB s() {
    return new SB();
  }
}
