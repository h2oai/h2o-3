package water.codegen;

/**
 * FIXME:
 */
abstract public class SimpleCodeGenerator<S extends SimpleCodeGenerator<S>> implements CodeGenerator, HasId<S> {

  String id;

  @Override
  public S withId(String id) {
    this.id = id;
    return (S) this;
  }

  @Override
  public String id() {
    return id;
  }

  protected final S self() {
    return (S) this;
  }
}
