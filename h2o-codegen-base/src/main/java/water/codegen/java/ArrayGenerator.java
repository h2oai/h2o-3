package water.codegen.java;

/**
 * Shared implementation for large array generator.
 * // FIXME: useless guy in hierrachy
 */
abstract public class ArrayGenerator<S extends ArrayGenerator<S>> extends ValueCodeGenerator<S> {

  /** Modifiers for generated classes. */
  int modifiers;

  final int off;
  final int len;

  public ArrayGenerator(int off, int len) {
    this.off = off;
    this.len = len;
  }
}
