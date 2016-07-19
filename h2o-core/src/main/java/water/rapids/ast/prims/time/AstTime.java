package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.ParseTime;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Basic time accessors; extract hours/days/years/etc from H2O's internal
 * msec-since-Unix-epoch time
 */
public abstract class AstTime extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"time"};
  }

  // (op time)
  @Override
  public int nargs() {
    return 1 + 1;
  }

  // Override for e.g. month and day-of-week
  protected String[][] factors() {
    return null;
  }

  public abstract long op(MutableDateTime dt);

  private double op(MutableDateTime dt, double d) {
    dt.setMillis((long) d);
    return op(dt);
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val val = asts[1].exec(env);
    switch (val.type()) {
      case Val.NUM:
        double d = val.getNum();
        return new ValNum(Double.isNaN(d) ? d : op(new MutableDateTime(0), d));
      case Val.FRM:
        Frame fr = stk.track(val).getFrame();
        if (fr.numCols() > 1) throw water.H2O.unimpl();
        return new ValFrame(new MRTask() {
          @Override
          public void map(Chunk chk, NewChunk cres) {
            MutableDateTime mdt = new MutableDateTime(0, ParseTime.getTimezone());
            for (int i = 0; i < chk._len; i++)
              cres.addNum(chk.isNA(i) ? Double.NaN : op(mdt, chk.at8(i)));
          }
        }.doAll(1, Vec.T_NUM, fr).outputFrame(fr._names, factors()));
      default:
        throw water.H2O.fail();
    }
  }
}

