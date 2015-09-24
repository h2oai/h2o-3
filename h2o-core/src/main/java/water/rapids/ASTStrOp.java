package water.rapids;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import org.apache.commons.lang.StringUtils;
import water.MRTask;
import water.fvec.*;
import water.parser.ValueString;

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
  @Override public String[] args() { return new String[]{"ary", "pattern"}; }
  @Override int nargs() { return 1+2; } // (countmatches x pattern)
  @Override public String str() { return "countmatches"; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final String[] pattern = asts[2] instanceof ASTStrList 
      ? ((ASTStrList)asts[2])._strs 
      : new String[]{asts[2].exec(env).getStr()};

    if (fr.numCols() != 1)
      throw new IllegalArgumentException("countmatches only takes a single column of data. " +
                                         "Got " + fr.numCols() + " columns.");
    Vec vec = fr.anyVec();  assert vec != null;
    if ( !vec.isString() ) throw new IllegalArgumentException("countmatches requires a string column.  Received "+fr.anyVec().get_type_str()+". Please convert column to strings first.");
    Frame fr2 = new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk) {
        if ( chk instanceof C0DChunk ) // all NAs
          for( int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else {
          ValueString vs = new ValueString();
          for( int i = 0; i < chk._len; ++i ) {
            if( chk.isNA(i) ) newChk.addNA();
            else {
              int cnt = 0;
              for (String aPattern : pattern)
                cnt += StringUtils.countMatches(chk.atStr(vs, i).toString(), aPattern);
              newChk.addNum(cnt, 0);
            }
          }
        }
      }
    }.doAll(1, vec).outputFrame();
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
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } //(tolower x)
  @Override public String str() { return "tolower"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("tolower only takes a single column of data. " +
                                         "Got " + fr.numCols() + " columns.");
    Vec res = null;
    Vec vec = fr.anyVec();  assert vec != null;
    if (vec.isString()) res = toLowerStringCol(vec);
    else throw new IllegalArgumentException("tolower requires a string column. "
        + "Received " + fr.anyVec().get_type_str() + ". Please convert column to strings first.");
    return new ValFrame(new Frame(res));
  }
  private Vec toLowerStringCol(Vec vec) {
    Vec res = new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk) {
        if (chk instanceof C0DChunk) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
          ((CStrChunk) chk).asciiToLower(newChk);
        } else { //UTF requires Java string methods for accuracy
          ValueString vs= new ValueString();
          for(int i =0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else
              newChk.addStr(new ValueString(chk.atStr(vs, i).toString().toLowerCase(Locale.ENGLISH)));
          }
        }
      }
    }.doAll(1, vec).outputFrame().anyVec();

    return res;
  }
}

class ASTToUpper extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } //(toupper x)
  @Override public String str() { return "toupper"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("toupper only takes a single column of data. " +
                                         "Got "+ fr.numCols()+" columns.");
    Vec res = null;
    Vec vec = fr.anyVec();   assert vec != null;
    if (vec.isString()) res = toUpperStringCol(vec);
    else throw new IllegalArgumentException("toupper requires a string column. "
        + "Received " + fr.anyVec().get_type_str() + ". Please convert column to strings first.");
    return new ValFrame(new Frame(res));
  }
  private Vec toUpperStringCol(Vec vec) {
    Vec res = new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if ( chk instanceof C0DChunk ) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
          ((CStrChunk) chk).asciiToUpper(newChk);
        } else { //UTF requires Java string methods for accuracy
          ValueString vs= new ValueString();
          for(int i =0; i < chk._len; i++) {
            if (chk.isNA(i))
              newChk.addNA();
            else
              newChk.addStr(new ValueString(chk.atStr(vs, i).toString().toUpperCase(Locale.ENGLISH)));
          }
        }
      }
    }.doAll(1, vec).outputFrame().anyVec();

    return res;
  }
}

