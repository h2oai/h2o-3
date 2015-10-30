package water.rapids;

import org.apache.commons.lang.StringUtils;
import water.MRTask;
import water.MemoryManager;
import water.fvec.*;
import water.parser.BufferedString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

public class ASTStrOp { /*empty*/}

class ASTStrSplit extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "split"}; }
  @Override int nargs() { return 1+2; } // (strsplit x split)
  @Override
  public String str() { return "strsplit"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
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
    final String[]   old_domains = vec.domain();
    final String[][] new_domains = newDomains(old_domains, splitRegEx);

    final String regex = splitRegEx;
    return new MRTask() {
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
    }.doAll(new_domains.length, Vec.T_CAT, new Frame(vec)).outputFrame(null,null,new_domains).vecs();
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

  private Vec[] strSplitStringCol(Vec vec, final String splitRegEx) {
    final int newColCnt = (new CountSplits(splitRegEx)).doAll(vec)._maxSplits;
    return new MRTask() {
      @Override public void map(Chunk[] cs, NewChunk[] ncs) {
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
  private static class CountSplits extends MRTask<CountSplits> {
    // IN
    private final String _regex;
    // OUT
    int _maxSplits = 0;

    CountSplits(String regex) { _regex = regex; }
    @Override public void map(Chunk chk) {
      BufferedString tmpStr = new BufferedString();
      for( int row = 0; row < chk._len; row++ ) {
        if (!chk.isNA(row)) {
          int split = chk.atStr(tmpStr, row).toString().split(_regex).length;
          if (split > _maxSplits) _maxSplits = split;
        }
      }
    }
    @Override public void reduce(CountSplits that) {
      if (this._maxSplits < that._maxSplits) this._maxSplits = that._maxSplits;
    }
  }
}

/**
 * Accepts a frame with a single string column, and a substring to look for in the target.
 * Returns a new integer column containing the countMatches result for each string in the
 * target column.
 *
 * countMatches - Counts how many times the substring appears in the larger string.
 * If either the target string or substring are empty (""), 0 is returned.
 */
class ASTCountMatches extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "pattern"}; }
  @Override int nargs() { return 1+2; } // (countmatches x pattern)
  @Override public String str() { return "countmatches"; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final String[] pattern = asts[2] instanceof ASTStrList 
      ? ((ASTStrList)asts[2])._strs 
      : new String[]{asts[2].exec(env).getStr()};

    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("countmatches() requires a string or categorical column. "
            +"Received "+fr.anyVec().get_type_str()
            +". Please convert column to a string or categorical first.");

    // Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for(Vec v: fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = countMatchesCategoricalCol(v, pattern);
      else
        nvs[i] = countMatchesStringCol(v, pattern);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec countMatchesCategoricalCol(Vec vec, String[] pattern){
    final int[] matchCounts = countDomainMatches(vec.domain(), pattern);
    return new MRTask() {
      @Override public void map(Chunk[] cs, NewChunk[] ncs) {
        Chunk c = cs[0];
        for (int i = 0; i < c._len; ++i) {
          if( !c.isNA(i) ) {
            int idx = (int) c.at8(i);
            ncs[0].addNum(matchCounts[idx]);
          } else ncs[i].addNA();
        }
      }
    }.doAll(1, Vec.T_NUM, new Frame(vec)).outputFrame().anyVec();
  }

  int[] countDomainMatches(String[] domain, String[] pattern) {
    int[] res = new int[domain.length];
    for (int i=0; i < domain.length; i++)
      for (String aPattern : pattern)
        res[i] += StringUtils.countMatches(domain[i], aPattern);
    return res;
  }

  private Vec countMatchesStringCol(Vec vec, String[] pat){
    final String[] pattern = pat;
    return new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk) {
        if ( chk instanceof C0DChunk ) // all NAs
          for( int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else {
          BufferedString tmpStr = new BufferedString();
          for( int i = 0; i < chk._len; ++i ) {
            if( chk.isNA(i) ) newChk.addNA();
            else {
              int cnt = 0;
              for (String aPattern : pattern)
                cnt += StringUtils.countMatches(chk.atStr(tmpStr, i).toString(), aPattern);
              newChk.addNum(cnt, 0);
            }
          }
        }
      }
    }.doAll(Vec.T_NUM, new Frame(vec)).outputFrame().anyVec();
  }
}

