package ai.h2o.automl.guessers.column;


import ai.h2o.automl.colmeta.ColMeta;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashMapLong;
import water.util.IcedInt;
import water.util.VecUtils;

/**
 * Is the problem regression or classification?
 *
 * This is typically a matter of reading the Vec's type IFF the type is categorical (then
 * it's classification). Or if the Vec is some non-integral numeric type, then it may be
 * safe to call it a regression problem (though AutoML better buttress with more data).
 *
 * Things start to get weird if the type is int: is it regression? is it classification?
 * What helps to decide if the column is discrete/continuous:
 *  How many uniques? (2? 10? 10000?)
 *  What's the column name? ("isDelayed" => binomial; "age" (still ambiguous?) noun or not?)
 *
 * If no decision is made what should we do? WARN or ERROR??
 */
public class ProblemTypeGuesser extends Guesser {
  public ProblemTypeGuesser(ColMeta cm) { super(cm); }

  @Override public void guess0(String name, Vec v) { // true -> classification; false -> regression
    if( !_cm._response ) return;
    if( v.get_type() == Vec.T_CAT ) _cm._isClass = true;
    else if( !v.isInt()) {
      if( v.isNumeric() ) _cm._isClass = false;
      // should probably throw up if v.isString() (no h2o algo works in that case)
      _cm._isClass = true;
    } else {
      // only checks for 0/1
      // do some more thorough analysis on the column, might be somewhat intensive
      // let's try to histo into 256 bins, we'll save some of this data for the metadata
      _cm._isClass = v.isBinary();
    }

    if( _cm._isClass && !v.isCategorical() )
      new InplaceToCategorical(v).doAll(v);
  }

  private static class InplaceToCategorical extends MRTask<InplaceToCategorical> {
    final Vec _v;
    final String[] _domain;
    private NonBlockingHashMapLong<IcedInt> _map;
    InplaceToCategorical(Vec v) {
      _v=v;
      long longDomain[] = new VecUtils.CollectDomain().doAll(v).domain();
      _map = new NonBlockingHashMapLong<>();
      _domain = new String[longDomain.length];
      for(int i=0;i<longDomain.length;++i) {
        _domain[i] = ""+longDomain[i];
        _map.put(longDomain[i],new IcedInt(i));
      }
    }

    @Override public void map(Chunk c) {
      for(int i=0;i<c._len;++i)
        c.set(i,_map.get(c.at8(i))._val);
    }
    @Override public void postGlobal() { _v.setDomain(_domain); _map=null; }
  }

  public static void advancedGuesser() {
    // TODO:
    // Interesting long-term idea: ask the users what their data is about.
    // NLP on that can likely advise some interesting strategies that might
    // be hard to discover using regular metafeatures.
    //
    // Idea comes from thinking that I can often tell what algorithms will do
    // well and what won't by just reading a problem statement. And largely this
    // is where you see generic approaches failing--way different contexts. We
    // want to be able to do it from data characteristics, but if that's difficult
    // to get up and running, maybe we just ask.
    // A lot you could do with that for future analysis too, if you start collecting
    // it at the outset. You tell me you have a fraud case, but
    // WARNING: your data looks nothing like what I would normally expect in fraud.
    // No reason to think that can't be done.
  }
}