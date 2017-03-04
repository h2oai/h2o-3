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
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String index = stk.track(asts[2].exec(env)).getStr();
    String column = stk.track(asts[3].exec(env)).getStr();
    String value = stk.track(asts[4].exec(env)).getStr();
    Key k1 = Key.<Frame>make();
    fr._key = k1;
    DKV.put(fr);
    int indexIdx = fr.find(index);
    int valueIdx = fr.find(value);
    int colIdx = fr.find(column);

    String[] column_set = fr.vec(column).domain();
    Frame indexSetFr = Rapids.exec("(sort (unique (cols " + fr._key + " " + indexIdx + ")) 0)").getFrame();
    indexSetFr._key = Key.<Frame>make();
    Frame res = new Frame(Key.<Frame>make());
    res.add(indexSetFr);
    DKV.put(indexSetFr);
    for (String c: column_set) {
      String rapidsString1 = String.format("(cols (rows " + fr._key + " (== (cols " + fr._key + " " + colIdx + ") '" + c + "')) [%d, %d])",
              indexIdx,
              valueIdx);
      Frame frTmp = Rapids.exec(rapidsString1).getFrame();
      frTmp._key = Key.<Frame>make();
      DKV.put(frTmp);
      String rapidsString2 = String.format("(cols (merge " + indexSetFr._key + " " + frTmp._key + " True False [0] [0] 'auto') 1)");
      Frame joinedOnAllIdx = Rapids.exec(rapidsString2).getFrame();
      joinedOnAllIdx.setNames(new String[]{c});
      res.add(joinedOnAllIdx);
      frTmp.delete();

    }
    DKV.remove(indexSetFr._key);
    DKV.remove(k1);

    return new ValFrame(res);

  }
}