/**
 * Accepts a frame with a single string column.
 * Returns a new string column containing the results of the toLower method on each string in the
 * target column.
 *
 * toLower - Converts all of the characters in this String to lower case.
 */
class ASTToLower extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } //(tolower x)
  @Override public String str() { return "tolower"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("tolower() requires a string or categorical column. "
            +"Received "+fr.anyVec().get_type_str()
            +". Please convert column to a string or categorical first.");

    // Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for(Vec v: fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = toLowerCategoricalCol(v);
      else
        nvs[i] = toLowerStringCol(v);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec toLowerCategoricalCol(Vec vec) {
    String[] dom = vec.domain();
    for (int i = 0; i < dom.length; ++i)
      dom[i] = dom[i].toLowerCase(Locale.ENGLISH);

    return vec.makeCopy(dom);
  }

  private Vec toLowerStringCol(Vec vec) {
    return new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if ( chk instanceof C0DChunk ) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
          ((CStrChunk) chk).asciiToLower(newChk);
        } else { //UTF requires Java string methods for accuracy
          BufferedString tmpStr = new BufferedString();
          for(int i =0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else // Locale.ENGLISH to give the correct results for local insensitive strings
              newChk.addStr(chk.atStr(tmpStr, i).toString().toLowerCase(Locale.ENGLISH));
          }
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().anyVec();
  }
}

/**
 * Accepts a frame with a single string column.
 * Returns a new string column containing the results of the toUpper method on each string in the
 * target column.
 *
 * toUpper - Converts all of the characters in this String to upper case.
 */
class ASTToUpper extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } //(toupper x)
  @Override public String str() { return "toupper"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("toupper() requires a string or categorical column. "
            +"Received "+fr.anyVec().get_type_str()
            +". Please convert column to a string or categorical first.");

    // Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for(Vec v: fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = toUpperCategoricalCol(v);
      else
        nvs[i] = toUpperStringCol(v);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec toUpperCategoricalCol(Vec vec) {
    String[] dom = vec.domain();
    for (int i = 0; i < dom.length; ++i)
      dom[i] = dom[i].toUpperCase(Locale.ENGLISH);

    return vec.makeCopy(dom);
  }

  private Vec toUpperStringCol(Vec vec) {
    return new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if ( chk instanceof C0DChunk ) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
          ((CStrChunk) chk).asciiToUpper(newChk);
        } else { //UTF requires Java string methods for accuracy
          BufferedString tmpStr = new BufferedString();
          for(int i =0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else // Locale.ENGLISH to give the correct results for local insensitive strings
              newChk.addStr(chk.atStr(tmpStr, i).toString().toUpperCase(Locale.ENGLISH));
          }
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().anyVec();
  }
}

/**
 * Accepts a frame with a single string column, a regex pattern string, a replacement substring,
 * and a boolean to indicate whether to ignore the case of the target string.
 * Returns a new string column containing the results of the replaceFirst method on each string
 * in the target column.
 *
 * replaceAll - Replaces the first substring of this string that matches the given regular
 * expression with the given replacement.
 */
