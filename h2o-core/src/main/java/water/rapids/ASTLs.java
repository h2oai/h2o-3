package water.rapids;

import water.Futures;
import water.Key;
import water.KeySnapshot;
import water.fvec.*;

import java.util.ArrayList;

/**
* R 'ls' command.
*
* This method is purely for the console right now.  Print stuff into the string buffer.
* JSON response is not configured at all.
*/
class ASTLs extends ASTPrim {
  @Override
  public String[] args() { return null; }
  @Override int nargs() { return 1; }
  @Override public String str() { return "ls" ; }
  @Override public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    ArrayList<String> domain = new ArrayList<>();
    Futures fs = new Futures();
    AppendableVec av = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec(),Vec.T_CAT);
    NewChunk keys = new NewChunk(new SingleChunk(av,0),0);
    int r = 0;
    for( Key key : KeySnapshot.globalSnapshot().keys()) {
      keys.addCategorical(r++);
      domain.add(key.toString());
    }
    String[] key_domain = domain.toArray(new String[domain.size()]);
    keys.close(fs);
    VecAry c0 = av.layout_and_close(fs, key_domain);   // c0 is the row index vec
    fs.blockForPending();
    return new ValFrame(new Frame(Key.make("h2o_ls"), new String[]{"key"}, c0));
  }
}

