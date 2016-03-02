package water.util;

import java.io.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import water.H2O;

public class RandomUtils {
  public enum RNGType { PCGRNG, MersenneTwisterRNG, JavaRNG, XorShiftRNG }
  private static RNGType _rngType = RNGType.PCGRNG;

  /* Returns the configured random generator */
  public static Random getRNG(long... seed) {
    switch(_rngType) {
    case JavaRNG:      return new H2ORandomRNG(seed[0]);
    case XorShiftRNG:  return new XorShiftRNG (seed[0]);
    case PCGRNG:       return new PCGRNG      (seed[0],seed.length > 1 ? seed[1] : 1);
    case MersenneTwisterRNG:
      // Do not copy the seeds - use them, and initialize the first two ints by
      // seeds based given argument.  The call is locked, and also
      // MersenneTwisterRNG will just copy the seeds into its datastructures
      return new MersenneTwisterRNG(ArrayUtils.unpackInts(seed));
    }
    throw H2O.fail();
  }



  // Converted to Java from the C
  /*
   * PCG Random Number Generation for C.
   *
   * Copyright 2014 Melissa O'Neill <oneill@pcg-random.org>
   *
   * Licensed under the Apache License, Version 2.0 (the "License");
   * you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at
   *
   * http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
   *
   * For additional information about the PCG random number generation scheme,
   * including its license and other licensing options, visit
   *
   * http://www.pcg-random.org
   */
  public static class PCGRNG extends Random {
    private long _state;        // Random state
    private long _inc;          // Fixed sequence, always odd
    // Seed the rng. Specified in two parts, state initializer and a sequence
    // selection constant (a.k.a. stream id)
    public PCGRNG(long seed, long seq) {
      //need to call a super-class constructor - ugly by-product of inheritance
      //required for reproducibility - super() would call time-based setSeed() and modify _state to non-zero
      super(0);
      assert(_state == 0);
      _inc = (seq<<1)|1;
      nextInt();
      _state += seed;
      nextInt();
    }

    @Override public synchronized void setSeed(long seed) {
      _state = 0;
      nextInt();
      _state += seed;
      nextInt();
    }

    // CNC: PCG expects the output to be an *unsigned* int which Java does not
    // support.  Instead we're returning a signed int, and the caller has to
    // figure out the sign-extension.
    @Override public int nextInt() { 
      long oldstate = _state;
      _state = oldstate * 6364136223846793005L + _inc;
      int xorshifted = (int)(((oldstate >>> 18) ^ oldstate) >>> 27);
      int rot = (int)(oldstate >>> 59);
      return (xorshifted >>> rot) | (xorshifted << ((-rot) & 31));      
    }
    @Override public long nextLong() { return (((long)nextInt())<<32) | (((long)nextInt())&0xFFFFFFFFL); }

    @Override protected int next(int bits) { long nextseed = nextLong(); return (int) (nextseed & ((1L << bits) - 1)); }
    // Generate a uniformly distributed number, r, where 0 <= r < bound
    @Override public int nextInt(int bound) {
      // To avoid bias, we need to make the range of the RNG a multiple of
      // bound, which we do by dropping output less than a threshold.  A naive
      // scheme to calculate the threshold would be to do
      //
      // uint32_t threshold = 0x100000000ull % bound;
      //
      // but 64-bit div/mod is slower than 32-bit div/mod (especially on 32-bit
      // platforms).  In essence, we do
      //
      // uint32_t threshold = (0x100000000ull-bound) % bound;
      //
      // because this version will calculate the same modulus, but the LHS
      // value is less than 2^32.
      long threshold = (-(long)bound % (long)bound)&0xFFFFFFFFL;
      // Uniformity guarantees that this loop will terminate. In practice, it
      // should usually terminate quickly; on average (assuming all bounds are
      // equally likely), 82.25% of the time, we can expect it to require just
      // one iteration. In the worst case, someone passes a bound of 2^31 + 1
      // (i.e., 2147483649), which invalidates almost 50% of the range. In
      // practice, bounds are typically small and only a tiny amount of the
      // range is eliminated.
      for (;;) {
        long r = ((long)nextInt()) & 0xFFFFFFFFL;
        if (r >= threshold)
          return (int)(r % bound);
      }
    }
  }


