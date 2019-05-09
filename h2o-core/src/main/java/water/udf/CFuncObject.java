package water.udf;

abstract public class CFuncObject<T extends CFunc> {
    protected final CFuncRef cFuncRef;
    protected transient T func;

    public CFuncObject(CFuncRef cFuncRef) {
        this.cFuncRef = cFuncRef;
        if (cFuncRef != null) {
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

    public T getFunc() {
        return func;
    }
}
