package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/**
 */
public class AstStrSplit extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "split"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (strsplit x split)

  @Override
  public String str() {
    return "strsplit";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String splitRegEx = asts[2].exec(env).getStr();

    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    ArrayList<Vec> vs = new ArrayList<>(fr.numCols());
    for (Vec v : fr.vecs()) {
      Vec[] splits;
      if (v.isCategorical()) {
        splits = strSplitCategoricalCol(v, splitRegEx);
        for (Vec split : splits) vs.add(split);
      } else {
        splits = strSplitStringCol(v, splitRegEx);
        for (Vec split : splits) vs.add(split);
      }
    }

    return new ValFrame(new Frame(vs.toArray(new Vec[vs.size()])));
  }

  private Vec[] strSplitCategoricalCol(Vec vec, String splitRegEx) {
    final String[] old_domains = vec.domain();
    final String[][] new_domains = newDomains(old_domains, splitRegEx);

    final String regex = splitRegEx;
    return new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        Chunk c = cs[0];
        for (int i = 0; i < c._len; ++i) {
          int cnt = 0;
          if (!c.isNA(i)) {
            int idx = (int) c.at8(i);
            String s = old_domains[idx];
            String[] ss = s.split(regex);
            for (String s1 : ss) {
              int n_idx = Arrays.asList(new_domains[cnt]).indexOf(s1);
              if (n_idx == -1) ncs[cnt++].addNA();
              else ncs[cnt++].addNum(n_idx);
            }
          }
          if (cnt < ncs.length)
            for (; cnt < ncs.length; ++cnt) ncs[cnt].addNA();
        }
      }
    }.doAll(new_domains.length, Vec.T_CAT, new Frame(vec)).outputFrame(null, null, new_domains).vecs();
  }

  // each domain level may split in its own uniq way.
  // hold onto a hashset of domain levels for each "new" column
  private String[][] newDomains(String[] domains, String regex) {
    ArrayList<HashSet<String>> strs = new ArrayList<>();

    // loop over each level in the domain
    HashSet<String> x;
    for (String domain : domains) {
      String[] news = domain.split(regex);
      for (int i = 0; i < news.length; ++i) {

        // we have a "new" column, must add a new HashSet to the array
        // list and start tracking levels for this "i"
        if (strs.size() == i) {
          x = new HashSet<>();
          x.add(news[i]);
          strs.add(x);
        } else {
          // ok not a new column
          // whip out the current set of levels and add the new one
          strs.get(i).add(news[i]);
        }
      }
    }
    return listToArray(strs);
  }

  private String[][] listToArray(ArrayList<HashSet<String>> strs) {
    String[][] doms = new String[strs.size()][];
    int i = 0;
    for (HashSet<String> h : strs)
      doms[i++] = h.toArray(new String[h.size()]);
    return doms;
  }

  private Vec[] strSplitStringCol(Vec vec, final String splitRegEx) {
    final int newColCnt = (new AstStrSplit.CountSplits(splitRegEx)).doAll(vec)._maxSplits;
    return new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        Chunk chk = cs[0];
        if (chk instanceof C0DChunk) // all NAs
          for (int row = 0; row < chk.len(); row++)
            for (int col = 0; col < ncs.length; col++)
              ncs[col].addNA();
        else {
          BufferedString tmpStr = new BufferedString();
          for (int row = 0; row < chk._len; ++row) {
            int col = 0;
            if (!chk.isNA(row)) {
              String[] ss = chk.atStr(tmpStr, row).toString().split(splitRegEx);
              for (String s : ss) // distribute strings among new cols
                ncs[col++].addStr(s);
            }
            if (col < ncs.length) // fill remaining cols w/ NA
              for (; col < ncs.length; col++) ncs[col].addNA();
          }
        }
      }
    }.doAll(newColCnt, Vec.T_STR, new Frame(vec)).outputFrame().vecs();
  }

  /**
   * Run through column to figure out the maximum split that
   * any string in the column will need.
   */
  private static class CountSplits extends MRTask<AstStrSplit.CountSplits> {
    // IN
    private final String _regex;
    // OUT
    int _maxSplits = 0;

    CountSplits(String regex) {
      _regex = regex;
    }

    @Override
    public void map(Chunk chk) {
      BufferedString tmpStr = new BufferedString();
      for (int row = 0; row < chk._len; row++) {
        if (!chk.isNA(row)) {
          int split = chk.atStr(tmpStr, row).toString().split(_regex).length;
          if (split > _maxSplits) _maxSplits = split;
        }
      }
    }

    @Override
    public void reduce(AstStrSplit.CountSplits that) {
      if (this._maxSplits < that._maxSplits) this._maxSplits = that._maxSplits;
    }
  }
}
