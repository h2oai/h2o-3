package water.rapids.ast.prims.mungers;

import water.*;
import water.fvec.*;
import water.rapids.Rapids;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.VecUtils;

public class AstPivot extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "index", "column", "value"}; //the array and name of columns
  }

  @Override
  public int nargs() {
    return 1 + 4;
  } // (pivot ary index column value)

  @Override
  public String str() {
    return "pivot";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame origfr = stk.track(asts[1].exec(env)).getFrame();
    String _index = stk.track(asts[2].exec(env)).getStr();
    String _column = stk.track(asts[3].exec(env)).getStr();
    String _value = stk.track(asts[4].exec(env)).getStr();
    Key k1 = Key.<Frame>make();
    Frame fr = origfr.subframe(new String[]{_index,_column,_value}).deepCopy(k1.toString());
    DKV.put(fr);
    int _index_colidx = fr.find(_index);
    int _value_colidx = fr.find(_value);
    int _col_colidx = fr.find(_column);

    String[] _column_set = fr.vec(_column).domain();
    long[] _indexSet = new VecUtils.CollectDomain().doAll(fr.vec(_index)).domain();
    byte vecType = fr.vec(_index_colidx).get_type();
    Frame _indexSetFr = new Frame(Key.<Frame>make(),new String[]{_index},new Vec[]{UniqueVec(vecType,_indexSet)});
    Frame res = new Frame(Key.<Frame>make());
    res.add(_indexSetFr.deepCopy(_indexSetFr._key.toString()));
    DKV.put(_indexSetFr);
    for (String _c: _column_set) {
      String rapidsString1 = String.format("(cols (rows " + fr._key + " (== (cols " + fr._key + " " + _col_colidx + ") '" + _c + "')) [%d, %d])",
              _index_colidx,
              _value_colidx);
      Frame frTmp = Rapids.exec(rapidsString1).getFrame();
      frTmp._key = Key.<Frame>make();
      DKV.put(frTmp);
      String rapidsString2 = String.format("(cols (merge " + _indexSetFr._key + " " + frTmp._key + " True False [0] [0] 'auto') 1)");
      Frame joinedOnAllIdx = Rapids.exec(rapidsString2).getFrame();
      joinedOnAllIdx.setNames(new String[]{_c});
      res.add(joinedOnAllIdx);
      frTmp.delete();

    }
    _indexSetFr.delete();
    fr.delete();
    return new ValFrame(res);

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