  /** Stock Java RNG, but force the initial seed to have no zeros in either the
   *  low 32 or high 32 bits - leading to well known really bad behavior. */
  public static class H2ORandomRNG extends Random {
    public H2ORandomRNG(long seed) {
      super();
      if ((seed >>> 32) < 0x0000ffffL)         seed |= 0x5b93000000000000L;
      if (((seed << 32) >>> 32) < 0x0000ffffL) seed |= 0xdb910000L;
      setSeed(seed);
    }
  }

  /** Simple XorShiftRNG.
   *  Note: According to RF benchmarks it does not provide so accurate results
   *  as {@link java.util.Random}, however it can be used as an alternative. */
  public static class XorShiftRNG extends Random {
    private AtomicLong _seed;
    public XorShiftRNG (long seed) { _seed = new AtomicLong(seed); }
    @Override public long nextLong() {
      long oldseed, nextseed;
      AtomicLong seed = this._seed;
      do {
        oldseed = seed.get();
        nextseed = xorShift(oldseed);
      } while (!seed.compareAndSet(oldseed, nextseed));
      return nextseed;
    }

    @Override public int nextInt() { return nextInt(Integer.MAX_VALUE); }
    @Override public int nextInt(int n) { int r = (int) (nextLong() % n); return r > 0 ? r : -r; }
    @Override protected int next(int bits) { long nextseed = nextLong(); return (int) (nextseed & ((1L << bits) - 1)); }

    private long xorShift(long x) {
      x ^= (x << 21);
      x ^= (x >>> 35);
      x ^= (x << 4);
      return x;
    }
  }

  /**
   * <p>
   * Random number generator based on the <a
   * href="http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/emt.html"
   * target="_top">Mersenne Twister</a> algorithm developed by Makoto Matsumoto
   * and Takuji Nishimura.
   * </p>
   *
   * <p>
   * This is a very fast random number generator with good statistical properties
   * (it passes the full DIEHARD suite). This is the best RNG for most
   * experiments. If a non-linear generator is required, use the slower
   * <code>AESCounterRNG</code> RNG.
   * </p>
   *
   * <p>
   * This PRNG is deterministic, which can be advantageous for testing purposes
   * since the output is repeatable. If multiple instances of this class are
   * created with the same seed they will all have identical output.
   * </p>
   *
   * <p>
   * This code is translated from the original C version and assumes that we will
   * always seed from an array of bytes. I don't pretend to know the meanings of
   * the magic numbers or how it works, it just does.
   * </p>
   *
   * <p>
   * <em>NOTE: Because instances of this class require 128-bit seeds, it is not
   * possible to seed this RNG using the {@link #setSeed(long)} method inherited
   * from {@link java.util.Random}.  Calls to this method will have no effect.
   * Instead the seed must be set by a constructor.</em>
   * </p>
   *
   * @author Makoto Matsumoto and Takuji Nishimura (original C version)
   * @author Daniel Dyer (Java port)
   */
  public static class MersenneTwisterRNG extends Random {

    // Magic numbers from original C version.
    private static final int    N                = 624;
    private static final int    M                = 397;
    private static final int[]  MAG01            = { 0, 0x9908b0df };
    private static final int    UPPER_MASK       = 0x80000000;
    private static final int    LOWER_MASK       = 0x7fffffff;
    private static final int    BOOTSTRAP_SEED   = 19650218;
    private static final int    BOOTSTRAP_FACTOR = 1812433253;
    private static final int    SEED_FACTOR1     = 1664525;
    private static final int    SEED_FACTOR2     = 1566083941;
    private static final int    GENERATE_MASK1   = 0x9d2c5680;
    private static final int    GENERATE_MASK2   = 0xefc60000;

