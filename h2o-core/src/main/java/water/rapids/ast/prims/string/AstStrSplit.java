package water.rapids.ast.prims.string;

import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.rapids.Env;
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
    for (VecAry v : fr.vecs().singleVecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry vs = new VecAry();
    for (VecAry v : fr.vecs().singleVecs()) {
      if (v.isCategorical()) {
        vs.append(strSplitCategoricalCol(v, splitRegEx));
      } else {
        vs.append(strSplitStringCol(v, splitRegEx));
      }
    }
    return new ValFrame(new Frame(vs));
  }

  private VecAry strSplitCategoricalCol(VecAry vec, String splitRegEx) {
    final String[] old_domains = vec.domain();
    final String[][] new_domains = newDomains(old_domains, splitRegEx);

    final String regex = splitRegEx;
    return new MRTask() {
      @Override
      public void map(ChunkAry cs, NewChunkAry ncs) {
        for (int i = 0; i < cs._len; ++i) {
          int cnt = 0;
          if (!cs.isNA(i)) {
            int idx = cs.at4(i);
            String s = old_domains[idx];
            String[] ss = s.split(regex);
            for (String s1 : ss) {
              int n_idx = Arrays.asList(new_domains[cnt]).indexOf(s1);
              if (n_idx == -1) ncs.addNA(cnt++);
              else ncs.addInteger(cnt++,n_idx);
            }
          }
          if (cnt < ncs._numCols)
            for (; cnt < ncs._numCols; ++cnt) ncs.addNA(cnt);
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

  private VecAry strSplitStringCol(VecAry vec, final String splitRegEx) {
    final int newColCnt = (new AstStrSplit.CountSplits(splitRegEx)).doAll(vec)._maxSplits;
    return new MRTask() {
      @Override
      public void map(ChunkAry cs, NewChunkAry ncs) {
        BufferedString tmpStr = new BufferedString();
        for (int row = 0; row < cs._len; ++row) {
          int col = 0;
          if (!cs.isNA(row)) {
            String[] ss = cs.atStr(tmpStr, row).toString().split(splitRegEx);
            for (String s : ss) // distribute strings among new cols
              ncs.addStr(col++,s);
          }
          if (col < ncs._numCols) // fill remaining cols w/ NA
            for (; col < ncs._numCols; col++) ncs.addNA(col);
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
    public void map(ChunkAry chk) {
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
