package water.rapids;

import org.apache.commons.lang.StringUtils;

import water.DKV;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

import java.util.*;
public class ASTStringOps {
  //merp
}

class ASTStrSplit extends ASTUniPrefixOp {
  String _split;
  ASTStrSplit() { super(new String[]{"strsplit", "x", "split"}); }
  @Override String opStr() { return "strsplit"; }
  @Override ASTOp make() { return new ASTStrSplit(); }
  ASTStrSplit parse_impl(Exec E) {
    AST ary = E.parse();
    _split = E.nextStr();
    E.eatEnd();
    ASTStrSplit res = (ASTStrSplit) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    if (fr.numCols() != 1) throw new IllegalArgumentException("strsplit requires a single column.");
    final String[]   old_domains = fr.anyVec().domain();
    final String[][] new_domains = newDomains(old_domains, _split);

    final String regex = _split;
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
    env.pushAry(fr2);
  }

  // each domain level may split in its own uniq way.
  // hold onto a hashset of domain levels for each "new" column
  private String[][] newDomains(String[] domains, String regex) {
    ArrayList<HashSet<String>> strs = new ArrayList<>();

    // loop over each level in the domain
    HashSet<String> x; // used all over
    for (String domain : domains) {
      String[] news = domain.split(regex);  // split the domain on the regex
      for( int i = 0; i < news.length; ++i ) {

        // we have a "new" column, must add a new HashSet to the array list and start tracking levels for this "i"
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

    // now loop over and create the String[][] result
    String[][] doms = new String[strs.size()][];
    for (int i = 0; i < strs.size(); ++i) {
      x = strs.get(i);
      doms[i] = new String[x.size()];
      for (int j = 0; j < x.size(); ++j)
        doms[i][j] = (String)x.toArray()[j];
    }
    return doms;
  }
}

class ASTCountMatches extends ASTUniPrefixOp {
  String _subStr;
  ASTCountMatches() { super(new String[]{"countmatches", "x", "substr"}); }
  @Override String opStr() { return "countmatches"; }
  @Override ASTOp make() { return  new ASTCountMatches(); }
  ASTCountMatches parse_impl(Exec E) {
    AST ary = E.parse();
    _subStr = E.nextStr();
    E.eatEnd();
    ASTCountMatches res = (ASTCountMatches) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    if (fr.numCols() != 1) throw new IllegalArgumentException("countmatches requires a single column.");
    if( !fr.anyVec().isEnum() ) throw new IllegalArgumentException("countmatches column must be categorical. Got: " + fr.anyVec().get_type_str());
    final int[] matchCounts = countMatches(fr.anyVec().domain());

    Frame fr2 = new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        for (int i = 0; i < c._len; ++i)
          if( c.isNA(i) ) nc.addNA();
          else            nc.addNum(matchCounts[ (int) c.at8(i)],0);
      }
    }.doAll(1, fr).outputFrame();
    env.pushAry(fr2);
  }

  int[] countMatches(String[] domain) {
    int[] res = new int[domain.length];
    for (int i=0; i < domain.length; i++) {
      res[i] = StringUtils.countMatches(domain[i],_subStr);
    }
    return res;
  }
}

// mutating call
class ASTToLower extends ASTUniPrefixOp {
  @Override String opStr() { return "tolower"; }
  @Override ASTOp make() { return new ASTToLower(); }
  @Override void apply(Env env) {
    if( !env.isAry() ) { throw new IllegalArgumentException("tolower only operates on a single vector!"); }
    Frame fr = env.popAry();
    if (fr.numCols() != 1) throw new IllegalArgumentException("tolower only takes a single column of data. Got "+ fr.numCols()+" columns.");
    String[] dom = fr.anyVec().domain();
    for (int i = 0; i < dom.length; ++i)
      dom[i] = dom[i].toLowerCase(Locale.ENGLISH);
    fr.anyVec().setDomain(dom);
    if( fr._key!=null && DKV.getGet(fr._key)!=null) DKV.put(fr._key, fr);
    env.pushAry(fr);
  }
}

class ASTToUpper extends ASTUniPrefixOp {
  @Override String opStr() { return "toupper"; }
  @Override ASTOp make() { return new ASTToUpper(); }
  @Override void apply(Env env) {
    if( !env.isAry() ) { throw new IllegalArgumentException("toupper only operates on a single vector!"); }
    Frame fr = env.popAry();
    if (fr.numCols() != 1) throw new IllegalArgumentException("toupper only takes a single column of data. Got "+ fr.numCols()+" columns.");
    String[] dom = fr.anyVec().domain();
    for (int i = 0; i < dom.length; ++i)
      dom[i] = dom[i].toUpperCase(Locale.ENGLISH);
    fr.anyVec().setDomain(dom);
    if( fr._key!=null && DKV.getGet(fr._key)!=null) DKV.put(fr._key, fr);
    env.pushAry(fr);
  }
}

