package water.rapids.ast.prims.time;

import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.ParseTime;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 */
public class AstListTimeZones extends AstPrimitive {
  @Override
  public String[] args() {
    return null;
  }

  /* (listTimeZones) */
  @Override
  public int nargs() {
    return 1;
  }

  @Override
  public String str() {
    return "listTimeZones";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    String[] domain = ParseTime.listTimezones().split("\n");
    double ds[] = new double[domain.length];
    for (int i = 0; i < domain.length; i++) ds[i] = i;
    Vec vec = Vec.makeVec(ds, Vec.VectorGroup.VG_LEN1.addVec());
    vec.setDomain(domain);
    return new ValFrame(new Frame(new String[]{"Timezones"}, new Vec[]{vec}));
  }
}
