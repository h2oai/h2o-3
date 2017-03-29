package water.udf;

/**
 * Custom function loader interface.
 *
 * The custom function loader provide way of
 * instantiating give function.
 */
abstract public class CFuncLoader {

  /**
   * Supported language.
   * @return  language of function this provider can instantiate.
   */
  public abstract String getLang();

  public <F> F load(String jfuncName, Class<? extends F> targetKlazz) {
    return load(jfuncName, targetKlazz, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Instantiate demanded function.
   * 
   * @param jfuncName  function name - this is target language specific!
   * @param targetKlazz  requested function Java interface
   * @param classLoader  classloader to use for function search
   * @param <F>  type of function
   * @return  return an object implementing given interface or null.
   */
  public abstract <F> F load(String jfuncName, Class<? extends F> targetKlazz, ClassLoader classLoader);
}