class ASTStrSub extends ASTUniPrefixOp {
  String _pattern;
  String _replacement;
  boolean _ignoreCase;
  ASTStrSub() { super(new String[]{"sub", "pattern", "replacement", "x", "ignore.case"}); }
  @Override String opStr() { return "sub"; }
  @Override ASTOp make() { return new ASTStrSub(); }
  ASTStrSub parse_impl(Exec E) {
    _pattern = E.nextStr();
    _replacement = E.nextStr();
    AST ary = E.parse();
    AST a = E.parse();
    if( a instanceof ASTId ) _ignoreCase = ((ASTNum)E._env.lookup((ASTId)a))._d==1;
    E.eatEnd();
    ASTStrSub res = (ASTStrSub) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    if (fr.numCols() != 1) throw new IllegalArgumentException("sub works on a single column at a time.");
    final String replacement = _replacement;
    final String pattern = _pattern;
    String[] doms = fr.anyVec().domain();
    for (int i = 0; i < doms.length; ++i)
      doms[i] = _ignoreCase
              ? doms[i].toLowerCase(Locale.ENGLISH).replaceFirst(pattern, replacement)
              : doms[i].replaceFirst(pattern, replacement);

    fr.anyVec().setDomain(doms);
    if( fr._key!=null && DKV.getGet(fr._key)!=null) DKV.put(fr._key, fr);
    env.pushAry(fr);
  }
}

class ASTGSub extends ASTStrSub {
  ASTGSub() { super(); }
  @Override String opStr() { return "gsub"; }
  @Override ASTOp make() { return new ASTGSub(); }
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    if (fr.numCols() != 1) throw new IllegalArgumentException("sub works on a single column at a time.");
    final String replacement = _replacement;
    final String pattern = _pattern;
    String[] doms = fr.anyVec().domain();
    for (int i = 0; i < doms.length; ++i)
      doms[i] = _ignoreCase
              ? doms[i].toLowerCase(Locale.ENGLISH).replaceAll(pattern, replacement)
              : doms[i].replaceAll(pattern, replacement);

    fr.anyVec().setDomain(doms);
    if( fr._key!=null && DKV.getGet(fr._key)!=null) DKV.put(fr._key, fr);
    env.pushAry(fr);
  }
}

class ASTTrim extends ASTUniPrefixOp {
  ASTTrim() { super(new String[]{"trim","x"}); }
  @Override String opStr() { return "trim"; }
  @Override ASTOp make() { return new ASTTrim(); }
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    if (fr.numCols() != 1) throw new IllegalArgumentException("trim works on a single column at a time.");
    if( !fr.anyVec().isEnum() ) throw new IllegalArgumentException("column must be character.");
    String[] doms = fr.anyVec().domain();
    for (int i = 0; i < doms.length; ++i) doms[i] = doms[i].trim();
    fr.anyVec().setDomain(doms);
    if( fr._key!=null && DKV.getGet(fr._key)!=null) DKV.put(fr._key, fr);
    env.pushAry(fr);
  }
}
//
//class ASTPaste extends ASTUniPrefixOp {
//  ASTPaste() { super(); }
//  @Override String opStr() { return "paste"; }
//  @Override ASTOp make() { return new ASTPaste(); }
//  ASTPaste parse_impl(Exec E) {
//
//  }
//  @Override void apply(Env env) {
//    Frame fr = env.popAry();
//  }
//}

