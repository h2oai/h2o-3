package water.fvec;

import java.io.*;
import org.junit.*;

public class WordCountBigTest extends WordCountTest {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }
  @Test public void testWordCountWiki() throws IOException {
    String best = "/home/0xdiag/datasets/wiki.xml";
    File file = find_test_file(best);
    if( file==null ) file = find_test_file("../wiki/enwiki-latest-pages-articles.xml");
    if( file==null ) throw new FileNotFoundException(best);
    doWordCount(file);
  }
}