class ASTReplaceFirst extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "pattern", "replacement", "ignore_case"}; }
  @Override int nargs() { return 1+4; } // (sub x pattern replacement ignore.case)
  @Override public String str() { return "replacefirst"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    final String pattern     = asts[2].exec(env).getStr();
    final String replacement = asts[3].exec(env).getStr();
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final boolean ignoreCase = asts[4].exec(env).getNum()==1;

    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("replacefirst() requires a string or categorical column. "
            +"Received "+fr.anyVec().get_type_str()
            +". Please convert column to a string or categorical first.");

    // Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for(Vec v: fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = replaceFirstCategoricalCol(v, pattern, replacement, ignoreCase);
      else
        nvs[i] = replaceFirstStringCol(v, pattern, replacement, ignoreCase);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec replaceFirstCategoricalCol(Vec vec, String pattern, String replacement, boolean ignoreCase) {
    String[] doms = vec.domain().clone();
    for (int i = 0; i < doms.length; ++i)
      doms[i] = ignoreCase
          ? doms[i].toLowerCase(Locale.ENGLISH).replaceFirst(pattern, replacement)
          : doms[i].replaceFirst(pattern, replacement);

    return vec.makeCopy(doms);
  }

  private Vec replaceFirstStringCol(Vec vec, String pat, String rep, boolean ic) {
    final String pattern = pat;
    final String replacement = rep;
    final boolean ignoreCase = ic;
    return new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if ( chk instanceof C0DChunk ) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else {
//        if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
//          ((CStrChunk) chk).asciiReplaceFirst(newChk);
//        } else { //UTF requires Java string methods for accuracy
          BufferedString tmpStr = new BufferedString();
          for (int i = 0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else {
              if (ignoreCase)
                newChk.addStr(chk.atStr(tmpStr, i).toString().toLowerCase(Locale.ENGLISH).replaceFirst(pattern, replacement));
              else
                newChk.addStr(chk.atStr(tmpStr, i).toString().replaceFirst(pattern, replacement));
            }
          }
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().anyVec();
  }
}

/**
 * Accepts a frame with a single string column, a regex pattern string, a replacement substring,
 * and a boolean to indicate whether to ignore the case of the target string.
 * Returns a new string column containing the results of the replaceAll method on each string
 * in the target column.
 *
 * replaceAll - Replaces each substring of this string that matches the given regular expression
 * with the given replacement.
 */
class ASTReplaceAll extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "pattern", "replacement", "ignore_case"}; }
  @Override int nargs() { return 1+4; } // (sub x pattern replacement ignore.case)
  @Override public String str() { return "replaceall"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    final String pattern     = asts[2].exec(env).getStr();
    final String replacement = asts[3].exec(env).getStr();
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final boolean ignoreCase = asts[4].exec(env).getNum()==1;

    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("replaceall() requires a string or categorical column. "
            +"Received "+fr.anyVec().get_type_str()
            +". Please convert column to a string or categorical first.");

    // Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for(Vec v: fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = replaceAllCategoricalCol(v, pattern, replacement, ignoreCase);
      else
        nvs[i] = replaceAllStringCol(v, pattern, replacement, ignoreCase);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec replaceAllCategoricalCol(Vec vec, String pattern, String replacement, boolean ignoreCase) {
    String[] doms = vec.domain();
    for (int i = 0; i < doms.length; ++i)
      doms[i] = ignoreCase
          ? doms[i].toLowerCase(Locale.ENGLISH).replaceAll(pattern, replacement)
          : doms[i].replaceAll(pattern, replacement);

    return vec.makeCopy(doms);
  }

  private Vec replaceAllStringCol(Vec vec, String pat, String rep, boolean ic) {
    final String pattern = pat;
    final String replacement = rep;
    final boolean ignoreCase = ic;
    return new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if ( chk instanceof C0DChunk ) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else {
//        if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
//          ((CStrChunk) chk).asciiReplaceAll(newChk);
//        } else { //UTF requires Java string methods for accuracy
          BufferedString tmpStr = new BufferedString();
          for (int i = 0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else {
              if (ignoreCase)
                newChk.addStr(chk.atStr(tmpStr, i).toString().toLowerCase(Locale.ENGLISH).replaceAll(pattern, replacement));
              else
                newChk.addStr(chk.atStr(tmpStr, i).toString().replaceAll(pattern, replacement));
            }
          }
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().anyVec();
  }
}

/**
 * Accepts a frame with a single string column.
 * Returns a new string column containing the trimmed versions of the strings in the target column.
 * Trimming removes all characters of value 0x20 or lower at the beginning and end of the
 * target string. Thus this only trims one of the 17 characters UTF considers as a space.
 */
class ASTTrim extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (trim x)
  @Override public String str() { return "trim"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("trim() requires a string or categorical column. "
            +"Received "+fr.anyVec().get_type_str()
            +". Please convert column to a string or categorical first.");

    // Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for(Vec v: fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = trimCategoricalCol(v);
      else
        nvs[i] = trimStringCol(v);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  // FIXME: this should resolve any categoricals that now have the same value after the trim
  private Vec trimCategoricalCol(Vec vec) {
    String[] doms = vec.domain();
    for (int i = 0; i < doms.length; ++i) doms[i] = doms[i].trim();
    Vec v = vec.makeCopy(doms);
    return v;
  }

  private Vec trimStringCol(Vec vec) {
    return new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if ( chk instanceof C0DChunk ) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        // Java String.trim() only operates on ASCII whitespace
        // so UTF-8 safe methods are not needed here.
        else ((CStrChunk)chk).asciiTrim(newChk);
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputFrame().anyVec();
  }
}

/**
 * Accepts a frame with a single string column.
 * Returns a new integer column containing the character count for each string in the target column.
 */
class ASTStrLength extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; }
  @Override public String str() { return "length"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    // Type check
    for (Vec v : fr.vecs())
      if (!(v.isCategorical() || v.isString()))
        throw new IllegalArgumentException("length() requires a string or categorical column. "
            +"Received "+fr.anyVec().get_type_str()
            +". Please convert column to a string or categorical first.");

    // Transform each vec
    Vec nvs[] = new Vec[fr.numCols()];
    int i = 0;
    for(Vec v: fr.vecs()) {
      if (v.isCategorical())
        nvs[i] = lengthCategoricalCol(v);
      else
        nvs[i] = lengthStringCol(v);
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private Vec lengthCategoricalCol(Vec vec) {
    String[] doms = vec.domain();
    int[] catLengths = new int[doms.length];
    for (int i = 0; i < doms.length; ++i) catLengths[i] = doms[i].length();
    Vec res = new MRTask() {
        transient int[] catLengths;
        @Override public void setupLocal() {
          String[] doms = _fr.anyVec().domain();
          catLengths = new int[doms.length];
          for (int i = 0; i < doms.length; ++i) catLengths[i] = doms[i].length();
        }
        @Override public void map(Chunk chk, NewChunk newChk){
          // pre-allocate since the size is known
          newChk._ls = MemoryManager.malloc8(chk._len);
          newChk._xs = MemoryManager.malloc4(chk._len); // sadly, a waste
          for (int i =0; i < chk._len; i++)
            if(chk.isNA(i))
              newChk.addNA();
            else
              newChk.addNum(catLengths[(int)chk.atd(i)],0);
        }
      }.doAll(1, Vec.T_NUM, new Frame(vec)).outputFrame().anyVec();
    return res;
  }

  private Vec lengthStringCol(Vec vec) {
    return new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if( chk instanceof C0DChunk ) { // All NAs
          for( int i =0; i < chk._len; i++)
            newChk.addNA();
        } else if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
          ((CStrChunk) chk).asciiLength(newChk);
        } else { //UTF requires Java string methods for accuracy
          BufferedString tmpStr = new BufferedString();
          for(int i =0; i < chk._len; i++){
            if (chk.isNA(i))  newChk.addNA();
            else              newChk.addNum(chk.atStr(tmpStr, i).toString().length(), 0);
          }
        }
      }
    }.doAll(new byte[]{Vec.T_NUM}, vec).outputFrame().anyVec();
  }
}
