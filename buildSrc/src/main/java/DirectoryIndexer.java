import java.io.*;
import java.lang.Runtime;
import java.lang.RuntimeException;
import java.util.ArrayList;

public class DirectoryIndexer {
  private String _dir;
  private ArrayList<String> _files;

  public DirectoryIndexer(String dir) {
    try {
      _dir = new File(dir).getCanonicalPath();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    _files = new ArrayList<String>();

    if (! new File(_dir).isDirectory()) {
      throw new RuntimeException();
    }
  }

  private void list(File file) {
    if (file.isFile()) {
      try {
        _files.add(file.getCanonicalPath());
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    File[] children = file.listFiles();
    if (children == null) {
      return;
    }
    for (File child : children) {
      list(child);
    }
  }

  void emitList(String prefix, String outputFile) {
    list(new File(_dir));
    BufferedWriter out = null;
    try {
      FileWriter fstream = new FileWriter(outputFile);
      out = new BufferedWriter(fstream);
      for (String s : _files) {
        int idx = s.lastIndexOf(prefix);
        String s2 = s.substring(idx);
        out.write(s2 + "\n");
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      if (out != null) {
        try {
          out.close();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}