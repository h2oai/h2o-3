package water.etl.prims.mungers;

import water.DKV;
import water.Futures;
import water.Key;
import water.fvec.AppendableVec;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.vals.ValFrame;
import water.util.VecUtils;

public final class Pivot {
  private Pivot() {}
  public static Frame get(Frame origfr, String index, String column, String value) {
    Key k1 = Key.<Frame>make();
    Frame fr = origfr.subframe(new String[]{index,column,value}).deepCopy(k1.toString());
    DKV.put(fr);
    int index_colidx = fr.find(index);
    int value_colidx = fr.find(value);
    int _col_colidx = fr.find(column);

    String[] column_set = fr.vec(column).domain();
    long[] indexSet = new VecUtils.CollectDomain().doAll(fr.vec(index)).domain();
    byte vecType = fr.vec(index_colidx).get_type();
    Frame indexSetFr = new Frame(Key.<Frame>make(),new String[]{index},new Vec[]{UniqueVec(vecType,indexSet)});
    Frame res = new Frame(Key.<Frame>make());
    res.add(indexSetFr.deepCopy(indexSetFr._key.toString()));
    DKV.put(indexSetFr);
    for (String _c: column_set) {
      String rapidsString1 = String.format("(cols (rows " + fr._key + " (== (cols " + fr._key + " " + _col_colidx + ") '" + _c + "')) [%d, %d])",
              index_colidx,
              value_colidx);
      Frame frTmp = Rapids.exec(rapidsString1).getFrame();
      frTmp._key = Key.<Frame>make();
      DKV.put(frTmp);
      String rapidsString2 = String.format("(cols (merge " + indexSetFr._key + " " + frTmp._key + " True False [0] [0] 'auto') 1)");
      Frame joinedOnAllIdx = Rapids.exec(rapidsString2).getFrame();
      joinedOnAllIdx.setNames(new String[]{_c});
      res.add(joinedOnAllIdx);
      frTmp.delete();

    }
    indexSetFr.delete();
    fr.delete();
    return res;

  }
  public static Vec UniqueVec(byte vecType,long ...rows) {
     Key<Vec> k = Vec.VectorGroup.VG_LEN1.addVec();
     Futures fs = new Futures();
     AppendableVec avec = new AppendableVec(k,vecType);
     avec.setDomain(null);
     NewChunk chunk = new NewChunk(avec, 0);
     for( long r : rows ) chunk.addNum(r);
     chunk.close(0, fs);
     Vec vec = avec.layout_and_close(fs);
     fs.blockForPending();
     return vec;
  }
}
