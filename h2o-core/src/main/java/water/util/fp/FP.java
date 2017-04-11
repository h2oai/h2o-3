package water.util.fp;

import water.util.Java7;

import java.util.*;

/**
 * Elements of Functional Programming (known as FP) in Java
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Functional_programming">Wikipedia</a>
 * for details
 */
public class FP {

  public static int hashCode(Object o) {
    return o != null ? o.hashCode() : 0;
  }

  public interface Option<T> extends Iterable<T> {
    boolean isEmpty();
    boolean nonEmpty();
    <U> Option<U> flatMap(PartialFunction<T, U> f);
  }
  
  public final static Option<?> None = new Option<Object>() {
    
    @Override public boolean isEmpty() { return true; }

    @Override public boolean nonEmpty() { return false; }

    @SuppressWarnings("unchecked")
    @Override public <U> Option<U> flatMap(PartialFunction<Object, U> f) {
      return (Option<U>) None;
    }

    @Override public Iterator<Object> iterator() {
      return Collections.emptyList().iterator();
    }
    
    @Override public String toString() { return "None"; }

    @Override public int hashCode() { return -1; }
  };

  @SuppressWarnings("unchecked")
  public static <T> Option<T> none() { return (Option<T>) None; }

  public final static class Some<T> implements Option<T> {
    private List<T> contents;
    
    public Some(T t) { contents = Collections.singletonList(t); }

    @Override public boolean isEmpty() { return false; }

    @Override public boolean nonEmpty() { return true; }

    @Override
    public <U> Option<U> flatMap(PartialFunction<T, U> f) {
      return f.apply(get());
    }

    @Override public Iterator<T> iterator() {
      return contents.iterator();
    }

    @SuppressWarnings("unchecked")
    public T get() { return contents.get(0); }

    @Override public String toString() { return "Some(" + get() + ")"; }

    @Override public boolean equals(Object o) {
      return this == o || 
             (o instanceof Some && Java7.Objects.equals(get(), (((Some<?>) o).get())));
    }

    @Override public int hashCode() { return Java7.Objects.hashCode(get()); }
  }

  public static <T> Option<T> Some(T t) {
    return new Some<>(t);
  }

  @SuppressWarnings("unchecked")
  public static <T> Option<T> Option(T t) {
    return t == null ? (Option<T>)None : new Some(t);
  }

  @SuppressWarnings("unchecked")
  public static <T> Option<T> flatten(Option<Option<T>> optOptT) {
    return optOptT.isEmpty() ? (Option<T>)None : ((Some<Option<T>>)optOptT).get();
  }

  public static <T> Option<T> headOption(Iterator<T> it) {
    return Option(it.hasNext() ? it.next() : null);
  }

  public static <T> Option<T> headOption(Iterable<T> ts) {
    return headOption(ts.iterator());
  }
  
  public <T> String mkString(T[] data, int from, int to, String sep) {
    StringBuilder sb = new StringBuilder();
    for (int i = from; i < to; i++) {
      if (sb.length() > 0) sb.append(sep);
      sb.append(data[i]);
    }
    return sb.toString();
  }
}
