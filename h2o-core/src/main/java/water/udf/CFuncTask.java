package water.udf;

import water.MRTask;

/**
 * Low-level MRTask to invoke given function stored in DKV.
 */
abstract public class CFuncTask<T extends CFunc, S extends CFuncTask<T,S>> extends MRTask<S> {

  protected final CFuncRef cFuncRef;
  protected transient T func;

  public CFuncTask(CFuncRef cFuncRef) {
    this.cFuncRef = cFuncRef;
  }
  
  @Override
  protected void setupLocal() {
    if (cFuncRef != null && func == null) {
      ClassLoader localCl = getFuncClassLoader();
      CFuncLoader loader = CFuncLoaderService.INSTANCE.getByLang(cFuncRef.language);
      if (loader != null) {
        func = loader.load(cFuncRef.funcName, getFuncType(), localCl);
      }
    }
  }

  protected ClassLoader getFuncClassLoader() {
    return new DkvClassLoader(cFuncRef.getKey(), getParentClassloader());
  }

  protected ClassLoader getParentClassloader() {
    return Thread.currentThread().getContextClassLoader();
  }

  abstract protected Class<T> getFuncType();

  // TODO: we should cleanup loader
}
