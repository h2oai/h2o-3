package hex.deeplearning;

import water.util.RandomUtils;

import java.util.Arrays;
import java.util.Random;

/**
 * Helper class for dropout training of Neural Nets
 */
public class Dropout {
  private transient Random _rand;
  private transient byte[] _bits;
  private transient double _rate;

  public byte[] bits() { return _bits; }

//  public Dropout() {
//    _rate = 0.5;
//  }

  @Override
  public String toString() {
    String s = "Dropout: " + super.toString();
    s += "\nRandom: " + _rand.toString();
    s += "\nDropout rate: " + _rate;
    s += "\nbits: ";
    for (int i=0; i< _bits.length*8; ++i) s += unit_active(i) ? "1":"0";
    s += "\n";
    return s;
  }

  Dropout(int units) {
    _bits = new byte[(units+7)/8];
    _rand = RandomUtils.getRNG(0);
    _rate = 0.5;
  }

  Dropout(int units, double rate) {
    this(units);
    _rate = rate;
  }

  public void randomlySparsifyActivation(Storage.Vector a, long seed) {
    if (a instanceof Storage.DenseVector)
      randomlySparsifyActivation((Storage.DenseVector) a, seed);
    else throw new UnsupportedOperationException("randomlySparsifyActivation not implemented for this type: " + a.getClass().getSimpleName());
  }

  // for input layer
  private void randomlySparsifyActivation(Storage.DenseVector a, long seed) {
    if (_rate == 0) return;
    setSeed(seed);
    for( int i = 0; i < a.size(); i++ )
      if (_rand.nextFloat() < _rate) a.set(i, 0);
  }

  // for hidden layers
  public void fillBytes(long seed) {
    setSeed(seed);
    if (_rate == 0.5) _rand.nextBytes(_bits);
    else {
      Arrays.fill(_bits, (byte)0);
      for (int i=0;i<_bits.length*8;++i)
        if (_rand.nextFloat() > _rate) _bits[i / 8] |= 1 << (i % 8);
    }
  }

  public boolean unit_active(int o) {
    return (_bits[o / 8] & (1 << (o % 8))) != 0;
  }

  private void setSeed(long seed) {
    if ((seed >>> 32) < 0x0000ffffL)         seed |= 0x5b93000000000000L;
    if (((seed << 32) >>> 32) < 0x0000ffffL) seed |= 0xdb910000L;
    _rand.setSeed(seed);
  }
}
