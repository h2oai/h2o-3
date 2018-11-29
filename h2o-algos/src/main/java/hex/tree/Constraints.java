package hex.tree;

import water.Iced;

public class Constraints extends Iced<Constraints> {

  private Constraint[] _cs;

  public Constraints(Constraint[] cs) {
    _cs = cs;
  }

  Constraint getColumnConstraint(int col) {
    return _cs[col];
  }

  Constraints withNewConstraint(int col, Constraint replacement) {
    Constraint[] ncs = _cs.clone();
    ncs[col] = replacement;
    return new Constraints(ncs);
  }

}
