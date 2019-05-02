package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Lockable;
import water.MRTask;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TargetEncodingDistributedTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(2);
  }

  private Frame fr = null;

  @Test
  public void imputeNAsForColumnTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColB";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", null, null, null))
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .withChunkLayout(3,2)
            .build();

    String nullStr = null;
    fr.vec(0).set(2, nullStr);

    String[] teColumns = {""};
    TargetEncoder tec = new TargetEncoder(teColumns);

    assertTrue(fr.vec("ColA").isCategorical());
    assertEquals(2, fr.vec("ColA").cardinality());

    Frame res = tec.imputeNAsForColumn(fr, "ColA", "ColA_NA");

    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (int i = 0; i < cs[0].len(); i++) {
          long levelValue = cs[0].at8(i);

          System.out.println("Level value: " + levelValue);
          System.out.println("Domain: " + Arrays.toString(cs[0].vec().domain()));
          int length = cs[0].vec().domain().length;
          System.out.println("Domain length: " + length);
          cs[0].vec().factor(2);
        }
      }
    }.doAll(res);
    
    // assumption is that domain is being properly distributed over nodes 
    // and there will be no exception while attempting to access new domain's value in `cs[0].vec().factor(2);`
    
    res.delete();
  }

  @Test
  public void setDomainTest() {
    String teColumnName = "ColA";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName)
            .withVecTypes(Vec.T_CAT)
            .withDataForCol(0, ar("a","a","b", "b", "b")) //  here it is 2 `a` and 3 `b` but layout is not perfectly aligned
            .withChunkLayout(3,2)
            .build();

    final String[] domain = {"a", "b", "imputed_cat"};

    Lockable lock = fr.write_lock();
    Vec updatedVec = fr.vec(teColumnName);
    updatedVec.setDomain(domain);
    DKV.put(updatedVec);
    fr.update();
    lock.unlock();
    
    assertEquals(3, fr.vec(teColumnName).cardinality());

    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (int i = 0; i < cs[0].len(); i++) {
          long levelValue = cs[0].at8(i);

          System.out.println("Level value: " + levelValue);

          System.out.println("Domain: " + Arrays.toString(cs[0].vec().domain()));
          System.out.println("Domain length: " + cs[0].vec().domain().length);
          cs[0].vec().factor(2);
        }
      }
    }.doAll(fr);
  }

  // assumption is that domain is being properly distributed over nodes 
  // and there will be no exception while attempting to access new domain's value in `cs[0].vec().factor(2);`

  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

}
