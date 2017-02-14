package water.fvec;

/**
 * Created by tomas on 2/4/17.
 */
public class Const1Chunk extends Chunk  {
  private Const1Chunk(){}
  public static final Const1Chunk _instance = new Const1Chunk();
  @Override
  public Chunk deepCopy() {
    return this; /* no need to copy constant chunk */
  }
  @Override
  public double atd(int idx) {
    return 1;
  }

  @Override
  public long at8(int idx) {
    return 1;
  }

  @Override
  public boolean isNA(int idx) {
    return false;
  }

  @Override
  public long byteSize() {
    return 8; // only 1 instance shared, size ~ 0
  }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._e = 0;
    v._m = 1;
    v._t  = DVal.type.N;
    return v;
  }

  @Override
  public int len() {return 0;}
}
