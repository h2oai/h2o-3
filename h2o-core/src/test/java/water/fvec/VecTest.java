package water.fvec;

import static org.junit.Assert.assertTrue;
import org.testng.AssertJUnit;

import org.testng.annotations.Test;
import water.Futures;
import water.TestUtil;
import static water.fvec.Vec.makeConSeq;
import static water.fvec.Vec.makeSeq;

/** This test tests stability of Vec API. */
public class VecTest extends TestUtil {

  /** Test toEnum call to return correct domain. */
  @Test public void testToEnum() {
    testToEnumDomainMatch(vec(0,1,0,1), ar("0", "1") );
    testToEnumDomainMatch(vec(1,2,3,4,5,6,7), ar("1", "2", "3", "4", "5", "6", "7") );
    testToEnumDomainMatch(vec(-1,0,1,2,3,4,5,6), ar("-1", "0", "1", "2", "3", "4", "5", "6") );
  }

  private void testToEnumDomainMatch(Vec f, String[] expectedDomain) {
    Vec ef = null;
    try {
      ef = f.toEnum();
      String[] actualDomain = ef.domain();
      AssertJUnit.assertArrayEquals("toEnum call returns wrong domain!", expectedDomain, actualDomain);
    } finally {
      if( f !=null ) f .remove();
      if( ef!=null ) ef.remove();
    }
  }

  @Test public void testMakeConSeq() {
    Vec v;

    v = makeConSeq(0xCAFE,Vec.CHUNK_SZ);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v._espc.length == 2);
    assertTrue(
            v._espc[0] == 0              &&
            v._espc[1] == Vec.CHUNK_SZ
    );
    v.remove(new Futures()).blockForPending();

    v = makeConSeq(0xCAFE,2*Vec.CHUNK_SZ);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(2*Vec.CHUNK_SZ-1) == 0xCAFE);
    assertTrue(v._espc.length == 3);
    assertTrue(
            v._espc[0] == 0              &&
            v._espc[1] == Vec.CHUNK_SZ   &&
            v._espc[2] == Vec.CHUNK_SZ*2
    );
    v.remove(new Futures()).blockForPending();

    v = makeConSeq(0xCAFE,2*Vec.CHUNK_SZ+1);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(2*Vec.CHUNK_SZ) == 0xCAFE);
    assertTrue(v._espc.length == 4);
    assertTrue(
            v._espc[0] == 0              &&
            v._espc[1] == Vec.CHUNK_SZ   &&
            v._espc[2] == Vec.CHUNK_SZ*2 &&
            v._espc[3] == Vec.CHUNK_SZ*2+1
    );
    v.remove(new Futures()).blockForPending();

    v = makeConSeq(0xCAFE,3*Vec.CHUNK_SZ);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(3*Vec.CHUNK_SZ-1) == 0xCAFE);
    assertTrue(v._espc.length == 4);
    assertTrue(
            v._espc[0] == 0              &&
            v._espc[1] == Vec.CHUNK_SZ   &&
            v._espc[2] == Vec.CHUNK_SZ*2 &&
            v._espc[3] == Vec.CHUNK_SZ*3
    );
    v.remove(new Futures()).blockForPending();
  }

  @Test public void testMakeSeq() {
    Vec v = makeSeq(3*Vec.CHUNK_SZ);
    assertTrue(v.at(0) == 1);
    assertTrue(v.at(234) == 235);
    assertTrue(v.at(2*Vec.CHUNK_SZ) == 2*Vec.CHUNK_SZ+1);
    assertTrue(v._espc.length == 4);
    assertTrue(
            v._espc[0] == 0 &&
            v._espc[1] == Vec.CHUNK_SZ &&
            v._espc[2] == Vec.CHUNK_SZ * 2 &&
            v._espc[3] == Vec.CHUNK_SZ * 3
    );
    v.remove(new Futures()).blockForPending();
  }
}
