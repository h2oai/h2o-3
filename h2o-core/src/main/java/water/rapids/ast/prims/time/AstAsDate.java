package water.rapids.ast.prims.time;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.parser.ParseTime;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Convert a String to a Time (msec since Unix Epoch) via a given parse format
 */
public class AstAsDate extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"time", "format"};
  }

  // (as.Date time format)
  @Override
  public int nargs() {
    return 1 + 2;
  }

  @Override
  public String str() {
    return "as.Date";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec vec = fr.vecs()[0];
    if (fr.vecs().length != 1 || !(vec.isCategorical() || vec.isString()))
      throw new IllegalArgumentException("as.Date requires a single column of factors or strings");

    final String format = asts[2].exec(env).getStr();
    if (format.isEmpty()) throw new IllegalArgumentException("as.Date requires a non-empty format string");
    // check the format string more?

    final String[] dom = vec.domain();
    final boolean isStr = dom == null && vec.isString();
    assert isStr || dom != null : "as.Date error: domain is null, but vec is not String";

    Frame fr2 = new MRTask() {
      private transient DateTimeFormatter _fmt;

      @Override
      public void setupLocal() {
        _fmt = ParseTime.forStrptimePattern(format).withZone(ParseTime.getTimezone());
      }

      @Override
      public void map(Chunk c, NewChunk nc) {
        //done on each node in lieu of rewriting DateTimeFormatter as Iced
        String date;
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < c._len; ++i) {
          if (!c.isNA(i)) {
            if (isStr) date = c.atStr(tmpStr, i).toString();
            else date = dom[(int) c.at8(i)];
            nc.addNum(DateTime.parse(date, _fmt).getMillis(), 0);
          } else nc.addNA();
        }
      }
    }.doAll(1, Vec.T_NUM, fr).outputFrame(fr._names, null);
    return new ValFrame(fr2);
  }
}
