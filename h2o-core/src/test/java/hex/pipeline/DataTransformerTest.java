package hex.pipeline;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.MRTask;
import water.Scope;
import water.fvec.*;
import water.logging.Logger;
import water.logging.LoggerFactory;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class DataTransformerTest {
  
  @Test
  public void test_transform() {
    try {
      Scope.enter();
      DataTransformer dt = new AddRandomColumnTransformer("foo");
      final Frame fr = Scope.track(new TestFrameBuilder()
              .withColNames("one", "two", "target")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, 2, 3))
              .withDataForCol(1, ard(1, 2, 3))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .build());

      Frame transformed = Scope.track(dt.transform(fr));
      assertEquals(4, transformed.names().length);
      assertArrayEquals(new String[]{"one", "two", "target", "foo"}, transformed.names());
    } finally {
      Scope.exit();
    }
  }
  
  public static class MultiplyNumericColumnTransformer extends DataTransformer<MultiplyNumericColumnTransformer> {
    
    private final String colName;
    
    private final int multiplier;

    public MultiplyNumericColumnTransformer(String colName, int multiplier) {
      this.colName = colName;
      this.multiplier = multiplier;
    }

    @Override
    protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
      Frame tr = new Frame(fr);
      final int colIdx = tr.find(colName);
      Vec col = tr.vec(colIdx);
      assert col.isNumeric();
      Vec multCol = new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
          for (int i = 0; i < cs[0]._len; i++)
            ncs[0].addNum(multiplier * (cs[0].atd(i)));
        }
      }.doAll(Vec.T_NUM, new Frame(col)).outputFrame().vec(0);
      tr.replace(colIdx, multCol);
      return tr;
    }
  }
  
  
  public static class AddRandomColumnTransformer extends DataTransformer<AddRandomColumnTransformer> {
    
    private final String colName;
    private final long seed;

    public AddRandomColumnTransformer(String colName) {
      this(colName, 0);
    }

    public AddRandomColumnTransformer(String colName, long seed) {
      this.colName = colName;
      this.seed = seed;
    }

    @Override
    protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
      Frame tr = new Frame(fr);
      tr.add(colName, tr.anyVec().makeRand(seed));
      return tr;
    }
  }
  
  public static class AddDummyCVColumnTransformer extends DataTransformer<AddDummyCVColumnTransformer> {
    
    private final String colName;
    private final int vecType;

    public AddDummyCVColumnTransformer(String colName) {
      this(colName, Vec.T_NUM);
    }
    
    public AddDummyCVColumnTransformer(String colName, int vecType) {
      this.colName = colName;
      this.vecType = vecType;
    }

    @Override
    public boolean isCVSensitive() {
      return true;
    }

    @Override
    protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
      if (type == FrameType.Training && context._params._is_cv_model) {
        Frame tr = new Frame(fr);
        Vec v = Vec.makeRepSeq(tr.anyVec().length(), context._params._cv_fold+2);
        Vec tmp;
        switch (vecType) {
          case Vec.T_CAT:
            tmp = v.toCategoricalVec(); v.remove(); v = tmp; break;
          case Vec.T_STR:
            tmp = v.toStringVec(); v.remove(); v = tmp; break;
          case Vec.T_NUM:
          default:
            //already numeric by construct
            break;
        }
        tr.add(colName, v);
        return tr;
      }
      return fr;
    }
  }
  
  public static class RenameFrameTransformer extends DataTransformer<RenameFrameTransformer> {
    
    private final String frameName;

    public RenameFrameTransformer(String frameName) {
      this.frameName = frameName;
    }

    @Override
    protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
      return new Frame(Key.make(frameName), fr.names(), fr.vecs());
    }
  }
  
  public static class FrameTrackerAsTransformer extends DataTransformer<FrameTrackerAsTransformer> {
    
    static final Logger logger = LoggerFactory.getLogger(FrameTrackerAsTransformer.class);
    
    public static class Transformation {
      final String frameId;
      final FrameType type;
      final boolean is_cv;

      public Transformation(String frameId, FrameType type, boolean is_cv) {
        this.frameId = frameId;
        this.type = type;
        this.is_cv = is_cv;
      }

      @Override
      public String toString() {
        final StringBuilder sb = new StringBuilder("Transformation{");
        sb.append("frameId='").append(frameId).append('\'');
        sb.append(", type=").append(type);
        sb.append(", is_cv=").append(is_cv);
        sb.append('}');
        return sb.toString();
      }
    }
    
    transient List<Transformation> transformations = new ArrayList<>();

    @Override
    protected void doPrepare(PipelineContext context) {
      transformations.clear();
    }

    @Override
    protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
      if (fr == null) return null;
      logger.info(fr.getKey()+": columns="+Arrays.toString(fr.names()));
      boolean is_cv = context != null && context._params._is_cv_model;
      transformations.add(new Transformation(fr.getKey().toString(), type, is_cv));
      return fr;
    }
  }
  
  @FunctionalInterface 
  interface FrameChecker {
    void check(Frame fr);
  }
  
  public static class FrameCheckerAsTransformer extends DataTransformer<FrameTrackerAsTransformer> {

    final transient FrameChecker checker;

    public FrameCheckerAsTransformer(FrameChecker checker) {
      this.checker = checker;
    }

    @Override
    protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
      checker.check(fr);
      return fr;
    }
  }
}
