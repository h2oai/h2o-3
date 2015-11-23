package water.util;

import sun.misc.Unsafe;
import water.nbhm.UtilUnsafe;

public abstract class AtomicUtils {
  // Atomically-updated float array
  public abstract static class FloatArray {
    private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
    private static final int _Fbase  = _unsafe.arrayBaseOffset(float[].class);
    private static final int _Fscale = _unsafe.arrayIndexScale(float[].class);
    private static long rawIndex(final float[] ary, final int idx) {
      assert idx >= 0 && idx < ary.length;
      return _Fbase + idx * _Fscale;
    }
    static public void setMin( float fs[], int i, float min ) {
      float old = fs[i];
      while( min < old && !_unsafe.compareAndSwapInt(fs,rawIndex(fs,i), Float.floatToRawIntBits(old), Float.floatToRawIntBits(min) ) )
        old = fs[i];
    }
    static public void setMax( float fs[], int i, float max ) {
      float old = fs[i];
      while( max > old && !_unsafe.compareAndSwapInt(fs,rawIndex(fs,i), Float.floatToRawIntBits(old), Float.floatToRawIntBits(max) ) )
        old = fs[i];
    }
    static public void add( float ds[], int i, float y ) {
      long adr = rawIndex(ds,i);
      float old = ds[i];
      while( !_unsafe.compareAndSwapInt(ds,adr, Float.floatToRawIntBits(old), Float.floatToRawIntBits(old+y) ) )
        old = ds[i];
    }
    static public String toString( float fs[] ) {
      SB sb = new SB();
      sb.p('[');
      for( float f : fs )
        sb.p(f==Float.MAX_VALUE ? "max": (f==-Float.MAX_VALUE ? "min": Float.toString(f))).p(',');
      return sb.p(']').toString();
    }
  }

  // Atomically-updated double array
  public static class DoubleArray {
    private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
    private static final int _Dbase  = _unsafe.arrayBaseOffset(double[].class);
    private static final int _Dscale = _unsafe.arrayIndexScale(double[].class);
    private static long rawIndex(final double[] ary, final int idx) {
      assert idx >= 0 && idx < ary.length;
      return _Dbase + idx * _Dscale;
    }
    static public boolean CAS( double[] ds, int i, double old, double newd ) {
      return _unsafe.compareAndSwapLong(ds,rawIndex(ds,i), Double.doubleToRawLongBits(old), Double.doubleToRawLongBits(newd) );
    }
      
    static public void add( double ds[], int i, double y ) {
      double old;
      while( !CAS(ds,i,old=ds[i],old+y) ) ;
    }
    static public void min( double ds[], int i, double min ) {
      double old;
      while( !CAS(ds,i,old=ds[i],Math.min(old,min)) ) ;
    }
    static public void max( double ds[], int i, double max ) {
      double old;
      while( !CAS(ds,i,old=ds[i],Math.max(old,max)) ) ;
    }
  }

  // Atomically-updated long array.  Instead of using the similar JDK pieces,
  // allows the bare array to be exposed for fast readers.
  public static class LongArray {
    private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
    private static final int _Lbase  = _unsafe.arrayBaseOffset(long[].class);
    private static final int _Lscale = _unsafe.arrayIndexScale(long[].class);
    private static long rawIndex(final long[] ary, final int idx) {
      assert idx >= 0 && idx < ary.length;
      return _Lbase + idx * _Lscale;
    }
    static public void incr( long ls[], int i ) { add(ls,i,1); }
    static public void add( long ls[], int i, long x ) {
      long adr = rawIndex(ls,i);
      long old = ls[i];
      while( !_unsafe.compareAndSwapLong(ls,adr, old, old+x) )
        old = ls[i];
    }
  }
  // Atomically-updated int array.  Instead of using the similar JDK pieces,
  // allows the bare array to be exposed for fast readers.
  public static class IntArray {
    private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
    private static final int _Ibase  = _unsafe.arrayBaseOffset(int[].class);
    private static final int _Iscale = _unsafe.arrayIndexScale(int[].class);
    private static long rawIndex(final int[] ary, final int idx) {
      assert idx >= 0 && idx < ary.length;
      return _Ibase + idx * _Iscale;
    }
    static public void incr( int is[], int i ) { add(is,i,1); }
    static public void add( int is[], int i, int x ) {
      long adr = rawIndex(is,i);
      int old = is[i];
      while( !_unsafe.compareAndSwapInt(is,adr, old, old+x) )
        old = is[i];
    }
  }
}


