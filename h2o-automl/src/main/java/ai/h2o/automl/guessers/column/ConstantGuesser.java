package ai.h2o.automl.guessers.column;

import ai.h2o.automl.colmeta.ColMeta;
import ai.h2o.automl.guessers.column.IgnoreGuesser.*;
import water.MRTask;
import water.fvec.*;
import water.nbhm.NonBlockingHashMap;
import water.util.IcedDouble;
import water.util.IcedLong;
import water.util.Log;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Is the column mostly constant?
 */
public class ConstantGuesser extends Guesser {

  private static final double THRESHOLD = 0.999;  // 0.001*nrow is non-constant
  public ConstantGuesser(ColMeta cm) { super(cm); }
  @Override public void guess0(String name, Vec v) {
    if( v.isConst() ) return;  // chucked out later
    ConstTask ct = new ConstTask().doAll(v); // ct._cnts <= v.nChunks()
    long[] freqs = new long[ct._cnts.size()];
    Iterator<IcedLong> it = ct._cnts.values().iterator();
    for(int i=0;i<freqs.length;++i)
      freqs[i] = it.next()._val;
    Arrays.sort(freqs);
    long nrow = v.length();
    double cumFrac=0;
    for (long freq : freqs)
      cumFrac += (double) freq / (double) nrow;
    if( cumFrac >= THRESHOLD) {   // most data is in a few constant values... ?
      Log.info("AutoML ignoring " + name + " for mostly constant: " + toFracString(ct._cnts,nrow));
      _cm._ignored=true;
      _cm._ignoredReason= IgnoreReason.mostly_constant;
    }
  }

  private static String toFracString(NonBlockingHashMap<IcedDouble,IcedLong> hm,long nrow) {
    StringBuilder sb = new StringBuilder("{");
    Iterator<IcedDouble> it = hm.keySet().iterator();
    IcedDouble d;
    while(it.hasNext()) {
      sb.append((d=it.next())._val).append("=").append((double)hm.get(d)._val/(double)nrow);
      if( it.hasNext() ) sb.append(",");
    }
    return sb.append("}").toString();
  }

  private static class ConstTask extends MRTask<ConstTask> {
    // get the "const" value and the number of occurences
    NonBlockingHashMap<IcedDouble,IcedLong> _cnts;

    @Override public void map(Chunk c) {
      _cnts = new NonBlockingHashMap<>();
      IcedLong i;
      IcedDouble d = new IcedDouble(0);
      if( c instanceof C0DChunk || c instanceof C0LChunk) {
        d._val = c.atd(0);
        i=_cnts.get(d);
        if( null==i ) _cnts.put(d,new IcedLong(c._len));
        else          i._val+=c._len;
      } else if( c instanceof CXIChunk ) {
        int numnonzeros = c.sparseLenZero();
        i=_cnts.get(d);
        if( null==i ) _cnts.put(d,new IcedLong(c._len-numnonzeros));
        else          i._val+=(c._len-numnonzeros);
      }
    }
    @Override public void reduce(ConstTask t) {
      NonBlockingHashMap<IcedDouble,IcedLong> l=_cnts;
      NonBlockingHashMap<IcedDouble,IcedLong> r=t._cnts;
      if( r.size() > l.size()) { r=l; l=_cnts; }  // larger on the left
      IcedLong i;
      for(IcedDouble d: r.keySet()) {
        i = l.get(d);
        if( null==i ) l.put(d,new IcedLong(r.get(d)._val));
        else          i._val+=r.get(d)._val;
      }
      _cnts=l;
      t._cnts=null;
    }
  }
}
