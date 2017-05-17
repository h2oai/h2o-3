package water.fvec;

import org.junit.*;
import water.util.FileUtils;

import java.io.*;

public class WordCountBigTest extends WordCountTest {
  @Test public void testWordCountWiki() throws IOException {
    String best = "/home/0xdiag/datasets/wiki.xml";
    File file = FileUtils.locateFile(best);
    if( file==null ) file = FileUtils.getFile("../datasets/Wiki_20130805.xml");
    doWordCount(file);
  }

  @Test public void testWordCount() throws IOException {
    // Do nothing; in particular, don't run inherited testWordCount again.
  }
}
