package water.rapids.ast;


public abstract class AstParameter extends AstRoot {

  @Override
  public int nargs() {
    return 1;
  }

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

}