//class ASTSample extends ASTOp {
//  ASTSample() { super(new String[]{"sample", "ary", "nobs", "seed"},
//          new Type[]{Type.ARY, Type.ARY, Type.DBL, Type.DBL},
//          OPF_PREFIX, OPP_PREFIX, OPA_RIGHT); }
//  @Override String opStr() { return "sample"; }
//  @Override ASTOp make() { return new ASTSample(); }
//  @Override void apply(Env env, int argcnt, ASTApply apply) {
//    final double seed = env.popDbl();
//    final double nobs = env.popDbl();
//    String skey = env.key();
//    Frame fr = env.popAry();
//    long[] espc = fr.anyVec()._espc;
//    long[] chk_sizes = new long[espc.length];
//    final long[] css = new long[espc.length];
//    for (int i = 0; i < espc.length-1; ++i)
//      chk_sizes[i] = espc[i+1] - espc[i];
//    chk_sizes[chk_sizes.length-1] = fr.numRows() - espc[espc.length-1];
//    long per_chunk_sample = (long) Math.floor(nobs / (double)espc.length);
//    long defecit = (long) (nobs - per_chunk_sample*espc.length) ;
//    // idxs is an array list of chunk indexes for adding to the sample size. Chunks with no defecit can not be "sampled" as candidates.
//    ArrayList<Integer> idxs = new ArrayList<Integer>();
//    for (int i = 0; i < css.length; ++i) {
//      // get the max allowed rows to sample from the chunk
//      css[i] = Math.min(per_chunk_sample, chk_sizes[i]);
//      // if per_chunk_sample > css[i] => spread around the defecit to meet number of rows requirement.
//      long def = per_chunk_sample - css[i];
//      // no more "room" in chunk `i`
//      if (def >= 0) {
//        defecit += def;
//        // else `i` has "room"
//      }
//      if (chk_sizes[i] > per_chunk_sample) idxs.add(i);
//    }
//    if (defecit > 0) {
//      Random rng = new Random(seed != -1 ? (long)seed : System.currentTimeMillis());
//      while (defecit > 0) {
//        if (idxs.size() <= 0) break;
//        // select chunks at random and add to the number of rows they should sample,
//        // up to the number of rows in the chunk.
//        int rand = rng.nextInt(idxs.size());
//        if (css[idxs.get(rand)] == chk_sizes[idxs.get(rand)]) {
//          idxs.remove(rand);
//          continue;
//        }
//        css[idxs.get(rand)]++;
//        defecit--;
//      }
//    }
//
//    Frame fr2 = new MRTask2() {
//      @Override public void map(Chunk[] chks, NewChunk[] nchks) {
//        int N = chks[0]._len;
//        int m = 0;
//        long n = css[chks[0].cidx()];
//        int row = 0;
//        Random rng = new Random(seed != -1 ? (long)seed : System.currentTimeMillis());
//        while( m  < n) {
//          double u = rng.nextDouble();
//          if ( (N - row)* u >= (n - m)) {
//            row++;
//          } else {
//            for (int i = 0; i < chks.length; ++i) nchks[i].addNum(chks[i].at0(row));
//            row++; m++;
//          }
//        }
//      }
//    }.doAll(fr.numCols(), fr).outputFrame(fr.names(), fr.domains());
//    env.subRef(fr, skey);
//    env.poppush(1, fr2, null);
//  }
//}


//class ASTRevalue extends ASTOp {
//
//  ASTRevalue(){ super(new String[]{"revalue", "x", "replace", "warn_missing"},
//          new Type[]{Type.ARY, Type.ARY, Type.STR, Type.DBL},
//          OPF_PREFIX,
//          OPP_PREFIX, OPA_RIGHT); }
//
//  @Override String opStr() { return "revalue"; }
//  @Override ASTOp  make()  { return new ASTRevalue(); }
//
//  @Override void apply(Env env, int argcnt, ASTApply apply) {
//    final boolean warn_missing = env.popDbl() == 1;
//    final String replace = env.popStr();
//    String skey = env.key();
//    Frame fr = env.popAry();
//    if (fr.numCols() != 1) throw new IllegalArgumentException("revalue works on a single column at a time.");
//    String[] old_dom = fr.anyVec()._domain;
//    if (old_dom == null) throw new IllegalArgumentException("Column is not a factor column. Can only revalue a factor column.");
//
//    HashMap<String, String> dom_map = hashMap(replace);
//
//    for (int i = 0; i < old_dom.length; ++i) {
//      if (dom_map.containsKey(old_dom[i])) {
//        old_dom[i] = dom_map.get(old_dom[i]);
//        dom_map.remove(old_dom[i]);
//      }
//    }
//    if (dom_map.size() > 0 && warn_missing) {
//      for (String k : dom_map.keySet()) {
//        env._warnings = Arrays.copyOf(env._warnings, env._warnings.length + 1);
//        env._warnings[env._warnings.length - 1] = "Warning: old value " + k + " not a factor level.";
//      }
//    }
//  }
//
//  private HashMap<String, String> hashMap(String replace) {
//    HashMap<String, String> map = new HashMap<String, String>();
//    //replace is a ';' separated string. Each piece after splitting is a key:value pair.
//    String[] maps = replace.split(";");
//    for (String s : maps) {
//      String[] pair = s.split(":");
//      String key   = pair[0];
//      String value = pair[1];
//      map.put(key, value);
//    }
//    return map;
//  }
//}