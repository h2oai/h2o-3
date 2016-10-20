package water.rapids.ast;


public abstract class AstParameter extends AstRoot {

  @Override
  public String example() {
    return null;
  }

  @Override
  public String description() {
    return null;
  }

  public String toJavaString() {
    return str();
  }

  // Select columns by number or String.
  public int[] columns(String[] names) {
    throw new IllegalArgumentException("Requires a number-list, but found an " + getClass().getSimpleName());
  }


}
