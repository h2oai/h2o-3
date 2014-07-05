package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.*;

import water.TestUtil;

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
}
