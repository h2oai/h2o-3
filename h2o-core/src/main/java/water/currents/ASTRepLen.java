package water.currents;

import water.Futures;
import water.MRTask;
import water.fvec.*;


class ASTRepLen extends ASTPrim {
  @Override int nargs() { return 1+2; } // (rep_len x length)
  @Override String str() { return "rep_len"; }
  @Override ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame f;
    if( asts[1] instanceof ASTFrame ) f = stk.track(asts[1].exec(env)).getFrame();
    else return new ValFrame(new Frame(Vec.makeCon(asts[1].exec(env).getNum(), (long)asts[2].exec(env).getNum())));
    long length = (long) asts[2].exec(env).getNum();

    final Frame fr = f;
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

// Same logic as R's generic seq method
class ASTSeq extends ASTPrim {
  @Override int nargs() { return 1+3; } // (seq from to by)
  @Override String str() { return "seq"; }

  @Override Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    double from = asts[1].exec(env).getNum();
    double to   = asts[2].exec(env).getNum();
    double by   = asts[3].exec(env).getNum();
    double delta = to - from;
    if(delta == 0 && to == 0) throw new IllegalArgumentException("Expected `to` and `from` to have nonzero difference.");
    else {
      double n = delta/by;
      if(n < 0)                     throw new IllegalArgumentException("wrong sign in 'by' argument");
      else if(n > Double.MAX_VALUE) throw new IllegalArgumentException("'by' argument is much too small");
      Futures fs = new Futures();
      AppendableVec av = new AppendableVec(Vec.newKey());
      NewChunk nc = new NewChunk(av, 0);
      int len = (int)n + 1;
      for (int r = 0; r < len; r++) nc.addNum(from + r*by);
      // May need to adjust values = by > 0 ? min(values, to) : max(values, to)
      nc.close(0, fs);
      Vec vec = av.close(fs);
      fs.blockForPending();
      return new ValFrame(new Frame(vec));
    }
  }
}

class ASTSeqLen extends ASTPrim {
  @Override int nargs() { return 1+1; } // (seq_len n)
  @Override String str() { return "seq_len"; }
  @Override Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    int len = (int) Math.ceil(asts[1].exec(env).getNum());
    if (len <= 0) throw new IllegalArgumentException("Error in seq_len("+len+"): argument must be coercible to positive integer");
    return new ValFrame(new Frame(Vec.makeSeq(len,true)));
  }
}