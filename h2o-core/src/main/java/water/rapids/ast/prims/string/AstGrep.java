package water.rapids.ast.prims.string;

import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AstGrep extends AstPrimitive {

  @Override
  public String[] args() {
    return new String[]{"ary", "regex", "ignore_case", "invert", "output_logical"};
  }

  @Override
  public int nargs() {
    return 1 + 5;
  } // (grep x regex ignore_case invert output_logical)

  @Override
  public String str() {
    return "grep";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String regex = asts[2].exec(env).getStr();
    boolean ignoreCase = asts[3].exec(env).getNum() == 1;
    boolean invert = asts[4].exec(env).getNum() == 1;
    boolean outputLogical = asts[5].exec(env).getNum() == 1;
    GrepHelper grepHelper = new GrepHelper(regex, ignoreCase, invert, outputLogical);

    if ((fr.numCols() != 1) || ! (fr.anyVec().isCategorical() || fr.anyVec().isString()))
      throw new IllegalArgumentException("can only grep on a single categorical/string column.");

    Vec v = fr.anyVec();
    assert v != null;

    Frame result;
    if (v.isCategorical()) {
      int[] filtered = grepDomain(grepHelper, v);
      Arrays.sort(filtered);
      result = new GrepCatTask(grepHelper, filtered).doAll(Vec.T_NUM, v).outputFrame();
    } else {
      result = new GrepStrTask(grepHelper).doAll(Vec.T_NUM, v).outputFrame();
    }

    return new ValFrame(result);
  }

  private static int[] grepDomain(GrepHelper grepHelper, Vec v) {
    Pattern p = grepHelper.compilePattern();
    String[] domain = v.domain();
    int cnt = 0;
    int[] filtered = new int[domain.length];
    for (int i = 0; i < domain.length; i++) {
      if (p.matcher(domain[i]).find())
        filtered[cnt++] = i;
    }
    int[] result = new int[cnt];
    System.arraycopy(filtered, 0, result, 0, cnt);
    return result;
  }

  private static class GrepCatTask extends MRTask<GrepCatTask> {
    private final int[] _matchingCats;
    private final GrepHelper _gh;

    GrepCatTask(GrepHelper gh, int[] matchingCats) {
      _matchingCats = matchingCats;
      _gh = gh;
    }

    @Override
    public void map(Chunk c, NewChunk n) {
      OutputWriter w = OutputWriter.makeWriter(_gh, n, c.start());
      int rows = c._len;
      for (int r = 0; r < rows; r++) {
        if (c.isNA(r)) {
          w.addNA(r);
        } else {
          int cat = (int) c.at8(r);
          int pos = Arrays.binarySearch(_matchingCats, cat);
          w.addRow(r, pos >= 0);
        }
      }
    }
  }

  private static class GrepStrTask extends MRTask<GrepStrTask> {
    private final GrepHelper _gh;

    GrepStrTask(GrepHelper gh) {
      _gh = gh;
    }

    @Override
    public void map(Chunk c, NewChunk n) {
      OutputWriter w = OutputWriter.makeWriter(_gh, n, c.start());
      Pattern p = _gh.compilePattern();
      Matcher m = p.matcher("dummy");
      BufferedString bs = new BufferedString();
      int rows = c._len;
      for (int r = 0; r < rows; r++) {
        if (c.isNA(r)) {
          w.addNA(r);
        } else {
          m.reset(c.atStr(bs, r).toString());
          w.addRow(r, m.find());
        }
      }
    }
  }

  private static class GrepHelper extends Iced<GrepHelper> {
    private String _regex;
    private boolean _ignoreCase;
    private boolean _invert;
    private boolean _outputLogical;

    public GrepHelper() {}

    GrepHelper(String regex, boolean ignoreCase, boolean invert, boolean outputLogical) {
      _regex = regex;
      _ignoreCase = ignoreCase;
      _invert = invert;
      _outputLogical = outputLogical;
    }

    Pattern compilePattern() {
      int flags = _ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
      return Pattern.compile(_regex, flags);
    }

  }

  private static abstract class OutputWriter {
    static final double MATCH = 1;
    static final double NO_MATCH = 0;

    NewChunk _nc;
    long _start;
    boolean _invert;

    OutputWriter(NewChunk nc, long start, boolean invert) {
      _nc = nc;
      _start = start;
      _invert = invert;
    }

    abstract void addNA(int row);
    abstract void addRow(int row, boolean matched);

    static OutputWriter makeWriter(GrepHelper gh, NewChunk nc, long start) {
      return gh._outputLogical ? new IndicatorWriter(nc, start, gh._invert) : new PositionWriter(nc, start, gh._invert);
    }

  }

  private static class IndicatorWriter extends OutputWriter {
    IndicatorWriter(NewChunk nc, long start, boolean invert) {
      super(nc, start, invert);
    }

    @Override
    void addNA(int row) {
      _nc.addNum(_invert ? MATCH : NO_MATCH);
    }

    @Override
    void addRow(int row, boolean matched) {
      _nc.addNum(matched != _invert ? MATCH : NO_MATCH);
    }
  }

  private static class PositionWriter extends OutputWriter {
    PositionWriter(NewChunk nc, long start, boolean invert) {
      super(nc, start, invert);
    }

    @Override
    void addNA(int row) {
      if (_invert)
        _nc.addNum(_start + row);
    }

    @Override
    void addRow(int row, boolean matched) {
      if (matched != _invert)
        _nc.addNum(_start + row);
    }
  }

}