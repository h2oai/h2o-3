package water.util.fp;

import java.util.*;

/**
 * Elements of Functional Programming (known as FP) in Java
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Functional_programming">Wikipedia</a>
 * for details
 */
public class FP {

  // the following two borrowed from Java 7 library.
  public static boolean equal(Object a, Object b) {
    return (a == b) || (a != null && a.equals(b));
  }
  public static int hashCode(Object o) {
    return o != null ? o.hashCode() : 0;
  }

  interface Option<T> extends Iterable<T> {
    boolean isEmpty();
    boolean nonEmpty();
    <U> Option<U> flatMap(Function<T, Option<U>> f);
  }
  
  public final static Option<?> None = new Option<Object>() {
    
    @Override public boolean isEmpty() { return true; }

    @Override public boolean nonEmpty() { return false; }

    @SuppressWarnings("unchecked")
    @Override public <U> Option<U> flatMap(Function<Object, Option<U>> f) {
      return (Option<U>) None;
    }

    @Override public Iterator<Object> iterator() {
      return Collections.emptyList().iterator();
    }
    
    @Override public String toString() { return "None"; }

    @Override public int hashCode() { return -1; }
  };
  
  public final static class Some<T> implements Option<T> {
    private List<T> contents;
    
    public Some(T t) { contents = Collections.singletonList(t); }

    @Override public boolean isEmpty() { return false; }

    @Override public boolean nonEmpty() { return true; }

    @Override
    public <U> Option<U> flatMap(Function<T, Option<U>> f) {
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
             (o instanceof Some && equal(get(), (((Some<?>) o).get())));
    }

    @Override public int hashCode() { return FP.hashCode(get()); }
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
}
