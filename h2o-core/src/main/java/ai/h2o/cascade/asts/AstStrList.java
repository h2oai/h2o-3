package ai.h2o.cascade.asts;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * A list of Strings.
 */
public class AstStrList extends Ast<AstStrList> {
  public String[] strings;

  public AstStrList(ArrayList<String> strs) {
    strings = strs.toArray(new String[strs.size()]);
  }

  @Override
  public String str() {
    return Arrays.toString(strings);
  }

}