    // Lock to prevent concurrent modification of the RNG's internal state.
    private final ReentrantLock lock             = new ReentrantLock();
    /* State vector */
    private final int[]         mt               = new int[N];
    /* Index into state vector */
    private int                 mtIndex          = 0;

//    public MersenneTwisterRNG(long... seeds){
//      this(unpackInts(seeds));
//    }
    /**
     * Creates an RNG and seeds it with the specified seed data.
     *
     * @param seedInts  The seed data used to initialise the RNG.
     */
    public MersenneTwisterRNG(int... seedInts) {

      // This section is translated from the init_genrand code in the C version.
      mt[0] = BOOTSTRAP_SEED;
      for( mtIndex = 1; mtIndex < N; mtIndex++ ) {
        mt[mtIndex] = (BOOTSTRAP_FACTOR
            * (mt[mtIndex - 1] ^ (mt[mtIndex - 1] >>> 30)) + mtIndex);
      }

      // This section is translated from the init_by_array code in the C version.
      int i = 1;
      int j = 0;
      for( int k = Math.max(N, SEEDS.length); k > 0; k-- ) {
        int jseeds = (j == 0 || j == 1) ? seedInts[j] : SEEDS[j];

        mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * SEED_FACTOR1))
            + jseeds + j;
        i++;
        j++;
        if( i >= N ) {
          mt[0] = mt[N - 1];
          i = 1;
        }
        if( j >= SEEDS.length ) {
          j = 0;
        }
      }
      for( int k = N - 1; k > 0; k-- ) {
        mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * SEED_FACTOR2)) - i;
        i++;
        if( i >= N ) {
          mt[0] = mt[N - 1];
          i = 1;
        }
      }
      mt[0] = UPPER_MASK; // Most significant bit is 1 - guarantees non-zero
                          // initial array.
    }

    @Override
    protected final int next(int bits) {
      int y;
      try {
        lock.lock();
        if( mtIndex >= N ) // Generate N ints at a time.
        {
          int kk;
          for( kk = 0; kk < N - M; kk++ ) {
            y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
            mt[kk] = mt[kk + M] ^ (y >>> 1) ^ MAG01[y & 0x1];
          }
          for( ; kk < N - 1; kk++ ) {
            y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
            mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ MAG01[y & 0x1];
          }
          y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
          mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ MAG01[y & 0x1];

          mtIndex = 0;
        }

        y = mt[mtIndex++];
      } finally {
        lock.unlock();
      }
      // Tempering
      y ^= (y >>> 11);
      y ^= (y << 7) & GENERATE_MASK1;
      y ^= (y << 15) & GENERATE_MASK2;
      y ^= (y >>> 18);

      return y >>> (32 - bits);
    }


    /* 624 int seeds generated from /dev/random
     *
     * SEEDS[0], and SEEDS[1] are reserved for MersenneTwister initialization in hex.rf.Utils.
     * They can obtain any value!
     *
     * Note: SEEDS are modified at this place. The user has to ensure proper locking.
     */
    public static final int[] SEEDS = new int[] {
      0x00000000, 0x00000000, 0x8a885b28, 0xcb618e3c, 0x6812fe78, 0xca8ca770, 0xf2a19ffd, 0xb6821eaa,
      0xd1fa32c7, 0xc6dbee65, 0xd9534b7f, 0xa8e765a6, 0x2da3c864, 0xb5a7766a, 0x2bc7e671, 0xf80571d0,
      0xa7174754, 0xf3234de2, 0x4e7cc080, 0x1140d082, 0x5fad93ab, 0x8cce5b9f, 0x1872465a, 0x6b42ecd3,
      0x2c8c9653, 0x453a2eef, 0xcc508838, 0x5a85a0e1, 0x3b7a05e9, 0x2ac09cfd, 0x88aa58c6, 0xd9680c83,
      0x061c1189, 0xc5ce6f21, 0x0acff61d, 0x3f550f57, 0xfce253ce, 0x72f39c54, 0x1772831b, 0x7f61413f,
      0x5971d316, 0x38306f1e, 0xe4102ecc, 0xe64f0fc5, 0x3bc7ba66, 0x739ef534, 0x1379892e, 0x8f608758,
      0x4828e965, 0xf4ac7b9a, 0xa8ddaba3, 0x50f8b1cb, 0xfec0f9d0, 0x842537e7, 0x5e6231bf, 0xef3ae390,
      0x420f8f3a, 0xeedd75cc, 0xe3c10283, 0x5c38cbd6, 0x662c8b91, 0x2cd589d5, 0xe28522a7, 0xda03a7b4,
      0xb29877dc, 0x45a109fb, 0x99c3021e, 0x0af14661, 0xe85d6e6e, 0xbdaa929b, 0x940e053d, 0x861e7d7d,
      0x73ae673f, 0x8491c460, 0xc01be6a4, 0x06e0818c, 0x142f7399, 0xc80a6a41, 0x45600653, 0x1c0516d5,
      0xd2ff0694, 0xb1cb723d, 0x73f355e0, 0x076cb63a, 0x7db7190f, 0x35ea0b80, 0xa36f646b, 0xb9ebfa2f,
      0x3844839b, 0x58d80a19, 0x1f3d8746, 0x229bb12e, 0x0ac3846d, 0xd2f43715, 0x04aaeb46, 0xacc87633,
      0x7dd5b268, 0xba3651fc, 0xd76801e6, 0x9e413be6, 0xb31b71c5, 0x5fd36451, 0x4041662e, 0x8e87487b,
      0x03126116, 0x6574b757, 0x7717d040, 0x1d15c783, 0x7a167e9c, 0x8e4ec7a0, 0x749bc3e5, 0xfa2ea1b1,
      0x25df2c84, 0xf9e7ae19, 0xe071597a, 0x6ae0fb27, 0x12380f69, 0xf672e42f, 0x5425f6f6, 0xed6e16b7,
      0x36b29279, 0x24cbd8fb, 0x4d682009, 0x0e17116c, 0x10428b6b, 0xe463f573, 0x2c5ff8d0, 0x1102b138,
      0xc544907c, 0xcf403704, 0x2565d0ec, 0x67e3111c, 0xc5097632, 0xe3505d2d, 0xb0a31246, 0x55cbffb3,
      0xf2b662cb, 0x944ba74f, 0xf64a1136, 0x67628af5, 0x1d442a18, 0x31c8c7d4, 0x648a701b, 0x563930c4,
      0x28ecd115, 0x9959be3f, 0x9afa938d, 0x0c40f581, 0x8ec73f72, 0x20dbf8a1, 0x2c2ca035, 0xb81f414c,
      0xfc16c15c, 0xec386121, 0x41d8bd3a, 0x60eab9ce, 0x9f4b093d, 0x56e5bb7c, 0x0d60cd53, 0x3238a405,
      0xa159ab87, 0xdadaaed3, 0xc86b574f, 0x9ed3b528, 0x3137e717, 0x028012fc, 0x8477ea87, 0x6477d097,
      0x06b6e294, 0x1dd29c4e, 0x5c732920, 0xc760bcec, 0x5d40a29a, 0xc581f784, 0x13b46a5e, 0xf6761ea7,
      0x1b4ee8c3, 0x1637d570, 0x0c00569a, 0xd01cb95e, 0x87343e82, 0x17190e4c, 0x357078a3, 0x3b59246c,
      0xdf11b5e7, 0x68971c7a, 0xcc3d497e, 0x21659527, 0x2c211ba2, 0xf34aa1ee, 0x4a07f67e, 0x7ae0eacd,
      0xe05bdc85, 0xfe2347a7, 0xebc4be3f, 0x1f033044, 0x82e2a46e, 0x75c66f49, 0x56c50b1e, 0xc20f0644,
      0x798ec011, 0x9eba0c81, 0x0fe34e70, 0x28061a7f, 0x26536ace, 0x6541a948, 0x305edffe, 0x25eaa0a9,
      0xef64db75, 0xe1f4d734, 0xe27e22de, 0x3b68a4b3, 0x8917d09f, 0x402f7e99, 0xe9b3e3e7, 0x9a95e6fb,
      0x42a5725c, 0x00d9f288, 0x9e893c59, 0x3771df6d, 0xbfb39333, 0x9039fd17, 0x3d574609, 0xb8a44bc4,
      0xe12f34ad, 0x7f165a6c, 0x8e13ec33, 0xa8d935be, 0x00ac09d8, 0x3ffff87b, 0xda94be75, 0x8b1804d5,
      0xd1ac4301, 0xc2b4101d, 0xb8dae770, 0x3062dbf0, 0xc5defd8d, 0xa791e2aa, 0x678f3924, 0xec4ea145,
      0x457c82b5, 0x6698be3c, 0xfbd4913f, 0xff52ad6d, 0x54c7f66d, 0x7d6ec779, 0x9ce9d1d9, 0x384dd1eb,
      0xb4b4d565, 0xa5736588, 0x33ae82b2, 0x051221b0, 0x11a8775f, 0xd2ed52ea, 0xdf99b00b, 0xa0425a1a,
      0xd6b32a9b, 0xfa162152, 0x4de98efb, 0xb0d5553e, 0xdd9d7239, 0x05be808d, 0x438f6f74, 0xdf28fc47,
      0xb6fcd76d, 0x58375c21, 0x1a88eae6, 0x1ce15ca9, 0x46304120, 0xc2a8c9ee, 0xa2eaf06e, 0xf548a76c,
      0xd288b960, 0xec1c7cb5, 0x6e59f189, 0x3424b4eb, 0x521220db, 0x9d2f797d, 0x8561d680, 0x63eda823,
      0x7f406b58, 0x31104105, 0x1a457dc1, 0x3a94cec4, 0xed5a24b7, 0xa11766a2, 0xefd011e1, 0x10806e51,
      0x5519474f, 0x08d1a66f, 0xc83ac414, 0xf9dad4f5, 0xfa64b469, 0x6cbfd6a3, 0xb2e787ce, 0x63eb2f8e,
      0xe0d36a89, 0xe232fe8f, 0xd0d28011, 0xd198ab29, 0x1e5aa524, 0x05ae372d, 0x314fb7fb, 0x7e263de0,
      0x61e8d239, 0x2f76e5b6, 0xaf2af828, 0x4146a159, 0x3626bccf, 0x308a82ed, 0x1e5527a3, 0xe540898d,
      0xb2e944de, 0x010007fd, 0xaabb40cc, 0xa119fd6b, 0xefca25a8, 0xd1389d26, 0x15b65a4b, 0xf1323150,
      0x3798f801, 0xf5787776, 0xcd069f96, 0x91da0117, 0xb603eaa4, 0xb068125e, 0x346216d5, 0xcb0af099,
      0xad8131db, 0x1c5ce132, 0x3a094b8a, 0x68d20e3f, 0x6f62b0b9, 0x5b2da8a9, 0x11530b9a, 0x5c340608,
      0x9b23c1d9, 0xf175fcba, 0x70fddd5e, 0x9c554ec4, 0xfc0cb505, 0x5249997f, 0xc42f151f, 0xee9f506f,
      0x8fb2cd27, 0xb799db4b, 0x4c5c0eeb, 0x37278283, 0x8183b362, 0x928b4cc7, 0x6c895352, 0x9b0a8270,
      0xc5cb93da, 0xf8268a31, 0x09fd1af6, 0xbc6e89fc, 0x5a614eb8, 0xe55b1348, 0x992a69ee, 0x55b0ffb7,
      0x4eb5db62, 0x5cde9e6b, 0xad9b186d, 0xa5006f43, 0xc82c2c7f, 0x822fa75f, 0xa3a4cb06, 0x6d05edda,
      0x5bf76fb7, 0x846a54f8, 0xca7ce73c, 0x43c1a8d1, 0x1b4c79a7, 0x85cb66c7, 0xc541b4ad, 0x07e69a11,
      0xffb1e304, 0xe585f233, 0x506773a5, 0xc7adaa3c, 0xf980d0c6, 0xa3d90125, 0xfbce4232, 0xfe6fed8f,
      0xe17f437a, 0x29c45214, 0xa0ea1046, 0xc025f727, 0x820202ca, 0x554f4e76, 0x5389096c, 0x7d58de96,
      0xe32295b8, 0x689b5fbe, 0xdfefacf1, 0xd4facb70, 0x0cf3703e, 0x78fec105, 0x57b53e14, 0x54bcd2ef,
      0x335f4d0d, 0x58552c2e, 0xf64df202, 0x0e5c3565, 0xa4cb22c5, 0xd91c91c1, 0x7827bb3f, 0x37b456e3,
      0x84950a9e, 0x273edcd7, 0xddaa5ebd, 0xb1f46855, 0xe0052b20, 0xcfb04082, 0xa449e49b, 0xfd95e21c,
      0xa9f477c0, 0xacf0be15, 0x611d1edc, 0xb3dca16a, 0x781efb9a, 0x6480c096, 0x4e545269, 0xbc836952,
      0xd511b539, 0xdf6248b4, 0x8ff7da61, 0x0756106d, 0x92f04a17, 0xee649e83, 0x14e35780, 0x6dc76815,
      0x0fe032bb, 0x1fd66462, 0x0f4be990, 0x1627c658, 0xb95f902d, 0xa6f9e4e9, 0xb7b9aa16, 0x6a0a31d5,
      0x647129e6, 0x071f89b7, 0xe4033ca9, 0xd81b3f59, 0x74f8a887, 0xc44bc880, 0xf1c2d04c, 0xf9e246c9,
      0x529f9c45, 0x14d322e7, 0x8c3305b1, 0x8dd9a988, 0x8a92b883, 0x47574eb3, 0x7b5779f4, 0x759a4eb6,
      0xc8ed6a11, 0x42a4e0ee, 0xf4603b1d, 0x790d9126, 0xa261034e, 0x94569718, 0x5f57c893, 0xa1c2486a,
      0x6727618f, 0xcfb7c5b3, 0xa4c2f232, 0x33b5e051, 0x9ed6c2d0, 0x16f3ec37, 0x5c7c96ba, 0x3a16185f,
      0x361d6c17, 0xa179808b, 0xb6751231, 0xc8486729, 0x873fc8ab, 0xe7f78a78, 0x2fd3093b, 0x489efe89,
      0x83628cd1, 0x67ad9faa, 0x623cbc2f, 0x3f01e8c4, 0xfdad453f, 0x2ccfb969, 0x5d2a3806, 0x9e3df87a,
      0x04700155, 0xab7b57ef, 0x262d746b, 0x737aa3e3, 0x949c724c, 0xa4120c39, 0xb0d6fc26, 0xf627a213,
      0xc0a0bc60, 0x24d6564a, 0x34d460dd, 0x785b0656, 0x9376f6a5, 0x25ebee5b, 0x5a0a5018, 0x84d02b01,
      0xa2b3658a, 0xad0d1cce, 0x38271683, 0x9f491585, 0x8ba28247, 0x40d5a42e, 0x7780e82e, 0x4211ccc3,
      0x99da0844, 0xb85f9474, 0xbdb158b6, 0xf8194c71, 0x6339f3ec, 0x4cd66cf7, 0xb636aa4f, 0x4068c56c,
      0xe41080a1, 0x55740173, 0x95903235, 0x90f39f69, 0x3f10a4e2, 0x3192a79b, 0x0590a944, 0xc9058c4f,
      0x6f05a8eb, 0xdb326d13, 0xfcefbcee, 0xa699db05, 0xd819d477, 0x610f7e52, 0xfa0a4aca, 0x0e6b3f1d,
      0x7a8da290, 0x6d12a9ef, 0xa12642d5, 0xebdedcff, 0x175ed926, 0xa094363a, 0xb3a07e30, 0x34fa8d2c,
      0xbc16e646, 0x3e6de94d, 0xd5288754, 0x204e5283, 0xc61106f6, 0x299835e0, 0xe04e7a38, 0x2e2c1e34,
      0xc069ea80, 0x5c2117cf, 0xd8fc2947, 0x10a40dc9, 0xb40dacd9, 0xfbdac86b, 0x2a8383cb, 0x46d86dc1,
      0x0a1f3958, 0x0f7e59ea, 0x5c10a118, 0xea13bfc8, 0xc82c0da5, 0x4cd40dd7, 0xdaa5dfe9, 0x8c2cc0a3,
      0x8dc15a64, 0x241b160c, 0xc44f573b, 0x3eb3155f, 0x284ba3fc, 0x1ece8db4, 0x03eaf07f, 0x7cbd99fb,
      0x7d313b45, 0xe7ea83a7, 0x6d339d60, 0x0ef002cb, 0x92a04b40, 0x510d79bc, 0x6440e050, 0x33916596,
      0xa11c5df3, 0xb582a3de, 0x031001c1, 0x85951218, 0xbe538ada, 0xe3aec1d2, 0x7fb67836, 0xc2d9ab84,
      0xb1841ad9, 0x1e64cc5f, 0xa3fe111d, 0xd081d6bb, 0xf8ae6c3b, 0x3b12ae4c, 0x9ba5eb58, 0x22931b18,
      0xf99b2e61, 0x628f1252, 0x2fce9aa0, 0xf99a04fb, 0x21577d22, 0x9d474c81, 0x7350e54a, 0xf88c8ac6,
      0x94f38853, 0x0b6333fe, 0x8875045e, 0x90c23689, 0x6b08a34b, 0x3fb742ea, 0xa8a9466a, 0xd543807d,
      0xbf12e26e, 0x10211c25, 0x068852e1, 0xf1d8f035, 0x012a5782, 0xe84cbf5f, 0xee35a87a, 0x8bfa2f09,
    };
  }
}
