package ai.h2o.cascade.asts;

import ai.h2o.cascade.core.IdList;
import ai.h2o.cascade.vals.ValIdList;
import water.util.SB;

import java.util.ArrayList;


/**
 * List of unevaluated identifiers.
 */
public class AstIdList extends Ast<AstIdList> {
  private ValIdList value;

  public AstIdList(ArrayList<String> names, String argsName) {
    value = new ValIdList(new IdList(names.toArray(new String[names.size()]), argsName));
  }

  public AstIdList(ValIdList v) {
    value = v;
  }

  @Override
  public ValIdList exec() {
    return value;
  }

  @Override
  public String str() {
    IdList idlist = value.getIds();
    int numIds = idlist.numIds();
    String argsId = idlist.getVarargId();
    SB sb = new SB("`");
    for (int i = 0; i < numIds; i++) {
      if (i > 0) sb.p(' ');
      sb.p(idlist.getId(i));
    }
    if (argsId != null) {
      if (numIds > 0) sb.p(' ');
      sb.p('*').p(argsId);
    }
    sb.p('`');
    return sb.toString();
  }

}
