package water.rapids;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import water.H2O;
import water.Key;
import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;
import water.util.VecUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ASTStrOp { /*empty*/}

class ASTStrSplit extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "split"}; }
  @Override int nargs() { return 1+2; } // (strsplit x split)
  @Override
  public String str() { return "strsplit"; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String splitRegEx = asts[2].exec(env).getStr();

    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry vs = new VecAry();
    for (int i = 0; i < vecs.len(); ++i) {
      VecAry splits;
      if (vecs.isCategorical(i)) {
        vs.addVecs(strSplitCategoricalCol(vecs.getVecs(i), splitRegEx));
      } else {
        vs.addVecs(strSplitStringCol(vecs.getVecs(i), splitRegEx));
      }
    }
    return new ValFrame(new Frame((Key)null,vs));
  }

  private VecAry strSplitCategoricalCol(VecAry vec, String splitRegEx) {
    final String[]   old_domains = vec.domain(0);
    final String[][] new_domains = newDomains(old_domains, splitRegEx);

    final String regex = splitRegEx;
    return new MRTask() {
      @Override public void map(Chunks cs, Chunks.AppendableChunks ncs) {
        for (int i = 0; i < cs.numRows(); ++i) {
          int cnt = 0;
          if( !cs.isNA(i,0) ) {
            int idx = cs.at4(i,0);
            String s = old_domains[idx];
            String[] ss = s.split(regex);
            for (String s1 : ss) {
              int n_idx = Arrays.asList(new_domains[cnt]).indexOf(s1);
              if (n_idx == -1) ncs.addNA(cnt++);
              else ncs.addNum(cnt++,n_idx,0);
            }
          }
          if (cnt < ncs.numCols())
            for (; cnt < ncs.numCols(); ++cnt) ncs.addNA(cnt);
        }
      }
    }.doAll(new_domains.length, Vec.T_CAT, vec).outputVecs(new_domains);
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

  private VecAry strSplitStringCol(VecAry vec, final String splitRegEx) {
    final int newColCnt = (new CountSplits(splitRegEx)).doAll(vec)._maxSplits;
    return new MRTask() {
      @Override public void map(Chunks cs, Chunks.AppendableChunks ncs) {
        BufferedString tmpStr = new BufferedString();
        for (int row = 0; row < cs.numRows(); ++row) {
          int col = 0;
          if (!cs.isNA(row,0)) {
            String[] ss = cs.atStr(tmpStr, row,0).toString().split(splitRegEx);
            for (String s : ss) // distribute strings among new cols
              ncs.addStr(col++,s);
          }
          if (col < ncs.numCols()) // fill remaining cols w/ NA
            for (; col < ncs.numCols(); col++) ncs.addNA(col);
        }
      }
    }.doAll(newColCnt, Vec.T_STR, vec).outputVecs(null);
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
    @Override public void map(Chunks chk) {
      BufferedString tmpStr = new BufferedString();
      for( int row = 0; row < chk.numRows(); row++ ) {
        if (!chk.isNA(row,0)) {
          int split = chk.atStr(tmpStr, row,0).toString().split(_regex).length;
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
  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final String[] pattern = asts[2] instanceof ASTStrList 
      ? ((ASTStrList)asts[2])._strs 
      : new String[]{asts[2].exec(env).getStr()};

    VecAry vecs = fr.vecs();
    // Type check
    for (int i =0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("countmatches() requires a string or categorical column. "
            +"Received "+fr.vecs().typesStr()
            +". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry nvs = new VecAry();

    for(int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(countMatchesCategoricalCol(vecs.getVecs(i), pattern));
      else
        nvs.addVecs(countMatchesStringCol(vecs.getVecs(i), pattern));
    }
    return new ValFrame(new Frame(nvs));
  }

  private VecAry countMatchesCategoricalCol(VecAry vec, String[] pattern){
    final int[] matchCounts = countDomainMatches(vec.domain(0), pattern);
    return new MRTask() {
      @Override public void map(Chunks cs, Chunks.AppendableChunks ncs) {
        for (int i = 0; i < cs.numRows(); ++i) {
          if( !cs.isNA(i,0) ) {
            int idx = cs.at4(i,0);
            ncs.addInteger(matchCounts[idx]);
          } else ncs.addNA(0);
        }
      }
    }.doAll(1, Vec.T_NUM, vec).outputVecs(null);
  }

  int[] countDomainMatches(String[] domain, String[] pattern) {
    int[] res = new int[domain.length];
    for (int i=0; i < domain.length; i++)
      for (String aPattern : pattern)
        res[i] += StringUtils.countMatches(domain[i], aPattern);
    return res;
  }

  private VecAry countMatchesStringCol(VecAry vec, String[] pat){
    final String[] pattern = pat;
    return new MRTask() {
      @Override public void map(Chunks chk, Chunks.AppendableChunks ncs) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk.numRows(); ++i) {
          if (chk.isNA(i, 0)) ncs.addNA(0);
          else {
            int cnt = 0;
            for (String aPattern : pattern)
              cnt += StringUtils.countMatches(chk.atStr(tmpStr, i).toString(), aPattern);
            ncs.addInteger(cnt);
          }
        }
      }
    }.doAll(1,Vec.T_NUM, vec).outputVecs(null);
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
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");
    // Transform each vec
    VecAry nvs = new VecAry();

    for(int i =0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(toLowerCategoricalCol(vecs.getVecs(i)));
      else
        nvs.addVecs(toLowerStringCol(vecs.getVecs(i)));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry toLowerCategoricalCol(VecAry vec) {
    String[] dom = vec.domain(0).clone();
    for (int i = 0; i < dom.length; ++i)
      dom[i] = dom[i].toLowerCase(Locale.ENGLISH);
    return vec.makeCopy(dom);
  }

  private VecAry toLowerStringCol(VecAry vec) {
    return new MRTask() {
      @Override public void map(Chunks chk, Chunks.AppendableChunks newChk){
        BufferedString tmpStr = new BufferedString();
        for(int i =0; i < chk.numRows(); i++) {
          if (chk.isNA(i))
            newChk.addNA();
          else // Locale.ENGLISH to give the correct results for local insensitive strings
            newChk.addStr(chk.atStr(tmpStr, i).toString().toLowerCase(Locale.ENGLISH));
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputVecs(null);
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
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");    // Transform each vec
    VecAry nvs = new VecAry();
    for(int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(toUpperCategoricalCol(vecs.getVecs(i)));
      else
        nvs.addVecs(toUpperStringCol(vecs.getVecs(i)));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry toUpperCategoricalCol(VecAry vec) {
    String[] dom = vec.domain(0).clone();
    for (int i = 0; i < dom.length; ++i)
      dom[i] = dom[i].toUpperCase(Locale.ENGLISH);

    return vec.makeCopy(dom);
  }

  private VecAry toUpperStringCol(VecAry vec) {
    return new MRTask() {
      @Override public void map(Chunks chk, Chunks.AppendableChunks newChk) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk.numRows(); i++) {
          if (chk.isNA(i))
            newChk.addNA();
          else // Locale.ENGLISH to give the correct results for local insensitive strings
            newChk.addStr(chk.atStr(tmpStr, i).toString().toUpperCase(Locale.ENGLISH));
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputVecs(null);
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
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    final String pattern     = asts[2].exec(env).getStr();
    final String replacement = asts[3].exec(env).getStr();
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final boolean ignoreCase = asts[4].exec(env).getNum()==1;

    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry nvs = new VecAry();
    for(int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(replaceFirstCategoricalCol(vecs.getVecs(i), pattern, replacement, ignoreCase));
      else
        nvs.addVecs(replaceFirstStringCol(vecs.getVecs(i), pattern, replacement, ignoreCase));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry replaceFirstCategoricalCol(VecAry vec, String pattern, String replacement, boolean ignoreCase) {
    String[] doms = vec.domain(0).clone();
    for (int i = 0; i < doms.length; ++i)
      doms[i] = ignoreCase
          ? doms[i].toLowerCase(Locale.ENGLISH).replaceFirst(pattern, replacement)
          : doms[i].replaceFirst(pattern, replacement);
    return vec.makeCopy(doms);
  }

  private VecAry replaceFirstStringCol(VecAry vec, String pat, String rep, boolean ic) {
    final String pattern = pat;
    final String replacement = rep;
    final boolean ignoreCase = ic;
    return new MRTask() {
      @Override public void map(Chunks chk, Chunks.AppendableChunks ncs) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk.numRows(); i++) {
          if (chk.isNA(i))
            ncs.addNA();
          else {
            if (ignoreCase)
              ncs.addStr(chk.atStr(tmpStr, i).toString().toLowerCase(Locale.ENGLISH).replaceFirst(pattern, replacement));
            else
              ncs.addStr(chk.atStr(tmpStr, i).toString().replaceFirst(pattern, replacement));
          }
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputVecs(null);
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
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    final String pattern     = asts[2].exec(env).getStr();
    final String replacement = asts[3].exec(env).getStr();
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final boolean ignoreCase = asts[4].exec(env).getNum()==1;

    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry nvs = new VecAry();
    for(int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(replaceAllCategoricalCol(vecs.getVecs(i), pattern, replacement, ignoreCase));
      else
        nvs.addVecs(replaceAllStringCol(vecs.getVecs(i), pattern, replacement, ignoreCase));
      i++;
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry replaceAllCategoricalCol(VecAry vec, String pattern, String replacement, boolean ignoreCase) {
    String[] doms = vec.domain(0).clone();
    for (int i = 0; i < doms.length; ++i)
      doms[i] = ignoreCase
          ? doms[i].toLowerCase(Locale.ENGLISH).replaceAll(pattern, replacement)
          : doms[i].replaceAll(pattern, replacement);

    return vec.makeCopy(doms);
  }

  private VecAry replaceAllStringCol(VecAry vec, String pat, String rep, boolean ic) {
    final String pattern = pat;
    final String replacement = rep;
    final boolean ignoreCase = ic;
    return new MRTask() {
      @Override public void map(Chunks chk, Chunks.AppendableChunks ncs) {

        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk.numRows(); i++) {
          if (chk.isNA(i))
            ncs.addNA();
          else {
            if (ignoreCase)
              ncs.addStr(chk.atStr(tmpStr, i).toString().toLowerCase(Locale.ENGLISH).replaceAll(pattern, replacement));
            else
              ncs.addStr(chk.atStr(tmpStr, i).toString().replaceAll(pattern, replacement));
          }
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, new VecAry(vec)).outputVecs(null);
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
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");
    // Transform each vec
    VecAry nvs = new VecAry();
    for(int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(trimCategoricalCol(vecs.getVecs(i)));
      else
        nvs.addVecs(trimStringCol(vecs.getVecs(i)));
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry trimCategoricalCol(VecAry vec) {
    String[] doms = vec.domain(0).clone();
    
    HashMap<String, ArrayList<Integer>> trimmedToOldDomainIndices = new HashMap<>();
    String trimmed;
    for (int i = 0; i < doms.length; ++i) {
      trimmed = doms[i].trim();
      doms[i] = trimmed;
      
      if(!trimmedToOldDomainIndices.containsKey(trimmed)) {
        ArrayList<Integer> val = new ArrayList<>();
        val.add(i);
        trimmedToOldDomainIndices.put(trimmed, val);
      } else {
        trimmedToOldDomainIndices.get(trimmed).add(i);
      }
    }
    //Check for duplicated domains
    if (trimmedToOldDomainIndices.size() < doms.length)
      return VecUtils.DomainDedupe.domainDeduper(vec, trimmedToOldDomainIndices);
    
    return vec.makeCopy(doms);
  }


  private VecAry trimStringCol(VecAry vec) {
    return new MRTask() {
      @Override public void map(Chunks chk, Chunks.AppendableChunks ncs) {
        asciiTrim(chk, ncs);
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputVecs(null);
  }

public static void asciiTrim(Chunks cs, Chunks.AppendableChunks ncs) {
  throw H2O.unimpl();
  // copy existing data
//  nc = add2NewChunk_impl(nc, 0, _len);
//  //update offsets and byte array
//  for (int i = 0; i < _len; i++) {
//    int j = 0;
//    int off = UnsafeUtils.get4(_mem, (i << 2) + _OFF);
//    if (off != NA) {
//      //UTF chars will appear as negative values. In Java spec, space is any char 0x20 and lower
//      while (_mem[_valstart + off + j] > 0 && _mem[_valstart + off + j] < 0x21) j++;
//      if (j > 0) nc.set_is(i, off + j);
//      while (_mem[_valstart + off + j] != 0) j++; //Find end
//      j--;
//      while (_mem[_valstart + off + j] > 0 && _mem[_valstart + off + j] < 0x21) { //March back to find first non-space
//        nc._ss[off + j] = 0; //Set new end
//        j--;
//      }
//    }
  }
}

/**
 * Accepts a frame with a single string column.
 * Returns a new integer column containing the character count for each string in the target column.
 */
class ASTStrLength extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; }
  @Override public String str() { return "strlen"; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");
    // Transform each vec
    VecAry nvs = new VecAry();
    for(int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(lengthCategoricalCol(vecs.getVecs(i)));
      else
        nvs.addVecs(lengthStringCol(vecs.getVecs(i)));
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry lengthCategoricalCol(VecAry vec) {
    //String[] doms = vec.domain();
    //int[] catLengths = new int[doms.length];
    //for (int i = 0; i < doms.length; ++i) catLengths[i] = doms[i].length();
    VecAry res = new MRTask() {
        transient int[] catLengths;
        @Override public void setupLocal() {
          String[] doms = _vecs.domain(0);
          catLengths = new int[doms.length];
          for (int i = 0; i < doms.length; ++i) catLengths[i] = doms[i].length();
        }
        @Override public void map(Chunks chk, Chunks.AppendableChunks newChk){
          // pre-allocate since the size is known
          for (int i =0; i < chk.numRows(); i++)
            if(chk.isNA(i))
              newChk.addNA();
            else
              newChk.addInteger(catLengths[(int)chk.atd(i)]);
        }
      }.doAll(1, Vec.T_NUM, vec).outputVecs(null);
    return res;
  }

  private VecAry lengthStringCol(VecAry vec) {
    throw H2O.unimpl();
//    return new MRTask() {
//      @Override public void map(Chunk chk, NewChunk newChk){
//        if( chk instanceof C0DChunk ) { // All NAs
//          for( int i =0; i < chk._len; i++)
//            newChk.addNA();
//        } else if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
//          ((CStrChunk) chk).asciiLength(newChk);
//        } else { //UTF requires Java string methods for accuracy
//          BufferedString tmpStr = new BufferedString();
//          for(int i =0; i < chk._len; i++){
//            if (chk.isNA(i))  newChk.addNA();
//            else              newChk.addInteger(chk.atStr(tmpStr, i).toString().length(), 0);
//          }
//        }
//      }
//    }.doAll(new byte[]{Vec.T_NUM}, vec).outputVecs(null);
  }
}

class ASTSubstring extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "startIndex", "endIndex"}; }
  @Override int nargs() {return 1+3; } // (substring x startIndex endIndex)
  @Override public String str() { return "substring"; }
  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int startIndex = (int) asts[2].exec(env).getNum();
    if (startIndex < 0) startIndex = 0;
    int endIndex = asts[3] instanceof ASTNumList ? Integer.MAX_VALUE : (int) asts[3].exec(env).getNum();
    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");
    // Transform each vec
    VecAry nvs = new VecAry();

    for (int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(substringCategoricalCol(vecs.getVecs(i), startIndex, endIndex));
      else
        nvs.addVecs(substringStringCol(vecs.getVecs(i), startIndex, endIndex));

    }
    
    return new ValFrame(new Frame(nvs));
  }

  private VecAry substringCategoricalCol(VecAry vec, int startIndex, int endIndex) {
    if (startIndex >= endIndex)
      return vec.makeZero(new String[]{""});
    String[] dom = vec.domain(0).clone();
    
    HashMap<String, ArrayList<Integer>> substringToOldDomainIndices = new HashMap<>();
    String substr;
    for (int i = 0; i < dom.length; i++) {
      substr = dom[i].substring(startIndex < dom[i].length() ? startIndex : dom[i].length(),
              endIndex < dom[i].length() ? endIndex : dom[i].length());
      dom[i] = substr;

      if (!substringToOldDomainIndices.containsKey(substr)) {
        ArrayList<Integer> val = new ArrayList<>();
        val.add(i);
        substringToOldDomainIndices.put(substr, val);
      } else {
        substringToOldDomainIndices.get(substr).add(i);
      }
    }
    //Check for duplicated domains
    if (substringToOldDomainIndices.size() < dom.length)
      return VecUtils.DomainDedupe.domainDeduper(vec, substringToOldDomainIndices);
    
    return vec.makeCopy(dom);
  }
  
  private VecAry substringStringCol(VecAry vec, final int startIndex, final int endIndex) {
    return new MRTask() {
      @Override
      public void map(Chunks chks, Chunks.AppendableChunks ncs) {
        //UTF requires Java string methods
          BufferedString tmpStr = new BufferedString();
          for (int i = 0; i < chks.numRows(); i++) {
            if (chks.isNA(i))
              ncs.addNA();
            else {
              String str = chks.atStr(tmpStr, i).toString();
              ncs.addStr(str.substring(startIndex < str.length() ? startIndex : str.length(),
                      endIndex < str.length() ? endIndex : str.length()));
            }
          }

      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputVecs(null);
  }
  
}

/**
 * Accepts a frame with a single string column.
 * Returns a new string column containing the lstripped versions of the strings in the target column.
 * Stripping removes all characters in the strings for the target columns that match the user provided set
 */
class ASTLStrip extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "set"}; }
  @Override int nargs() { return 1+2; }
  @Override public String str() { return "lstrip"; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String set = asts[2].exec(env).getStr();

    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry nvs = new VecAry();

    for(int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(lstripCategoricalCol(vecs.getVecs(i), set));
      else
        nvs.addVecs(lstripStringCol(vecs.getVecs(i), set));
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry lstripCategoricalCol(VecAry vec, String set) {
    String[] doms = vec.domain(0).clone();

    HashMap<String, ArrayList<Integer>> strippedToOldDomainIndices = new HashMap<>();
    String stripped;

    for (int i = 0; i < doms.length; i++) {
      stripped = StringUtils.stripStart(doms[i], set);
      doms[i] = stripped;

      if(!strippedToOldDomainIndices.containsKey(stripped)) {
        ArrayList<Integer> val = new ArrayList<>();
        val.add(i);
        strippedToOldDomainIndices.put(stripped, val);
      } else {
        strippedToOldDomainIndices.get(stripped).add(i);
      }
    }
    //Check for duplicated domains
    if (strippedToOldDomainIndices.size() < doms.length)
      return VecUtils.DomainDedupe.domainDeduper(vec, strippedToOldDomainIndices);
    return vec.makeCopy(doms);
  }

  private VecAry lstripStringCol(VecAry vec, String set) {
    final String charSet = set;
    return new MRTask() {
      @Override public void map(Chunks chk, Chunks.AppendableChunks ncs) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk.numRows(); i++) {
          if (chk.isNA(i))
            ncs.addNA();
          else
            ncs.addStr(StringUtils.stripStart(chk.atStr(tmpStr, i).toString(), charSet));
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputVecs(null);
  }
}

/**
 * Accepts a frame with a single string column.
 * Returns a new string column containing the rstripped versions of the strings in the target column.
 * Stripping removes all characters in the strings for the target columns that match the user provided set
 */
class ASTRStrip extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "set"}; }
  @Override int nargs() { return 1+2; }
  @Override public String str() { return "rstrip"; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String set = asts[2].exec(env).getStr();

    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");

    // Transform each vec
    VecAry nvs = new VecAry();

    for(int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(rstripCategoricalCol(vecs.getVecs(i), set));
      else
        nvs.addVecs(rstripStringCol(vecs.getVecs(i), set));
    }

    return new ValFrame(new Frame(nvs));
  }

  private VecAry rstripCategoricalCol(VecAry vec, String set) {
    String[] doms = vec.domain(0).clone();

    HashMap<String, ArrayList<Integer>> strippedToOldDomainIndices = new HashMap<>();
    String stripped;

    for (int i = 0; i < doms.length; i++) {
      stripped = StringUtils.stripEnd(doms[i], set);
      doms[i] = stripped;

      if(!strippedToOldDomainIndices.containsKey(stripped)) {
        ArrayList<Integer> val = new ArrayList<>();
        val.add(i);
        strippedToOldDomainIndices.put(stripped, val);
      } else {
        strippedToOldDomainIndices.get(stripped).add(i);
      }
    }
    //Check for duplicated domains
    if (strippedToOldDomainIndices.size() < doms.length)
      return VecUtils.DomainDedupe.domainDeduper(vec, strippedToOldDomainIndices);

    return vec.makeCopy(doms);
  }

  private VecAry rstripStringCol(VecAry vec, String set) {
    final String charSet = set;
    return new MRTask() {
      @Override public void map(Chunks chk, Chunks.AppendableChunks ncs){
        BufferedString tmpStr = new BufferedString();
        for(int i = 0; i < chk.numRows(); i++) {
          if (chk.isNA(i))
            ncs.addNA();
          else
            ncs.addStr(StringUtils.stripEnd(chk.atStr(tmpStr, i).toString(), charSet));
        }
      }
    }.doAll(new byte[]{Vec.T_STR}, vec).outputVecs(null);
  }
}

class ASTEntropy extends ASTPrim {
  @Override public String[] args() {return new String[] {"ary"}; }
  @Override int nargs() {return 1+1; } // (entropy x)
  @Override public String str() { return "entropy"; }
  @Override public ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");
    //Transform each vec
    VecAry nvs = new VecAry();
    for (int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(entropyCategoricalCol(vecs.getVecs(i)));
      else
        nvs.addVecs(entropyStringCol(vecs.getVecs(i)));
    }
    
    return new ValFrame(new Frame(nvs));
  }
  
  private VecAry entropyCategoricalCol(VecAry vec) {
    VecAry res = new MRTask() {
      transient double[] catEntropies;
      @Override public void setupLocal() {
        String[] doms = _vecs.domain(0);
        catEntropies = new double[doms.length];
        for (int i = 0; i < doms.length; i++) catEntropies[i] = calcEntropy(doms[i]);
      }
      @Override public void map(Chunks chk, Chunks.AppendableChunks ncs) {
        //pre-allocate since the size is known
        for (int i = 0; i < chk.numRows(); i++)
          if (chk.isNA(i))
            ncs.addNA();
          else 
            ncs.addNum(catEntropies[(int) chk.atd(i)]);
      }
    }.doAll(1, Vec.T_NUM, vec).outputVecs(null);
    return res;
  }
  
  private VecAry entropyStringCol(VecAry vec) {
    return new MRTask() {
      @Override
      public void map(Chunks chk, Chunks.AppendableChunks newChk) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk.numRows(); i++) {
          if (chk.isNA(i))
            newChk.addNA();
          else {
            String str = chk.atStr(tmpStr, i).toString();
            newChk.addNum(calcEntropy(str));
          }
        }
      }
    }.doAll(new byte[]{Vec.T_NUM}, vec).outputVecs(null);
  }
  
  //Shannon's entropy
  private double calcEntropy(String str) {
    
    HashMap<Character, Integer> freq = new HashMap<>();
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      Integer count = freq.get(c);
      if (count == null) freq.put(c, 1);
      else freq.put(c, count + 1);
    }
    double sume = 0;
    int N = str.length();
    double n;
    for (char c: freq.keySet()) {
      n = freq.get(c);
      sume += -n/ N * Math.log(n/N) / Math.log(2);
    }
    return sume;
  }
}

class ASTCountSubstringsWords extends ASTPrim {
  @Override public String[] args() {return new String[]{"ary", "words"};}
  @Override int nargs() {return 1 + 2;} // (num_valid_substrings x words)
  @Override public String str() {return "num_valid_substrings";}
  @Override public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String wordsPath = asts[2].exec(env).getStr();

    VecAry vecs = fr.vecs();
    // Type check
    for (int i = 0; i < vecs.len(); ++i)
      if (!(vecs.isCategorical(i) || vecs.isString(i)))
        throw new IllegalArgumentException("strsplit() requires a string or categorical column. "
            + "Received " + fr.vecs().typesStr()
            + ". Please convert column to a string or categorical first.");

    HashSet<String> words = null;
    try {
      words = new HashSet<>(FileUtils.readLines(new File(wordsPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    //Transform each vec
    VecAry nvs = new VecAry();

    for (int i = 0; i < vecs.len(); ++i) {
      if (vecs.isCategorical(i))
        nvs.addVecs(countSubstringsWordsCategoricalCol(vecs.getVecs(i), words));
      else
        nvs.addVecs(countSubstringsWordsStringCol(vecs.getVecs(i), words));
    }
    return new ValFrame(new Frame(nvs));
  }

  private VecAry countSubstringsWordsCategoricalCol(VecAry vec, final HashSet<String> words) {
    VecAry res = new MRTask() {
      transient double[] catCounts;

      @Override
      public void setupLocal() {
        String[] doms = _vecs.domain(0);
        catCounts = new double[doms.length];
        for (int i = 0; i < doms.length; i++) catCounts[i] = calcCountSubstringsWords(doms[i], words);
      }

      @Override
      public void map(Chunks chk, Chunks.AppendableChunks newChk) {
        //pre-allocate since the size is known
        for (int i = 0; i < chk.numRows(); i++)
          if (chk.isNA(i))
            newChk.addNA();
          else
            newChk.addNum(catCounts[(int) chk.atd(i)]);
      }
    }.doAll(1, Vec.T_NUM, vec).outputVecs(null);
    return res;
  }

  private VecAry countSubstringsWordsStringCol(VecAry vec, final HashSet<String> words) {
    return new MRTask() {
      @Override
      public void map(Chunks chk, Chunks.AppendableChunks newChk) {
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < chk.numRows(); i++) {
          if (chk.isNA(i))
            newChk.addNA();
          else {
            String str = chk.atStr(tmpStr, i).toString();
            newChk.addInteger(calcCountSubstringsWords(str, words));
          }
        }
      }
    }.doAll(new byte[]{Vec.T_NUM}, vec).outputVecs(null);
  }
  
  // count all substrings >= 2 chars that are in words 
  private int calcCountSubstringsWords(String str, HashSet<String> words) {
    int wordCount = 0;
    int N = str.length();
    for (int i = 0; i < N-1; i++) 
      for (int j = i+2; j < N+1; j++) {
        if (words.contains(str.substring(i, j))) 
          wordCount += 1;
      }
    return wordCount;  
  }
  
}