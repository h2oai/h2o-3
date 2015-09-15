package water.currents;

import org.apache.commons.lang.StringUtils;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

public class ASTStrOp { /*empty*/}

class ASTStrSplit extends ASTPrim {
  @Override int nargs() { return 1+2; } // (strsplit x split)
  @Override String str() { return "strsplit"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String split = asts[2].exec(env).getStr();
    if (fr.numCols() != 1) throw new IllegalArgumentException("strsplit requires a single column.");
    final String[]   old_domains = fr.anyVec().domain();
    final String[][] new_domains = newDomains(old_domains, split);

    final String regex = split;
    Frame fr2 = new MRTask() {
      @Override public void map(Chunk[] cs, NewChunk[] ncs) {
        Chunk c = cs[0];
        for (int i = 0; i < c._len; ++i) {
          int cnt = 0;
          if( !c.isNA(i) ) {
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
    }.doAll(new_domains.length, fr).outputFrame(null,null,new_domains);
    return new ValFrame(fr2);
  }

  // each domain level may split in its own uniq way.
  // hold onto a hashset of domain levels for each "new" column
  private String[][] newDomains(String[] domains, String regex) {
    ArrayList<HashSet<String>> strs = new ArrayList<>();

    // loop over each level in the domain
    HashSet<String> x;
    for (String domain : domains) {
      String[] news = domain.split(regex);
      for( int i = 0; i < news.length; ++i ) {

        // we have a "new" column, must add a new HashSet to the array
        // list and start tracking levels for this "i"
        if( strs.size() == i ) {
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
    int i=0;
    for (HashSet<String> h: strs)
      doms[i++] = h.toArray(new String[h.size()]);
    return doms;
  }
}

class ASTCountMatches extends ASTPrim {

  @Override int nargs() { return 1+2; } // (countmatches x pattern)
  @Override String str() { return "countmatches"; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String[] pattern;
    if( asts[2] instanceof ASTStrList ) pattern = ((ASTStrList)asts[2])._strs;
    else                                pattern = new String[]{asts[2].exec(env).getStr()};

    if (fr.numCols() != 1) throw new IllegalArgumentException("countmatches requires a single column.");
    final int[] matchCounts = countMatches(fr.anyVec().domain(),pattern);

    Frame fr2 = new MRTask() {
      @Override public void map(Chunk[] cs, NewChunk[] ncs) {
        Chunk c = cs[0];
        for (int i = 0; i < c._len; ++i) {
          if( !c.isNA(i) ) {
            int idx = (int) c.at8(i);
            ncs[0].addNum(matchCounts[idx]);
          } else ncs[i].addNA();
        }
      }
    }.doAll(1, fr).outputFrame();
    return new ValFrame(fr2);
  }

  int[] countMatches(String[] domain, String[] pattern) {
    int[] res = new int[domain.length];
    for (int i=0; i < domain.length; i++)
      for (String aPattern : pattern)
        res[i] += StringUtils.countMatches(domain[i], aPattern);
    return res;
  }
}

// mutating call
class ASTToLower extends ASTPrim {
  @Override int nargs() { return 1+1; } //(tolower x)
  @Override String str() { return "tolower"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1) throw new IllegalArgumentException("tolower only takes a single column of data. Got "+ fr.numCols()+" columns.");
    Vec vec = fr.anyVec();   assert vec != null;
    if( !vec.isEnum() ) throw new IllegalArgumentException("expected categorical column.");
    String[] dom = vec.domain();

    for (int i = 0; i < dom.length; ++i)
      dom[i] = dom[i].toLowerCase(Locale.ENGLISH);

    // COW
    Vec v = vec.makeCopy(dom);
    return new ValFrame(new Frame(v));
  }
}

class ASTToUpper extends ASTPrim {
  @Override int nargs() { return 1+1; } //(toupper x)
  @Override String str() { return "toupper"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1) throw new IllegalArgumentException("toupper only takes a single column of data. Got "+ fr.numCols()+" columns.");
    Vec vec = fr.anyVec();   assert vec != null;
    if( !vec.isEnum() ) throw new IllegalArgumentException("expected categorical column.");
    String[] dom = vec.domain();

    for (int i = 0; i < dom.length; ++i)
      dom[i] = dom[i].toUpperCase(Locale.ENGLISH);

    // COW
    Vec v = vec.makeCopy(dom);
    return new ValFrame(new Frame(v));
  }
}

class ASTStrSub extends ASTPrim {
  @Override int nargs() { return 1+4; } // (sub pattern replacement x ignore.case)
  @Override String str() { return "sub"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    String pattern     = asts[1].exec(env).getStr();
    String replacement = asts[2].exec(env).getStr();
    Frame fr = stk.track(asts[3].exec(env)).getFrame();
    boolean ignoreCase = asts[4].exec(env).getNum()==1;

    if (fr.numCols() != 1) throw new IllegalArgumentException("sub works on a single column at a time.");
    Vec vec = fr.anyVec();   assert vec != null;
    String[] doms = vec.domain();
    for (int i = 0; i < doms.length; ++i)
      doms[i] = ignoreCase
              ? doms[i].toLowerCase(Locale.ENGLISH).replaceFirst(pattern, replacement)
              : doms[i].replaceFirst(pattern, replacement);

    // COW
    Vec v = vec.makeCopy(doms);
    return new ValFrame(new Frame(v));
  }
}

class ASTGSub extends ASTPrim {
  @Override int nargs() { return 1+4; } // (sub pattern replacement x ignore.case)
  @Override String str() { return "gsub"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    String pattern     = asts[1].exec(env).getStr();
    String replacement = asts[2].exec(env).getStr();
    Frame fr = stk.track(asts[3].exec(env)).getFrame();
    boolean ignoreCase = asts[4].exec(env).getNum()==1;

    if (fr.numCols() != 1) throw new IllegalArgumentException("sub works on a single column at a time.");
    Vec vec = fr.anyVec();   assert vec != null;
    String[] doms = vec.domain();
    for (int i = 0; i < doms.length; ++i)
      doms[i] = ignoreCase
              ? doms[i].toLowerCase(Locale.ENGLISH).replaceAll(pattern, replacement)
              : doms[i].replaceAll(pattern, replacement);

    // COW
    Vec v = vec.makeCopy(doms);
    return new ValFrame(new Frame(v));
  }
}

class ASTTrim extends ASTPrim {
  @Override int nargs() { return 1+1; } // (trim x)
  @Override String str() { return "trim"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1) throw new IllegalArgumentException("trim works on a single column at a time.");
    Vec vec = fr.anyVec();   assert vec != null;
    if( !vec.isEnum() ) throw new IllegalArgumentException("column must be character.");
    String[] doms = vec.domain();
    for (int i = 0; i < doms.length; ++i) doms[i] = doms[i].trim();

    // COW
    Vec v = vec.makeCopy(doms);
    return new ValFrame(new Frame(v));
  }
}