package water.udf;

import java.util.*;

/**
 * Elements of Functional Programming (known as FP) in Java
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Functional_programming">Wikipedia</a>
 * for details
 */
public class FP {
  
  interface Option<T> extends Iterable<T> {
     T get();
  }
  
  public final static Option<Object> None = new Option<Object>() {
    @Override
    public Iterator<Object> iterator() {
      return Collections.EMPTY_SET.iterator();
    }
    
    @SuppressWarnings("unchecked")
    public Option<Object> get() { return this; }
    
    @Override public String toString() { return "None"; }
  };
  
  public final static class Some<T> implements Option<T> {
    private List<T> contents;
    public Some(T t) { contents = Collections.singletonList(t); }

    @Override public Iterator<T> iterator() {
      return contents.iterator();
    }

    @SuppressWarnings("unchecked")
    public T get() { return contents.get(0); }

    @Override public String toString() { return "Some(" + get() + ")"; }
  }

  public static <T> Option<T> Some(T t) {
    return new Some<>(t);
  }

  @SuppressWarnings("unchecked")
  public static <T> Option<T> Option(T t) {
    return t == null ? None : new Some(t);
  }

  @SuppressWarnings("unchecked")
  public static <T> Option<T> flatten(Option<?> optOptT) {
    return (Option<T>)optOptT.get();
  }

  public static <T> Option<T> headOption(Iterator<T> it) {
    return Option(it.hasNext() ? it.next() : null);
  }

  public static <T> Option<T> headOption(Iterable<T> ts) {
    return headOption(ts.iterator());
  }

//  public static void main(String[] args) {
//    Option<?> oo1 = None;
//    Option<?> oo2 = Some(None);
//    Option<?> oo3 = Some(oo2);
//    System.out.println(flatten(oo1));
//    System.out.println(flatten(oo2));
//    System.out.println(flatten(oo3));
//    System.out.println(flatten(Some(Some(123))));
//    System.out.println(flatten(Option(Option(123))));
//    System.out.println(flatten(Option(Option(null))));
//  }
}
