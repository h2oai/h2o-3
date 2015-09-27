package water.rapids;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import org.apache.commons.lang.StringUtils;
import water.MRTask;
import water.fvec.*;
import water.parser.BufferedString;

public class ASTStrOp { /*empty*/}

class ASTStrSplit extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "split"}; }
  @Override int nargs() { return 1+2; } // (strsplit x split)
  @Override
  public String str() { return "strsplit"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String split = asts[2].exec(env).getStr();
    if (fr.numCols() != 1) throw new IllegalArgumentException("strsplit() requires a single column.");
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

    if (fr.numCols() != 1)
      throw new IllegalArgumentException("countmatches() only takes a single column of data. " +
                                         "Got " + fr.numCols() + " columns.");
    Vec vec = fr.anyVec();  assert vec != null;
    if ( !vec.isString() ) throw new IllegalArgumentException("countmatches() requires a string" +
        "column.  Received "+fr.anyVec().get_type_str()+". Please convert column to strings first.");
    Frame res = new MRTask() {
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
    }.doAll(1, vec).outputFrame();
    assert res != null;
    return new ValFrame(res);
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
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("tolower() only takes a single column of data. " +
                                         "Got " + fr.numCols() + " columns.");
    Frame res = null;
    Vec vec = fr.anyVec();  assert vec != null;
    if (vec.isString()) res = toLowerStringCol(vec);
    else throw new IllegalArgumentException("tolower() requires a string column. "
        + "Received " + fr.anyVec().get_type_str() + ". Please convert column to strings first.");
    assert res != null;
    return new ValFrame(res);
  }
  private Frame toLowerStringCol(Vec vec) {
    Frame f = new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk) {
        if (chk instanceof C0DChunk) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
          ((CStrChunk) chk).asciiToLower(newChk);
        } else { //UTF requires Java string methods for accuracy
          BufferedString tmpStr1 = new BufferedString();
          BufferedString tmpStr2 = new BufferedString();
          for(int i =0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else
              newChk.addStr(tmpStr2.setTo(chk.atStr(tmpStr1, i).toString().toLowerCase(Locale.ENGLISH)));
          }
        }
      }
    }.doAll(1, vec).outputFrame();
    return f;
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
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("toupper() only takes a single column of data. " +
                                         "Got "+ fr.numCols()+" columns.");
    Frame res = null;
    Vec vec = fr.anyVec();   assert vec != null;
    if (vec.isString()) res = toUpperStringCol(vec);
    else throw new IllegalArgumentException("toupper() requires a string column. "
        + "Received " + fr.anyVec().get_type_str() + ". Please convert column to strings first.");
    assert res != null;
    return new ValFrame(res);
  }
  private Frame toUpperStringCol(Vec vec) {
    Frame f = new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if ( chk instanceof C0DChunk ) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
          ((CStrChunk) chk).asciiToUpper(newChk);
        } else { //UTF requires Java string methods for accuracy
          BufferedString tmpStr1 = new BufferedString();
          BufferedString tmpStr2 = new BufferedString();
          for(int i =0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else // Locale.ENGLISH to give the correct results for local insensitive strings
              newChk.addStr(tmpStr2.setTo(chk.atStr(tmpStr1, i).toString().toUpperCase(Locale.ENGLISH)));
          }
        }
      }
    }.doAll(1, vec).outputFrame();
    return f;
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
  @Override
  public String[] args() { return new String[]{"pattern", "replacement", "ary", "ignore_case"}; }
  @Override int nargs() { return 1+4; } // (sub pattern replacement x ignore.case)
  @Override public String str() { return "replacefirst"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    final String _pattern     = asts[1].exec(env).getStr();
    final String _replacement = asts[2].exec(env).getStr();
    Frame fr = stk.track(asts[3].exec(env)).getFrame();
    final boolean _ignoreCase = asts[4].exec(env).getNum()==1;

    if (fr.numCols() != 1)
      throw new IllegalArgumentException("replacefirst() works on a single column at a time." +
          "Got "+ fr.numCols()+" columns.");
    Frame res = null;
    Vec vec = fr.anyVec();   assert vec != null;
    if ( !vec.isString() ) throw new IllegalArgumentException("replacefirst() requires a string column."
        +" Received "+fr.anyVec().get_type_str()+". Please convert column to strings first.");
    else {
      res = new MRTask() {
        @Override public void map(Chunk chk, NewChunk newChk){
          if ( chk instanceof C0DChunk ) // all NAs
            for (int i = 0; i < chk.len(); i++)
              newChk.addNA();
          else {
//        if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
//          ((CStrChunk) chk).asciiReplaceFirst(newChk);
//        } else { //UTF requires Java string methods for accuracy
            BufferedString tmpStr1 = new BufferedString();
            BufferedString tmpStr2 = new BufferedString();
            for (int i = 0; i < chk._len; i++) {
              if (chk.isNA(i))
                newChk.addNA();
              else {
                if (_ignoreCase)
                  newChk.addStr(tmpStr2.setTo(chk.atStr(tmpStr1, i).toString().toLowerCase(Locale.ENGLISH).replaceFirst(_pattern, _replacement)));
                else
                  newChk.addStr(tmpStr2.setTo(chk.atStr(tmpStr1, i).toString().replaceFirst(_pattern, _replacement)));
              }
            }
          }
        }
      }.doAll(1, vec).outputFrame();
    }
    assert res != null;
    return new ValFrame(res);
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
  public String[] args() { return new String[]{"pattern", "replacement", "ary", "ignore_case"}; }
  @Override int nargs() { return 1+4; } // (sub pattern replacement x ignore.case)
  @Override public String str() { return "replaceall"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    final String _pattern     = asts[1].exec(env).getStr();
    final String _replacement = asts[2].exec(env).getStr();
    Frame fr = stk.track(asts[3].exec(env)).getFrame();
    final boolean _ignoreCase = asts[4].exec(env).getNum()==1;

    if (fr.numCols() != 1)
      throw new IllegalArgumentException("replaceall() works on a single column at a time." +
                                         "Got "+ fr.numCols()+" columns.");
    Frame res = null;
    Vec vec = fr.anyVec();   assert vec != null;
    if ( !vec.isString() ) throw new IllegalArgumentException("replaceall() requires a string column."
        +" Received "+fr.anyVec().get_type_str()+". Please convert column to strings first.");
    else {
      res = new MRTask() {
        @Override public void map(Chunk chk, NewChunk newChk){
          if ( chk instanceof C0DChunk ) // all NAs
            for (int i = 0; i < chk.len(); i++)
              newChk.addNA();
          else {
//        if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
//          ((CStrChunk) chk).asciiReplaceAll(newChk);
//        } else { //UTF requires Java string methods for accuracy
            BufferedString tmpStr1 = new BufferedString();
            BufferedString tmpStr2 = new BufferedString();
            for (int i = 0; i < chk._len; i++) {
              if (chk.isNA(i))
                newChk.addNA();
              else {
                if (_ignoreCase)
                  newChk.addStr(tmpStr2.setTo(chk.atStr(tmpStr1, i).toString().toLowerCase(Locale.ENGLISH).replaceAll(_pattern, _replacement)));
                else
                  newChk.addStr(tmpStr2.setTo(chk.atStr(tmpStr1, i).toString().replaceAll(_pattern, _replacement)));
              }
            }
          }
        }
      }.doAll(1, vec).outputFrame();
    }
    assert res != null;
    return new ValFrame(res);
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
    Frame res = null;
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("trim() only works on a single column at a time." +
                                         "Got "+ fr.numCols()+" columns.");
    Vec vec = fr.anyVec();   assert vec != null;
    if ( vec.isString() ) res = trimStringCol(vec);
    else throw new IllegalArgumentException("trim() requires a string column. "
        +"Received "+fr.anyVec().get_type_str()+". Please convert column to strings first.");
    assert res != null;
    return new ValFrame(res);
  }

  private Frame trimStringCol(Vec vec) {
    Frame f = new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if ( chk instanceof C0DChunk ) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        // Java String.trim() only operates on ASCII whitespace
        // so UTF-8 safe methods are not needed here.
        else ((CStrChunk)chk).asciiTrim(newChk);
      }
    }.doAll(1, vec).outputFrame();
    return f;
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
    Frame res = null;
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("length() only works on a single column at a time." +
                                         "Got "+ fr.numCols()+" columns.");
    Vec vec = fr.anyVec();   assert vec != null;
    if ( vec.isString() ) res = lengthStringCol(vec);
    else throw new IllegalArgumentException("length() requires a string column. "
        +"Received "+fr.anyVec().get_type_str()+". Please convert column to strings first.");
    assert res != null;
    return new ValFrame(res);
  }

  private Frame lengthStringCol(Vec vec) {
    Frame f = new MRTask() {
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
    }.doAll(1, vec).outputFrame();
    return f;
  }
}
