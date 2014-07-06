package water.fvec;

import org.testng.annotations.*;

import java.io.*;

public class WordCountBigTest extends WordCountTest {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Test(groups={"NOPASS"}) public void testWordCountWiki() throws IOException {
    String best = "/home/0xdiag/datasets/wiki.xml";
    File file = find_test_file(best);
    if( file==null ) file = find_test_file("../datasets/Wiki_20130805.xml");
    if( file==null ) throw new FileNotFoundException(best);
    doWordCount(file);
  }

  @Test public void testWordCount() throws IOException {
    // Do nothing; in particular, don't run inherited testWordCount again.
  }
}
