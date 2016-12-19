package ai.h2o.cascade.asts;

import ai.h2o.cascade.core.IdList;
import ai.h2o.cascade.vals.ValIdList;
import water.util.SB;

import java.util.ArrayList;


/**
 * List of unevaluated identifiers.
 */
public class AstIdList extends Ast<AstIdList> {
  private IdList idList;


  public AstIdList(ArrayList<String> names, String argsName) {
    idList = new IdList(names.toArray(new String[names.size()]), argsName);
  }

  public AstIdList(IdList v) {
    idList = v;
  }

  @Override
  public ValIdList exec() {
    return new ValIdList(idList);
  }

  @Override
  public String str() {
    int numIds = idList.numIds();
    String argsId = idList.getVarargId();
    SB sb = new SB("`");
    for (int i = 0; i < numIds; i++) {
      if (i > 0) sb.p(' ');
      sb.p(idList.getId(i));
    }
    if (argsId != null) {
      if (numIds > 0) sb.p(' ');
      sb.p('*').p(argsId);
    }
    sb.p('`');
    return sb.toString();
  }

}
