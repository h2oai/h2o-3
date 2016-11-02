package water.udf;

import java.util.*;
import java.util.function.Consumer;

/**
 * Elements of FP in Java
 */
public class FP {
  interface Option<T> extends Iterable<T> {
    <X> X get();
  }
  
  public final static Option<Object> None = new Option<Object>() {
    @Override
    public Iterator<Object> iterator() {
      return Collections.emptyIterator();
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
  
  public static <T> Option<T> flatten(Option<?> optOptT) {
    return optOptT.get();
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
