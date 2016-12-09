package water;

/**
 * Created by tomas on 11/5/16.
 * Interface to be used by LocalMR.
 *
 */
public abstract class MrFun<T extends MrFun<T>> extends Iced<T> {
  protected abstract void map(int id);
  protected void reduce(T t) {}
  protected MrFun<T> makeCopy() {
    return clone();
  }
}
