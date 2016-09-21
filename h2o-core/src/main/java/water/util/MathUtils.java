package water.util;

import edu.emory.mathcs.jtransforms.dct.DoubleDCT_1D;
import edu.emory.mathcs.jtransforms.dct.DoubleDCT_2D;
import edu.emory.mathcs.jtransforms.dct.DoubleDCT_3D;
import edu.emory.mathcs.utils.ConcurrencyUtils;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;

public class MathUtils {

  public static double weightedSigma(long nobs, double wsum, double xSum, double xxSum) {
    double reg = 1.0/wsum;
    return nobs <= 1? 0 : Math.sqrt(xxSum*reg - (xSum*xSum) * reg * reg);
  }

  public static double logFactorial(long y) {
    if(y <= 100) {
      double l = 0;
      for (long i = 2; i <= y; ++i)
        l += Math.log(i);
      return l;
    }
    return y * Math.log(y) - y + .5*Math.log(2*Math.PI*y);
  }

  static public double computeWeightedQuantile(Vec weight, Vec values, double alpha) {
    QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
    Frame tempFrame = weight == null ?
            new Frame(Key.<Frame>make(), new String[]{"y"},     new Vec[]{values}) :
            new Frame(Key.<Frame>make(), new String[]{"y","w"}, new Vec[]{values, weight});
    DKV.put(tempFrame);
    parms._train = tempFrame._key;
    parms._probs = new double[]{alpha};
    parms._weights_column = weight == null ? null : "w";
    Job<QuantileModel> job = new Quantile(parms).trainModel();
    QuantileModel kmm = job.get();
    double value = kmm._output._quantiles[0/*col*/][0/*quantile*/];
    assert(!Double.isNaN(value));
    Log.debug("weighted " + alpha + "-quantile: " + value);
    job.remove();
    kmm.remove();
    DKV.remove(tempFrame._key);
    return value;
  }

  static public class ComputeAbsDiff extends MRTask<ComputeAbsDiff> {
    @Override public void map(Chunk chks[], NewChunk nc[]) {
      for (int i=0; i<chks[0].len(); ++i)
        nc[0].addNum(Math.abs(chks[0].atd(i) - chks[1].atd(i)));
    }
  }

  /**
   * Wrapper around weighted paralell basic stats computation (mean, variance)
   */
  public static final class BasicStats extends Iced {
    private final double[] _mean;
    private final double[] _m2;
    double[] _wsums;
    transient double[] _nawsums;
    long [] _naCnt;
    double[] _var;
    double[] _sd;
    public double _wsum = Double.NaN;
    public long[] _nzCnt;
    long _nobs = -1;

    public BasicStats(int n) {
      _mean = MemoryManager.malloc8d(n);
      _m2 = MemoryManager.malloc8d(n);
      _wsums = MemoryManager.malloc8d(n);
      _nzCnt = MemoryManager.malloc8(n);
      _nawsums = MemoryManager.malloc8d(n);
      _naCnt = MemoryManager.malloc8(n);
    }

    public void add(double x, double w, int i) {
      if(Double.isNaN(x)) {
        _nawsums[i] += w;
        _naCnt[i]++;
      } else if (w != 0) {
        double wsum = _wsums[i] + w;
        double delta = x - _mean[i];
        double R = delta * w / wsum;
        _mean[i] += R;
        _m2[i] += _wsums[i] * delta * R;
        _wsums[i] = wsum;
        ++_nzCnt[i];
      }
    }

    public void add(double[] x, double w) {
      for (int i = 0; i < x.length; ++i)
        add(x[i], w, i);
    }

    public void setNobs(long nobs, double wsum) {
      _nobs = nobs;
      _wsum = wsum;
    }

    public void fillSparseZeros(int i) {
      int zeros = (int)(_nobs - _nzCnt[i]);
      if(zeros > 0) {
        double muReg = 1.0 / (_wsum - _nawsums[i]);
        double zeromean = 0;
        double delta = _mean[i] - zeromean;
        double zerowsum = _wsum - _wsums[i] - _nawsums[i];
        _mean[i] *= _wsums[i] * muReg;
        _m2[i] += delta * delta * _wsums[i] * zerowsum * muReg; //this is the variance*(N-1), will do sqrt(_sigma/(N-1)) later in postGlobal
        _wsums[i] += zerowsum;
      }
    }
    public void fillSparseNAs(int i) {_naCnt[i] = (int)(_nobs - _nzCnt[i]);}
    public void reduce(BasicStats bs) {
      ArrayUtils.add(_nzCnt, bs._nzCnt);
      ArrayUtils.add(_naCnt, bs._naCnt);
      for (int i = 0; i < _mean.length; ++i) {
        double wsum = _wsums[i] + bs._wsums[i];
        if(wsum != 0) {
          double delta = bs._mean[i] - _mean[i];
          _mean[i] = (_wsums[i] * _mean[i] + bs._wsums[i] * bs._mean[i]) / wsum;
          _m2[i] += bs._m2[i] + delta * delta * _wsums[i] * bs._wsums[i] / wsum;
        }
        _wsums[i] = wsum;
      }
      _nobs += bs._nobs;
      _wsum += bs._wsum;
    }

