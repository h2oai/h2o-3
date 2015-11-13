package water.codegen.java;

import water.codegen.SimpleCodeGenerator;

/**
 * Shared implementation for large array generator.
 */
abstract public class ArrayGenerator<S extends ArrayGenerator<S>> extends SimpleCodeGenerator<S> {

  /** Modifiers for generated classes. */
  int modifiers;
  /** Array component type */
  String type;
  /** Class containter which will hold generated classes. */
  ClassGenContainer classContainer;
  /** Prefix for generated classes. */
  String prefix;

  final int off;
  final int len;

  public ArrayGenerator(int off, int len) {
    this.off = off;
    this.len = len;
  }

  public S withModifiers(int...modifiers) {
    for (int m : modifiers) {
      this.modifiers |= m;
    }
    return self();
  }

  public S withType(String type) {
    this.type = type;
    return self();
  }

  public S withPrefix(String prefix) {
    this.prefix = prefix;
    return self();
  }

  // Target for new classes
  public S withClassContainer(ClassGenContainer classContainer) {
    this.classContainer = classContainer;
    return self();
  }
}
