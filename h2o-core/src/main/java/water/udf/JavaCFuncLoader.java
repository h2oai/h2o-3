package water.udf;

/**
 * Custom function loader of java-based function.
 *
 * This loader is a tiny wrapper around {@link ClassLoader#loadClass(String, boolean)} call.
 */
public class JavaCFuncLoader extends CFuncLoader {

  @Override
  public String getLang() {
    return "java";
  }
  
  @Override
  public <F> F load(String jfuncName, Class<? extends F> targetKlazz, ClassLoader classLoader) {
    try {
      return (F) classLoader.loadClass(jfuncName).newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
