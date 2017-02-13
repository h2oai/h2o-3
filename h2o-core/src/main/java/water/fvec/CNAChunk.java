package water.fvec;

/**
 * Created by tomas on 2/4/17.
 */
public class CNAChunk extends Chunk {
  private CNAChunk(){}
  public static final CNAChunk _instance = new CNAChunk();
  @Override
  public Chunk deepCopy() {return this; /* no need to copy constant chunk */}

  @Override
  public double atd(int idx) {return Double.NaN;}
  @Override
  public long at8(int idx) {throw new RuntimeException("at8 but value is missing");}

  @Override
  public boolean isNA(int idx) {return true;}


  @Override
  public long byteSize() {
    return 8; // only 1 instance shared, size ~ 8 bytes for the reference
  }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._d = Double.NaN;
    v._t = DVal.type.D;
    return v;
  }
  @Override
  public int len() {return 0;}

}