    private double[] variance(double[] res) {
      for (int i = 0; i < res.length; ++i) {
        long nobs = _nobs - _naCnt[i];
        res[i] = (nobs / (nobs - 1.0)) * _m2[i] / _wsums[i];
      }
      return res;
    }

    public double variance(int i){return variance()[i];}
    public double[] variance() {
//      if(sparse()) throw new UnsupportedOperationException("Can not do single pass sparse variance computation");
      if (_var != null) return _var;
      return _var = variance(MemoryManager.malloc8d(_mean.length));
    }
    public double sigma(int i){return sigma()[i];}
    public double[] sigma() {
      if(_sd != null) return _sd;
      double[] res = variance().clone();
      for (int i = 0; i < res.length; ++i)
        res[i] = Math.sqrt(res[i]);
      return _sd = res;
    }
    public double[] mean() {return _mean;}
    public double mean(int i) {return _mean[i];}
    public long nobs() {return _nobs;}

    public boolean isSparse(int col) {return _nzCnt[col] < _nobs;}
  }
  /** Fast approximate sqrt
   *  @return sqrt(x) with up to 5% relative error */
  public static double approxSqrt(double x) {
    return Double.longBitsToDouble(((Double.doubleToLongBits(x) >> 32) + 1072632448) << 31);
  }
  /** Fast approximate sqrt
   *  @return sqrt(x) with up to 5% relative error */
  public static float approxSqrt(float x) {
    return Float.intBitsToFloat(532483686 + (Float.floatToRawIntBits(x) >> 1));
  }
  /** Fast approximate 1./sqrt
   *  @return 1./sqrt(x) with up to 2% relative error */
  public static double approxInvSqrt(double x) {
    double xhalf = 0.5d*x; x = Double.longBitsToDouble(0x5fe6ec85e7de30daL - (Double.doubleToLongBits(x)>>1)); return x*(1.5d - xhalf*x*x);
  }
  /** Fast approximate 1./sqrt
   *  @return 1./sqrt(x) with up to 2% relative error */
  public static float approxInvSqrt(float x) {
    float xhalf = 0.5f*x; x = Float.intBitsToFloat(0x5f3759df - (Float.floatToIntBits(x)>>1)); return x*(1.5f - xhalf*x*x);
  }
  /** Fast approximate exp
   *  @return exp(x) with up to 5% relative error */
  public static double approxExp(double x) {
    return Double.longBitsToDouble(((long)(1512775 * x + 1072632447)) << 32);
  }
  /** Fast approximate log for values greater than 1, otherwise exact
   *  @return log(x) with up to 0.1% relative error */
  public static double approxLog(double x){
    if (x > 1) return ((Double.doubleToLongBits(x) >> 32) - 1072632447d) / 1512775d;
    else return Math.log(x);
  }
  /** Fast calculation of log base 2 for integers.
   *  @return log base 2 of n */
  public static int log2(int n) {
    if (n <= 0) throw new IllegalArgumentException();
    return 31 - Integer.numberOfLeadingZeros(n);
  }
  public static int log2(long n) {
    return 63 - Long.numberOfLeadingZeros(n);
  }

  public static float[] div(float[] nums, float n) {
    assert !Float.isInfinite(n) : "Trying to divide " + Arrays.toString(nums) + " by  " + n; // Almost surely not what you want
    for (int i=0; i<nums.length; i++) nums[i] /= n;
    return nums;
  }

  public static double[] div(double[] nums, double n) {
    assert !Double.isInfinite(n) : "Trying to divide " + Arrays.toString(nums) + " by  " + n; // Almost surely not what you want
    for (int i=0; i<nums.length; i++) nums[i] /= n;
    return nums;
  }

  public static float sum(final float[] from) {
    float result = 0;
    for (float d: from) result += d;
    return result;
  }

  public static double sum(final double[] from) {
    double result = 0;
    for (double d: from) result += d;
    return result;
  }

