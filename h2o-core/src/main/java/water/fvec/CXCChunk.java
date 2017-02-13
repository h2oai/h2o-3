package water.fvec;

/**
 * Created by tomas on 2/5/17.
 */
public class CXCChunk extends CX0Chunk {
  final double _con;
  @Override public double con(){return _con;}
  protected CXCChunk(int[] ids, double c) {super(ids); _con = c;}
}
