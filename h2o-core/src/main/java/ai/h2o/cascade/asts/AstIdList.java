package ai.h2o.cascade.asts;

import water.util.SB;

import java.util.ArrayList;


/**
 * List of unevaluated identifiers.
 */
public class AstIdList extends Ast<AstIdList> {
  String[] ids;
  String argsId;

  public AstIdList(ArrayList<String> names, String argsName) {
    ids = names.toArray(new String[names.size()]);
    argsId = argsName;
  }

  @Override
  public String str() {
    SB sb = new SB("`");
    for (int i = 0; i < ids.length; i++) {
      if (i > 0) sb.p(' ');
      sb.p(ids[i]);
    }
    if (argsId != null) {
      if (ids.length > 0) sb.p(' ');
      sb.p('*').p(argsId);
    }
    sb.p('`');
    return sb.toString();
  }

}