  public static float sumSquares(final float[] a) {
    return sumSquares(a, 0, a.length);
  }

  /**
   * Approximate sumSquares
   * @param a Array with numbers
   * @param from starting index (inclusive)
   * @param to ending index (exclusive)
   * @return approximate sum of squares based on a sample somewhere in the middle of the array (pos determined by bits of a[0])
   */
  public static float approxSumSquares(final float[] a, int from, int to) {
    final int len = to-from;
    final int samples = Math.max(len / 16, 1);
    final int offset = from + Math.abs(Float.floatToIntBits(a[0])) % (len-samples);
    assert(offset+samples <= to);
    return sumSquares(a, offset, offset + samples) * (float)len / (float)samples;
  }

  public static float sumSquares(final float[] a, int from, int to) {
    assert(from >= 0 && to <= a.length);
    float result = 0;
    final int cols = to-from;
    final int extra=cols-cols%8;
    final int multiple = (cols/8)*8-1;
    float psum1 = 0, psum2 = 0, psum3 = 0, psum4 = 0;
    float psum5 = 0, psum6 = 0, psum7 = 0, psum8 = 0;
    for (int c = from; c < from + multiple; c += 8) {
      psum1 += a[c  ]*a[c  ];
      psum2 += a[c+1]*a[c+1];
      psum3 += a[c+2]*a[c+2];
      psum4 += a[c+3]*a[c+3];
      psum5 += a[c+4]*a[c+4];
      psum6 += a[c+5]*a[c+5];
      psum7 += a[c+6]*a[c+6];
      psum8 += a[c+7]*a[c+7];
    }
    result += psum1 + psum2 + psum3 + psum4;
    result += psum5 + psum6 + psum7 + psum8;
    for (int c = from + extra; c < to; ++c) {
      result += a[c]*a[c];
    }
    return result;
  }

  /**
   * Compare two numbers to see if they are within one ulp of the smaller decade.
   * Order of the arguments does not matter.
   *
   * @param a First number
   * @param b Second number
   * @return true if a and b are essentially equal, false otherwise.
   */
  public static boolean equalsWithinOneSmallUlp(float a, float b) {
    if (Double.isNaN(a) && Double.isNaN(b)) return true;
    float ulp_a = Math.ulp(a);
    float ulp_b = Math.ulp(b);
    float small_ulp = Math.min(ulp_a, ulp_b);
    float absdiff_a_b = Math.abs(a - b); // subtraction order does not matter, due to IEEE 754 spec
    return absdiff_a_b <= small_ulp;
  }

  public static boolean equalsWithinOneSmallUlp(double a, double b) {
    if (Double.isNaN(a) && Double.isNaN(b)) return true;
    double ulp_a = Math.ulp(a);
    double ulp_b = Math.ulp(b);
    double small_ulp = Math.min(ulp_a, ulp_b);
    double absdiff_a_b = Math.abs(a - b); // subtraction order does not matter, due to IEEE 754 spec
    return absdiff_a_b <= small_ulp;
  }

  // Section 4.2: Error bound on recursive sum from Higham, Accuracy and Stability of Numerical Algorithms, 2nd Ed
  // |E_n| <= (n-1) * u * \sum_i^n |x_i| + P(u^2)
  public static boolean equalsWithinRecSumErr(double actual, double expected, int n, double absum) {
    return Math.abs(actual - expected) <= (n-1) * Math.ulp(actual) * absum;
  }

  /** Compare 2 doubles within a tolerance
   *  @param a double
   *  @param b double
   *  @param abseps - Absolute allowed tolerance
   *  @param releps - Relative allowed tolerance
   *  @return true if equal within tolerances  */
  public static boolean compare(double a, double b, double abseps, double releps) {
    return
      Double.compare(a, b) == 0 || // check for equality
      Math.abs(a-b)/Math.max(a,b) < releps ||  // check for small relative error
      Math.abs(a - b) <= abseps; // check for small absolute error
  }

  // some common Vec ops

  public static double innerProduct(double [] x, double [] y){
    double result = 0;
    for (int i = 0; i < x.length; i++)
      result += x[i] * y[i];
    return result;
  }
  public static double l2norm2(double [] x){
    double sum = 0;
    for(double d:x)
      sum += d*d;
    return sum;
  }
  public static double l1norm(double [] x){
    double sum = 0;
    for(double d:x)
      sum += d >= 0?d:-d;
    return sum;
  }
  public static double l2norm(double [] x){
    return Math.sqrt(l2norm2(x));
  }