class ASTReplaceFirst extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"pattern", "replacement", "ary", "ignore_case"}; }
  @Override int nargs() { return 1+4; } // (sub pattern replacement x ignore.case)
  @Override public String str() { return "sub"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    final String _pattern     = asts[1].exec(env).getStr();
    final String _replacement = asts[2].exec(env).getStr();
    Frame fr = stk.track(asts[3].exec(env)).getFrame();
    final boolean _ignoreCase = asts[4].exec(env).getNum()==1;

    if (fr.numCols() != 1)
      throw new IllegalArgumentException("replacefirst works on a single column at a time." +
          "Got "+ fr.numCols()+" columns.");
    Vec res = null;
    Vec vec = fr.anyVec();   assert vec != null;
    if ( !vec.isString() ) throw new IllegalArgumentException("replacefirst requires a string column."
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
            ValueString vs = new ValueString();
            for (int i = 0; i < chk._len; i++) {
              if (chk.isNA(i))
                newChk.addNA();
              else {
                if (_ignoreCase)
                  newChk.addStr(new ValueString(chk.atStr(vs, i).toString().toLowerCase(Locale.ENGLISH).replaceFirst(_pattern, _replacement)));
                else
                  newChk.addStr(new ValueString(chk.atStr(vs, i).toString().replaceFirst(_pattern, _replacement)));
              }
            }
          }
        }
      }.doAll(1, vec).outputFrame().anyVec();
    }
    return new ValFrame(new Frame(res));
  }
}

class ASTReplaceAll extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"pattern", "replacement", "ary", "ignore_case"}; }
  @Override int nargs() { return 1+4; } // (sub pattern replacement x ignore.case)
  @Override public String str() { return "gsub"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    final String _pattern     = asts[1].exec(env).getStr();
    final String _replacement = asts[2].exec(env).getStr();
    Frame fr = stk.track(asts[3].exec(env)).getFrame();
    final boolean _ignoreCase = asts[4].exec(env).getNum()==1;

    if (fr.numCols() != 1)
      throw new IllegalArgumentException("replaceall works on a single column at a time." +
                                         "Got "+ fr.numCols()+" columns.");
    Vec res = null;
    Vec vec = fr.anyVec();   assert vec != null;
    if ( !vec.isString() ) throw new IllegalArgumentException("replaceall requires a string column."
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
            ValueString vs = new ValueString();
            for (int i = 0; i < chk._len; i++) {
              if (chk.isNA(i))
                newChk.addNA();
              else {
                if (_ignoreCase)
                  newChk.addStr(new ValueString(chk.atStr(vs, i).toString().toLowerCase(Locale.ENGLISH).replaceAll(_pattern, _replacement)));
                else
                  newChk.addStr(new ValueString(chk.atStr(vs, i).toString().replaceAll(_pattern, _replacement)));
              }
            }
          }
        }
      }.doAll(1, vec).outputFrame().anyVec();
    }
    return new ValFrame(new Frame(res));
  }
}

class ASTTrim extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (trim x)
  @Override public String str() { return "trim"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec res = null;
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("trim only works on a single column at a time." +
                                         "Got "+ fr.numCols()+" columns.");
    Vec vec = fr.anyVec();   assert vec != null;
    if ( vec.isString() ) res = trimStringCol(vec);
    else throw new IllegalArgumentException("trim requires a string column. "
        +"Received "+fr.anyVec().get_type_str()+". Please convert column to strings first.");
    return new ValFrame(new Frame(res));
  }

  private Vec trimStringCol(Vec vec) {
    Vec res = new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if ( chk instanceof C0DChunk ) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        // Java String.trim() only operates on ASCII whitespace
        // so UTF-8 safe methods are not needed here.
        else ((CStrChunk)chk).asciiTrim(newChk);
      }
    }.doAll(1, vec).outputFrame().anyVec();

    return res;
  }
}

class ASTStrLength extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; }
  @Override public String str() { return "length"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec res = null;
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("length only works on a single column at a time." +
                                         "Got "+ fr.numCols()+" columns.");
    Vec vec = fr.anyVec();   assert vec != null;
    if ( vec.isString() ) res = lengthStringCol(vec);
    else throw new IllegalArgumentException("length requires a string column. "
        +"Received "+fr.anyVec().get_type_str()+". Please convert column to strings first.");
    return new ValFrame(new Frame(res));
  }

  private Vec lengthStringCol(Vec vec) {
    Vec res = new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if ( chk instanceof C0DChunk ) // all NAs
          for (int i = 0; i < chk.len(); i++)
            newChk.addNA();
        else if (((CStrChunk)chk)._isAllASCII) { // fast-path operations
          ((CStrChunk) chk).asciiLength(newChk);
        } else { //UTF requires Java string methods for accuracy
          ValueString vs= new ValueString();
          for(int i =0; i < chk._len; i++){
            if (chk.isNA(i))  newChk.addNA();
            else              newChk.addNum(chk.atStr(vs, i).toString().length(), 0);
          }
        }
      }
    }.doAll(1, vec).outputFrame().anyVec();

    return res;
  }
}
