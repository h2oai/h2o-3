package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstExec;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstId;

/**
 * Convert year, month, day, hour, minute, sec, msec to Unix epoch time
 */
@Deprecated  // Use {@link AstMoment} instead
public class AstMktime extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"yr", "mo", "dy", "hr", "mi", "se", "ms"};
  }

  /**
   * (mktime yr mo dy hr mi se ms)
   */
  @Override
  public int nargs() {
    return 1 + 7;
  }

  @Override
  public String str() {
    return "mktime";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    // Seven args, all required.  See if any are arrays.
    Frame fs[] = new Frame[nargs() - 1];
    int is[] = new int[nargs() - 1];
    Frame x = null;             // Sample frame (for auto-expanding constants)
    for (int i = 1; i < nargs(); i++)
      if (asts[i] instanceof AstId || asts[i] instanceof AstExec)
        fs[i - 1] = x = stk.track(asts[i].exec(env)).getFrame();
      else is[i - 1] = (int) asts[i].exec(env).getNum();

    if (x == null) {                            // Single point
      long msec = new MutableDateTime(
          is[0],   // year
          is[1] + 1, // month
          is[2] + 1, // day
          is[3],   // hour
          is[4],   // minute
          is[5],   // second
          is[6])   // msec
          .getMillis();
      return new ValNum(msec);
    }

    // Make constant Vecs for the constant args.  Commonly, they'll all be zero
    Vec vecs[] = new Vec[7];
    for (int i = 0; i < 7; i++) {
      if (fs[i] == null) {
        vecs[i] = x.anyVec().makeCon(is[i]);
      } else {
        if (fs[i].numCols() != 1) throw new IllegalArgumentException("Expect single column");
        vecs[i] = fs[i].anyVec();
      }
    }

    // Convert whole column to epoch msec
    Frame fr2 = new MRTask() {
      @Override
      public void map(Chunk chks[], NewChunk nchks[]) {
        MutableDateTime dt = new MutableDateTime(0);
        NewChunk n = nchks[0];
        int rlen = chks[0]._len;
        for (int r = 0; r < rlen; r++) {
          dt.setDateTime(
              (int) chks[0].at8(r),  // year
              (int) chks[1].at8(r) + 1,// month
              (int) chks[2].at8(r) + 1,// day
              (int) chks[3].at8(r),  // hour
              (int) chks[4].at8(r),  // minute
              (int) chks[5].at8(r),  // second
              (int) chks[6].at8(r)); // msec
          n.addNum(dt.getMillis());
        }
      }
    }.doAll(new byte[]{Vec.T_NUM}, vecs).outputFrame(new String[]{"msec"}, null);
    // Clean up the constants
    for (int i = 0; i < nargs() - 1; i++)
      if (fs[i] == null)
        vecs[i].remove();
    return new ValFrame(fr2);
  }
}
