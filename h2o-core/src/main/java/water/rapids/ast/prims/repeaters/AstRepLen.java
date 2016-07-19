package water.rapids.ast.prims.repeaters;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 */
public class AstRepLen extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "length"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (rep_len x length)

  @Override
  public String str() {
    return "rep_len";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val v = asts[1].exec(env);
    long length = (long) asts[2].exec(env).getNum();
    Frame ff;
    if (v instanceof ValFrame) ff = stk.track(v).getFrame();
    else return new ValFrame(new Frame(Vec.makeCon(v.getNum(), length)));

    final Frame fr = ff;
    if (fr.numCols() == 1) {
      Vec vec = Vec.makeRepSeq(length, fr.numRows());
      new MRTask() {
        @Override
        public void map(Chunk c) {
          for (int i = 0; i < c._len; ++i)
            c.set(i, fr.anyVec().at((long) c.atd(i)));
        }
      }.doAll(vec);
      vec.setDomain(fr.anyVec().domain());
      return new ValFrame(new Frame(vec));
    } else {
      Frame f = new Frame();
      for (int i = 0; i < length; ++i)
        f.add(Frame.defaultColName(f.numCols()), fr.vec(i % fr.numCols()));
      return new ValFrame(f);
    }
  }
}
