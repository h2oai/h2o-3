package water.fvec;

/**
 * Created by tomas on 2/5/17.
 */
public class CXCChunk extends CX0Chunk {
  final double _con;
  @Override public double con(){return _con;}
  @Override double min() { return _con > 0?0:_con; }
  @Override double max() { return _con > 0?_con:0; }
  protected CXCChunk(int[] ids, double c) {super(ids); _con = c;}
}
