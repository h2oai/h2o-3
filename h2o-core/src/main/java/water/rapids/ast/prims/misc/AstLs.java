package water.rapids.ast.prims.misc;

import water.Futures;
import water.Key;
import water.KeySnapshot;
import water.fvec.AppendableVec;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import java.util.ArrayList;

/**
 * R 'ls' command.
 * <p/>
 * This method is purely for the console right now.  Print stuff into the string buffer.
 * JSON response is not configured at all.
 */
public class AstLs extends AstPrimitive {
  @Override
  public String[] args() {
    return null;
  }

  @Override
  public int nargs() {
    return 1;
  }

  @Override
  public String str() {
    return "ls";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    ArrayList<String> domain = new ArrayList<>();
    Futures fs = new Futures();
    AppendableVec av = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec(), Vec.T_CAT);
    NewChunk keys = new NewChunk(av, 0);
    int r = 0;
    for (Key key : KeySnapshot.globalSnapshot().keys()) {
      keys.addCategorical(r++);
      domain.add(key.toString());
    }
    String[] key_domain = domain.toArray(new String[domain.size()]);
    av.setDomain(key_domain);
    keys.close(fs);
    Vec c0 = av.layout_and_close(fs);   // c0 is the row index vec
    fs.blockForPending();
    return new ValFrame(new Frame(Key.<Frame>make("h2o_ls"), new String[]{"key"}, new Vec[]{c0}));
  }
}

