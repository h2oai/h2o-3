package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Futures;
import water.H2O;
import water.TestUtil;

import static org.junit.Assert.assertTrue;
import static water.fvec.Vec.makeCon;
import static water.fvec.Vec.makeSeq;

/** This test tests stability of Vec API. */
public class VecTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  /** Test toEnum call to return correct domain. */
  @Test public void testToEnum() {
    testToEnumDomainMatch(vec(0,1,0,1), ar("0", "1") );
    testToEnumDomainMatch(vec(1,2,3,4,5,6,7), ar("1", "2", "3", "4", "5", "6", "7") );
    testToEnumDomainMatch(vec(0,1,2,99,4,5,6), ar("0", "1", "2", "4", "5", "6", "99") );
  }

  private void testToEnumDomainMatch(Vec f, String[] expectedDomain) {
    Vec ef = null;
    try {
      ef = f.toEnum();
      String[] actualDomain = ef.domain();
      Assert.assertArrayEquals("toEnum call returns wrong domain!", expectedDomain, actualDomain);
    } finally {
      if( f !=null ) f .remove();
      if( ef!=null ) ef.remove();
    }
  }

  @Test public void testMakeConSeq() {
    Vec v;
    int chks1 = Math.min( 4 * H2O.NUMCPUS * H2O.CLOUD.size(), 2*FileVec.DFLT_CHUNK_SIZE);
    v = makeCon(0xCAFE,2*FileVec.DFLT_CHUNK_SIZE);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v._espc.length == chks1+1);
    v.remove(new Futures()).blockForPending();

    chks1 = Math.min( 4 * H2O.NUMCPUS * H2O.CLOUD.size(), 3*FileVec.DFLT_CHUNK_SIZE);
    v = makeCon(0xCAFE,3*FileVec.DFLT_CHUNK_SIZE);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(3*FileVec.DFLT_CHUNK_SIZE-1) == 0xCAFE);
    assertTrue(v._espc.length == chks1+1);
    v.remove(new Futures()).blockForPending();

    chks1 = Math.min( 4 * H2O.NUMCPUS * H2O.CLOUD.size(), 3*FileVec.DFLT_CHUNK_SIZE+1);
    v = makeCon(0xCAFE,3*FileVec.DFLT_CHUNK_SIZE+1);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(3*FileVec.DFLT_CHUNK_SIZE) == 0xCAFE);
    assertTrue(v._espc.length == chks1+1);
    v.remove(new Futures()).blockForPending();

    chks1 = Math.min( 4 * H2O.NUMCPUS * H2O.CLOUD.size(), 4*FileVec.DFLT_CHUNK_SIZE);
    v = makeCon(0xCAFE,4*FileVec.DFLT_CHUNK_SIZE);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(4*FileVec.DFLT_CHUNK_SIZE-1) == 0xCAFE);
    assertTrue(v._espc.length == chks1+1);
    v.remove(new Futures()).blockForPending();
  }

  @Test public void testMakeSeq() {
    int chks1 = Math.min( 4 * H2O.NUMCPUS * H2O.CLOUD.size(), 3*FileVec.DFLT_CHUNK_SIZE);
    Vec v = makeSeq(3*FileVec.DFLT_CHUNK_SIZE);
    assertTrue(v.at(0) == 1);
    assertTrue(v.at(234) == 235);
    assertTrue(v.at(2*FileVec.DFLT_CHUNK_SIZE) == 2*FileVec.DFLT_CHUNK_SIZE+1);
    assertTrue(v._espc.length == chks1+1);
    v.remove(new Futures()).blockForPending();
  }
}