  public static double [] wadd(double [] x, double [] y, double w){
    for(int i = 0; i < x.length; ++i)
      x[i] += w*y[i];
    return x;
  }

  // Random 1000 larger primes
  public static final long[] PRIMES = {
      709887397L, 98016697L, 85080053L, 56490571L, 385003067, 57525611L, 191172517L, 707389223L,
      38269029L, 971065009L, 969012193L, 932573549L, 88277861L, 557977913L, 186530489L, 971846399L,
      93684557L, 568491823L, 374500471L, 260955337L, 98748991L, 571124921L, 268388903L, 931975097L,
      80137923L, 378339371L, 191476231L, 982164353L, 96991951L, 193488247L, 186331151L, 186059399L,
      99717967L, 714703333L, 195765091L, 934873301L, 33844087L, 392819423L, 709242049L, 975098351L,
      15814261L, 846357791L, 973645069L, 968987629L, 27247177L, 939785537L, 714611087L, 846883019L,
      98514157L, 851126069L, 180055321L, 378662957L, 97312573L, 553353439L, 268057183L, 554327167L,
      24890223L, 180650339L, 964569689L, 565633303L, 52962097L, 931225723L, 556700413L, 570525509L,
      99233241L, 270892441L, 185716603L, 928527371L, 21286513L, 561435671L, 561547303L, 696202733L,
      53624617L, 930346357L, 567779323L, 973736227L, 91898247L, 560750693L, 187256227L, 373704811L,
      35668549L, 191257589L, 934128313L, 698681153L, 81768851L, 378742241L, 971211347L, 848250443L,
      57148391L, 844575103L, 976095787L, 193706609L, 12680637L, 929060857L, 973363793L, 979803301L,
      59840627L, 923478557L, 262430459L, 970229543L, 77980417L, 924763579L, 703130651L, 263613989L,
      88115473L, 695202203L, 378625519L, 850417619L, 37875123L, 696088793L, 553766351L, 381382453L,
      90515451L, 570302171L, 962465983L, 923407679L, 19931057L, 856231703L, 941060833L, 971397239L,
      10339277L, 379853059L, 845156227L, 187980707L, 87821407L, 938344853L, 380122333L, 270054377L,
      83320839L, 261180221L, 192697819L, 839701211L, 12564821L, 556717591L, 848036339L, 374151047L,
      97257047L, 936281293L, 188681027L, 195149543L, 87704907L, 927976717L, 844819139L, 273676181L,
      39585799L, 706129079L, 384034087L, 933489013L, 59297633L, 268994839L, 981927539L, 195840863L,
      67345573L, 967452049L, 560096107L, 381740743L, 30924129L, 924804943L, 856120231L, 378647363L,
      80385621L, 697508593L, 274289269L, 193688753L, 73891551L, 271848133L, 932057111L, 257551951L,
      91279349L, 938126183L, 555432523L, 981016831L, 30805159L, 196382603L, 706893793L, 933713923L,
      24244231L, 378590591L, 710972333L, 269517089L, 16916897L, 562526791L, 183312523L, 189463201L,
      38989417L, 391893721L, 972826333L, 386610647L, 64896971L, 926400467L, 932555329L, 850558381L,
      89064649L, 714662899L, 384851339L, 265636697L, 91508059L, 275418673L, 559709609L, 922161403L,
      10531101L, 857303261L, 853919329L, 558603317L, 55745273L, 856595459L, 923077957L, 841009783L,
      16850687L, 708322837L, 184264963L, 696558959L, 93682079L, 375977179L, 974002649L, 849803629L,
      97926061L, 968610047L, 844793123L, 384591617L, 55237313L, 935336407L, 559316999L, 554674333L,
      14130253L, 846839069L, 931726963L, 696160733L, 75174581L, 557994317L, 838168543L, 966852493L,
      77072929L, 970159979L, 964704397L, 189568151L, 86268653L, 855284593L, 850048289L, 191313583L,
      93713647L, 191142043L, 388880231L, 553249517L, 30195511L, 387150937L, 849836231L, 970592537L,
      28652147L, 268424399L, 558866377L, 186814247L, 39044643L, 976912063L, 845625881L, 711967423L,
      50662731L, 386395531L, 188849761L, 711490979L, 15549633L, 979839541L, 559484329L, 563433161L,
      59397379L, 920856857L, 192399139L, 187354667L, 55056687L, 196880249L, 558354787L, 967650823L,
      94294149L, 389784139L, 180486277L, 565918721L, 20466667L, 268413349L, 267469649L, 936151193L,
      72346123L, 979276561L, 695068741L, 699857383L, 54711473L, 182608813L, 183270007L, 702031919L,
      97944489L, 387586607L, 381249059L, 376605809L, 77319227L, 556347787L, 701093269L, 192346391L,
      90335227L, 256723087L, 962532569L, 266508769L, 17739193L, 937662653L, 847160927L, 555998467L,
      88295583L, 857415067L, 261917263L, 385579793L, 51141643L, 373631119L, 705996133L, 973170461L,
      55331307L, 967455763L, 938587709L, 706688057L, 21297597L, 922065379L, 185517257L, 187628431L,
      96410283L, 563376631L, 570763741L, 936993961L, 52224149L, 979458331L, 392576593L, 700887227L,
      68821447L, 979730771L, 980082293L, 273639451L, 50288347L, 378934783L, 571910639L, 557914661L,
      96941061L, 260494543L, 711310849L, 192637969L, 22890911L, 963887479L, 554730437L, 922265609L,
      78772921L, 696207877L, 570249107L, 393007129L, 86456451L, 385480783L, 926825371L, 267285527L,
      22092111L, 713561533L, 393315437L, 856347343L, 93146269L, 855525691L, 939838357L, 708335053L,
      93532607L, 714598517L, 853725269L, 844167949L, 21977701L, 270958973L, 192136349L, 375609701L,
      19897797L, 966888187L, 932260729L, 383532827L, 25237737L, 272543773L, 392590733L, 853665451L,
      21725587L, 700887881L, 194074883L, 981838607L, 80417439L, 704312201L, 553750697L, 980933669L,
      74528743L, 179675627L, 383340833L, 709235897L, 90741063L, 192309673L, 571935391L, 194902511L,
      94110553L, 924261131L, 191984729L, 269236567L, 58470623L, 182656571L, 849099131L, 569471723L,
      11961733L, 851046631L, 262712029L, 193922059L, 51451747L, 854728031L, 264981697L, 842532959L,
      11163561L, 967373513L, 857689213L, 971242631L, 91159577L, 376996001L, 561336649L, 709380197L,
      53406409L, 963273559L, 273184829L, 559905089L, 80983593L, 570001207L, 181289533L, 846881023L,
      28890767L, 845688421L, 555569233L, 189620681L, 78793177L, 854935111L, 572712211L, 965532551L,
      37847349L, 262570873L, 963609191L, 926753309L, 58346681L, 189095527L, 842218019L, 265500401L,
      58861247L, 389674489L, 390095639L, 841892383L, 85054659L, 191505641L, 712111369L, 841407407L,
      91256717L, 930216869L, 196419757L, 714269687L, 27174241L, 572612297L, 191433857L, 180735229L,
      55107853L, 183312203L, 981881179L, 185146877L, 82402047L, 187382323L, 274363207L, 191076499L,
      57751437L, 187785713L, 924689923L, 393190717L, 71161873L, 197227729L, 180143683L, 381192601L,
      15005641L, 376847017L, 567605161L, 838240673L, 80153253L, 965992537L, 857310253L, 261754247L,
      36064557L, 267898751L, 967090921L, 937570097L, 12337347L, 712318247L, 978577751L, 568905091L,
      94257099L, 842182967L, 374004977L, 381257309L, 96791961L, 921781121L, 557889977L, 192185387L,
      93247459L, 193216277L, 700322947L, 970295303L, 13157043L, 377418233L, 938901113L, 380496409L,
      27278997L, 980067787L, 921546019L, 182505511L, 80115941L, 934837181L, 926914847L, 259623571L,
      28102691L, 562673513L, 967105907L, 926710639L, 94210853L, 920748757L, 391684499L, 387247697L,
      57752203L, 839753723L, 566374183L, 569364071L, 91244107L, 701970299L, 183147761L, 192938983L,
      57579247L, 387206317L, 938222833L, 270174413L, 80376961L, 923378317L, 383078257L, 191690461L,
      96389807L, 267712741L, 850101353L, 970424239L, 34699577L, 707392033L, 846517769L, 572099873L,
      80426597L, 980129011L, 846324977L, 571031159L, 93248107L, 567629729L, 192701459L, 375630173L,
      97379631L, 558891877L, 385348591L, 708982787L, 99143939L, 181841897L, 192597829L, 854675441L,
      71312189L, 383257489L, 382600903L, 714164239L, 14287911L, 555130057L, 970321717L, 570861703L,
      25868783L, 559474921L, 269746163L, 934658899L, 11042893L, 188907143L, 933254173L, 275577487L,
      22606051L, 570314989L, 706436851L, 382812809L, 20093987L, 383146817L, 258516589L, 180236977L,
      70049377L, 929492677L, 704664187L, 185934289L, 58575211L, 392996663L, 856628287L, 197998483L,
      95194827L, 980551813L, 927882983L, 391326917L, 24153433L, 378212663L, 849772571L, 382378159L,
      69371443L, 259661527L, 380291797L, 970105957L, 39696727L, 931108069L, 557712577L, 706204777L,
      90975487L, 377724973L, 976364429L, 258731423L, 32280277L, 966276109L, 392993767L, 922543927L,
      35895501L, 843852797L, 842395019L, 938078633L, 80021733L, 180972413L, 972384389L, 257708257L,
      11399039L, 699607547L, 179571479L, 381531497L, 95577441L, 967694027L, 703939237L, 560134033L,
      10374449L, 969953659L, 570804607L, 188228603L, 98870849L, 695911061L, 179866429L, 566537623L,
      18741029L, 572525543L, 705109633L, 374728357L, 66409487L, 857997661L, 969932363L, 271021117L,
      87386813L, 924659837L, 930064451L, 699659099L, 92722127L, 940860467L, 381665183L, 979952719L,
      27144841L, 274646369L, 936578021L, 559210007L, 16684763L, 196169173L, 926404139L, 192762901L,
      17681727L, 189521161L, 181515617L, 858437443L, 23552873L, 258885643L, 572831971L, 973561471L,
      59372601L, 181459769L, 566285441L, 965442013L, 93491029L, 180786043L, 929988151L, 845756941L,
      35529257L, 699442283L, 853078201L, 390950671L, 15958801L, 712435631L, 387157913L, 976160347L,
      68684279L, 179988047L, 389090791L, 699322219L, 10307823L, 259064219L, 377097319L, 850345549L,
      66881839L, 933108151L, 266299519L, 260426339L, 72105031L, 931087667L, 973797767L, 392582221L,
      66105353L, 843357917L, 965549551L, 555596219L, 98867657L, 973871617L, 928572781L, 965246651L,
      73876453L, 934831181L, 940948433L, 570264209L, 71210171L, 847592843L, 262149649L, 555835717L,
      17468753L, 388931927L, 260194087L, 970748903L, 39762147L, 181554757L, 711884729L, 261162977L,
      35297709L, 856201667L, 380186867L, 180397589L, 11201441L, 922615327L, 376981837L, 554670449L,
      34089477L, 964124867L, 569139349L, 853955087L, 95490287L, 709207027L, 572850679L, 566624309L,
      39946727L, 968467037L, 840315521L, 923008613L, 96636383L, 570123877L, 695094643L, 695377961L,
      85046823L, 698062327L, 840797417L, 197750629L, 88399737L, 389835253L, 939584969L, 923130347L,
      71023647L, 981863369L, 696543251L, 375409421L, 13752431L, 855538433L, 269223991L, 980951861L,
      17976011L, 383342473L, 696386767L, 383000213L, 38001763L, 260224427L, 969142787L, 924409687L,
      92289037L, 705677339L, 854639273L, 709648501L, 51602861L, 927498401L, 963151939L, 257969059L,
      99942561L, 702552397L, 378807467L, 843849547L, 20636249L, 838174921L, 921188483L, 697743737L,
      55171601L, 963313399L, 969542537L, 268784609L, 10638293L, 554031749L, 257309069L, 856356289L,
      272064581L, 193518863L, 272811667L, 382857571L, 705293539L, 94434307L, 841390831L, 378434863L,
      22644091L, 933591301L, 263483903L, 937305671L, 92030791L, 855482651L, 706132187L, 703258151L,
      34513681L, 262886671L, 193130321L, 977976803L, 51169839L, 934495231L, 266741317L, 974393971L,
      22079491L, 700151497L, 705291473L, 568384493L, 93712889L, 851253661L, 265654027L, 393268147L,
      56217787L, 850416367L, 857303827L, 391728109L, 98810113L, 191962153L, 268291579L, 181466911L,
      94017901L, 921053269L, 186716597L, 963617209L, 59349733L, 192916351L, 853395997L, 181896479L,
      54769193L, 186653633L, 841422889L, 560707079L, 92365467L, 703592261L, 982412807L, 982243111L,
      78892241L, 927464383L, 930534359L, 268636259L, 94549379L, 712074763L, 559450939L, 857428151L,
      71670509L, 256671463L, 936352111L, 980141417L, 36271839L, 186475811L, 925100521L, 972243169L,
      91920501L, 696389069L, 928678631L, 381418831L, 12023729L, 844714907L, 857426887L, 846161201L,
      99505771L, 386542469L, 856860959L, 572063227L, 56038117L, 385629949L, 979920607L, 258498697L,
      81234773L, 389956109L, 556370957L, 379944343L, 50730109L, 565321789L, 981670519L, 974403491L,
      96057349L, 711469903L, 979604279L, 265069711L, 35443673L, 197595613L, 925185959L, 940443347L,
      17173331L, 854818409L, 707162809L, 557260003L, 12290843L, 973388453L, 713357609L, 379834097L,
      16945751L, 272464273L, 853795783L, 975641603L, 20326481L, 271093661L, 560031733L, 563000783L,
      89785227L, 381224603L, 389678899L, 382372531L, 93398507L, 713755909L, 379280107L, 849555587L,
      12726569L, 713067799L, 386762897L, 699452197L, 68249743L, 921329677L, 969662999L, 708401153L,
      92343817L, 695690659L, 376186373L, 971774849L, 68191267L, 559122461L, 846282403L, 928908247L,
      36511479L, 921516097L, 270107843L, 568075631L, 87827469L, 844675283L, 562808263L, 191356681L,
      14927579L, 840652927L, 553679459L, 558298787L, 89230059L, 980861633L, 266720513L, 566820913L,
      69320183L, 554150749L, 970182487L, 196312381L, 13836923L, 927087017L, 269236103L, 197279059L,
      27011321L, 190280689L, 844923689L, 708889619L, 35296049L, 383543333L, 971450659L, 932468473L,
      94659689L, 569153671L, 378633757L, 972685003L, 94676831L, 383130073L, 184098373L, 848604173L,
      57587529L, 383922947L, 257719843L, 377849887L, 94816741L, 974841787L, 851800231L, 386896033L,
      28408719L, 852139663L, 975564299L, 268145221L, 11937199L, 386365229L, 190900637L, 187768367L,
  };

