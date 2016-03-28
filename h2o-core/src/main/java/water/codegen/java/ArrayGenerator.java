package water.codegen.java;

/**
 * Shared implementation for large array generator.
 * // FIXME: useless guy in hierrachy
 */
abstract public class ArrayGenerator<S extends ArrayGenerator<S>> extends ValueCodeGenerator<S> {

  /** Modifiers for generated classes. */
  int modifiers;
  /** Array component type */
  Class type; // FIXME why we have here component type?

  final int off;
  final int len;

  public ArrayGenerator(int off, int len) {
    this.off = off;
    this.len = len;
  }

  // FIXME: remove - we are generating value, not field
  public S withModifiers(int...modifiers) {
    for (int m : modifiers) {
      this.modifiers |= m;
    }
    return self();
  }

  public S withType(Class type) {
    this.type = type;
    return self();
  }

}
