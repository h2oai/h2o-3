package water.fvec;

/**
 * Created by tomas on 2/4/17.
 */
public class C0Chunk extends Chunk {
  private C0Chunk(){}
  public static final C0Chunk _instance = new C0Chunk();
  @Override
  public Chunk deepCopy() {return this; /* no need to copy constant chunk */}

  @Override
  public double atd(int idx) {return 0;}
  @Override
  public long at8(int idx) {return 0;}

  @Override
  public boolean isNA(int idx) {return false;}

  @Override
  public long byteSize() {
    return 8; // only 1 instance shared, size ~ 8 bytes for the reference
  }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._m = 0;
    v._e = 0;
    v._t = DVal.type.N;
    return v;
  }

  @Override
  public int len() {return 0;}
}