  public static double roundToNDigits(double d, int n) {
    if(d == 0)return d;
    int log = (int)Math.log10(d);
    int exp = n;
    exp -= log;
    int ival = (int)(Math.round(d * Math.pow(10,exp)));
    return ival/Math.pow(10,exp);
  }

  public enum Norm {L1,L2,L2_2,L_Infinite}
  public static double[] min_max_mean_stddev(long[] counts) {
    double min = Float.MAX_VALUE;
    double max = Float.MIN_VALUE;
    double mean = 0;
    for (long tmp : counts) {
      min = Math.min(tmp, min);
      max = Math.max(tmp, max);
      mean += tmp;
    }
    mean /= counts.length;
    double stddev = 0;
    for (long tmp : counts) {
      stddev += Math.pow(tmp - mean, 2);
    }
    stddev /= counts.length;
    stddev = Math.sqrt(stddev);
    return new double[] {min,max,mean,stddev};
  }

  public static double sign(double d) {
    if(d == 0)return 0;
    return d < 0?-1:1;
  }

  public static class DCT {

    public static void initCheck(Frame input, int width, int height, int depth) {
      ConcurrencyUtils.setNumberOfThreads(1);
      if (width < 1 || height < 1 || depth < 1)
        throw new H2OIllegalArgumentException("dimensions must be >= 1");
      if (width*height*depth != input.numCols())
        throw new H2OIllegalArgumentException("dimensions HxWxD must match the # columns of the frame");
      for (Vec v : input.vecs()) {
        if (v.naCnt() > 0)
          throw new H2OIllegalArgumentException("DCT can not be computed on rows with missing values");
        if (!v.isNumeric())
          throw new H2OIllegalArgumentException("DCT can only be computed on numeric columns");
      }
    }

