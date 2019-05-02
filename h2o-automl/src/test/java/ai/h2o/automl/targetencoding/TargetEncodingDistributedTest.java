package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
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
        long levelValue = cs[0].at8(0);

        System.out.println("Level value: " + levelValue);
        System.out.println("Domain: " + Arrays.toString(cs[0].vec().domain()));
        int length = cs[0].vec().domain().length;
        System.out.println("Domain length: " + length);
        cs[0].vec().factor(2); // should throw exception on one of the nodes
      }
    }.doAll(res);
    
    res.delete();
  }

  @Test
  public void setDomainTest() {
    String teColumnName = "ColA";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName)
            .withVecTypes(Vec.T_CAT)
            .withDataForCol(0, ar("a","a","b", "b", "b"))
            .withChunkLayout(3,2)
            .build();

    final String[] domain = {"a", "b", "imputed_cat"};
    fr.vec(teColumnName).setDomain(domain);
    
    // Note: cardinality is correct
    assertEquals(3, fr.vec(teColumnName).cardinality()); 

    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (int i = 0; i < cs[0].len(); i++) {
          long levelValue = cs[0].at8(i);

          System.out.println("Level value: " + i + ")" + levelValue);

          System.out.println("Domain: " + Arrays.toString(cs[0].vec().domain()));
          if(cs[0].vec().domain().length == 2) throw new IllegalStateException("One of the nodes does not know about updated domain");
        }
      }
    }.doAll(fr);
  }


  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

}
