package hex.pipeline.transformers;

import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.MRTask;
import water.Scope;
import water.fvec.*;
import water.junit.rules.ScopeTracker;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.StringUtils;

import static water.TestUtil.ar;
import static water.TestUtil.assertFrameEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class UnionTransformerTest {
  
  @Rule
  public ScopeTracker scope = new ScopeTracker();

  @Test
  public void test_union_transform_append() {
    DataTransformer plus = new StrConcatColumnsTransformer("+");
    DataTransformer minus = new StrConcatColumnsTransformer("-");
      
    DataTransformer union = new UnionTransformer(UnionTransformer.UnionStrategy.append, plus, minus);
    final Frame fr = Scope.track(new TestFrameBuilder()
            .withColNames("one", "two")
            .withVecTypes(Vec.T_STR, Vec.T_STR)
            .withDataForCol(0, ar("un", "eins", "jeden"))
            .withDataForCol(1, ar("deux", "zwei", "dva"))
            .build());
      
    final Frame expected = Scope.track(new TestFrameBuilder()
            .withColNames("one", "two", "one+two", "one-two")
            .withVecTypes(Vec.T_STR, Vec.T_STR, Vec.T_STR, Vec.T_STR)
            .withDataForCol(0, ar("un", "eins", "jeden"))
            .withDataForCol(1, ar("deux", "zwei", "dva"))
            .withDataForCol(2, ar("un+deux", "eins+zwei", "jeden+dva"))
            .withDataForCol(3, ar("un-deux", "eins-zwei", "jeden-dva"))
            .build());

    Frame transformed = Scope.track(union.transform(fr));
    assertFrameEquals(expected, transformed, 0);
  }
    
  @Test
  public void test_union_transform_replace() {
    DataTransformer plus = new StrConcatColumnsTransformer("+");
    DataTransformer minus = new StrConcatColumnsTransformer("-");

    DataTransformer union = new UnionTransformer(UnionTransformer.UnionStrategy.replace, plus, minus);
    final Frame fr = Scope.track(new TestFrameBuilder()
            .withColNames("one", "two")
            .withVecTypes(Vec.T_STR, Vec.T_STR)
            .withDataForCol(0, ar("un", "eins", "jeden"))
            .withDataForCol(1, ar("deux", "zwei", "dva"))
            .build());

    final Frame expected = Scope.track(new TestFrameBuilder()
            .withColNames("one+two", "one-two")
            .withVecTypes(Vec.T_STR, Vec.T_STR)
            .withDataForCol(0, ar("un+deux", "eins+zwei", "jeden+dva"))
            .withDataForCol(1, ar("un-deux", "eins-zwei", "jeden-dva"))
            .build());

    Frame transformed = Scope.track(union.transform(fr));
    assertFrameEquals(expected, transformed, 0);
  }
  
  public static class StrConcatColumnsTransformer extends DataTransformer<StrConcatColumnsTransformer> {

    private String separator;

    public StrConcatColumnsTransformer(String separator) {
      this.separator = separator;
    }

    @Override
    protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
      return new VecsConcatenizer(separator).concat(fr);
    }

    public static class VecsConcatenizer extends MRTask<VecsConcatenizer> {
      private final String _sep;

      public VecsConcatenizer(String separator) {
        _sep = separator;
      }

      @Override
      public void map(Chunk[] cs, NewChunk nc) {
        for (int row = 0; row < cs[0]._len; row++) {
          StringBuilder tmpStr = new StringBuilder();
          for (int col = 0; col < cs.length; col++) {
            Chunk chk = cs[col];
            if (chk.isNA(row)) continue;
            String s = chk.stringAt(row);
            tmpStr.append(s);
            if (col+1 < cs.length) tmpStr.append(_sep);
          }
          nc.addStr(tmpStr.toString());
        }
      }
      
      public Frame concat(Frame fr) {
        String name = StringUtils.join(_sep, fr.names());
        return doAll(Vec.T_STR, fr).outputFrame(new String[]{name}, null);
      }
    }
  }
  
}