    /**
     * Compute the 1D discrete cosine transform for each row in the given Frame, and return a new Frame
     *
     * @param input   Frame containing numeric columns with data samples
     * @param N       Number of samples (must be less or equal than number of columns)
     * @param inverse Whether to compute the inverse
     * @return Frame containing 1D (inverse) DCT of each row (same dimensionality)
     */
    public static Frame transform1D(Frame input, final int N, final boolean inverse) {
      initCheck(input, N, 1, 1);
      return new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
          double[] a = new double[N];
          for (int row = 0; row < cs[0]._len; ++row) {
            // fill 1D array
            for (int i = 0; i < N; ++i)
              a[i] = cs[i].atd(row);

            // compute DCT for each row
            if (!inverse)
              new DoubleDCT_1D(N).forward(a, true);
            else
              new DoubleDCT_1D(N).inverse(a, true);

            // write result to NewChunk
            for (int i = 0; i < N; ++i)
              ncs[i].addNum(a[i]);
          }
        }
      }.doAll(input.numCols(), Vec.T_NUM, input).outputFrame();
    }

    /**
     * Compute the 2D discrete cosine transform for each row in the given Frame, and return a new Frame
     *
     * @param input   Frame containing numeric columns with data samples
     * @param height  height
     * @param width   width
     * @param inverse Whether to compute the inverse
     * @return Frame containing 2D DCT of each row (same dimensionality)
     */
    public static Frame transform2D(Frame input, final int height, final int width, final boolean inverse) {
      initCheck(input, height, width, 1);
      return new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
          double[][] a = new double[height][width];
          // each row is a 2D sample
          for (int row = 0; row < cs[0]._len; ++row) {
            for (int i = 0; i < height; ++i)
              for (int j = 0; j < width; ++j)
                a[i][j] = cs[i * width + j].atd(row);

            // compute 2D DCT
            if (!inverse)
              new DoubleDCT_2D(height, width).forward(a, true);
            else
              new DoubleDCT_2D(height, width).inverse(a, true);

            // write result to NewChunk
            for (int i = 0; i < height; ++i)
              for (int j = 0; j < width; ++j)
                ncs[i * width + j].addNum(a[i][j]);

          }
        }
      }.doAll(height * width, Vec.T_NUM, input).outputFrame();
    }

    /**
     * Compute the 3D discrete cosine transform for each row in the given Frame, and return a new Frame
     *
     * @param input   Frame containing numeric columns with data samples
     * @param height  height
     * @param width   width
     * @param depth   depth
     * @param inverse Whether to compute the inverse
     * @return Frame containing 3D DCT of each row (same dimensionality)
     */
    public static Frame transform3D(Frame input, final int height, final int width, final int depth, final boolean inverse) {
      initCheck(input, height, width, depth);
      return new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
          double[][][] a = new double[height][width][depth];

          // each row is a 3D sample
          for (int row = 0; row < cs[0]._len; ++row) {
            for (int i = 0; i < height; ++i)
              for (int j = 0; j < width; ++j)
                for (int k = 0; k < depth; ++k)
                  a[i][j][k] = cs[i*(width*depth) + j*depth + k].atd(row);

            // compute 3D DCT
            if (!inverse)
              new DoubleDCT_3D(height, width, depth).forward(a, true);
            else
              new DoubleDCT_3D(height, width, depth).inverse(a, true);

            // write result to NewChunk
            for (int i = 0; i < height; ++i)
              for (int j = 0; j < width; ++j)
                for (int k = 0; k < depth; ++k)
                  ncs[i*(width*depth) + j*depth + k].addNum(a[i][j][k]);
          }
        }
      }.doAll(height*width*depth, Vec.T_NUM, input).outputFrame();
    }
  }

  public static class SquareError extends MRTask<SquareError> {
    public double _sum;
    @Override public void map( Chunk resp, Chunk pred ) {
      double sum = 0;
      for( int i=0; i<resp._len; i++ ) {
        double err = resp.atd(i)-pred.atd(i);
        sum += err*err;
      }
      _sum = sum;
    }
    @Override public void reduce( SquareError ce ) { _sum += ce._sum; }
  }

  public static double y_log_y(double y, double mu) {
    if(y == 0)return 0;
    if(mu < Double.MIN_NORMAL) mu = Double.MIN_NORMAL;
    return y * Math.log(y / mu);
  }

  /** Compare signed longs */
  public static int compare(long x, long y) {
    return (x < y) ? -1 : ((x == y) ? 0 : 1);
  }

  /** Copmarision of unsigned longs.
   */
  public static int compareUnsigned(long a, long b) {
    // Just map [0, 2^64-1] to [-2^63, 2^63-1]
    return compare(a^0x8000000000000000L, b^0x8000000000000000L);
  }

  /** Comparision of 128bit unsigned values represented by 2 longs */
  public static int compareUnsigned(long hiA, long loA, long hiB, long loB) {
    int resHi = compareUnsigned(hiA, hiB);
    int resLo = compareUnsigned(loA, loB);
    return resHi != 0 ? resHi : resLo;
  }

  /**
   * Logloss
   * @param err prediction error (between 0 and 1)
   * @return logloss
   */
  public static double logloss(double err) {
    return Math.min(MAXLL, -Math.log(1.0-err));
  }
  final static double MAXLL = -Math.log(1e-15); //34.53878
}
