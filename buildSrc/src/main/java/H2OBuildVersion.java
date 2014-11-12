import java.io.File;
import java.io.PrintWriter;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class H2OBuildVersion {
  File _rootDir;

  H2OBuildVersion(File rootDir) {
    _rootDir = rootDir;
  }

  private String calcBranch() {
    try {
      Process p = new ProcessBuilder("git", "branch").start();
      p.waitFor();
      BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String line = br.readLine();
      while (line != null) {
        if (!line.startsWith("*")) {
          line = br.readLine();
          continue;
        }

        String branch = line.substring(2);
        return branch;
      }
      return "(unknown)";
    }
    catch (Exception e) {
      return "(unknown)";
    }
  }

  private String calcLastCommitHash() {
    try {
      Process p = new ProcessBuilder("git", "log", "-1", "--format=%H").start();
      p.waitFor();
      BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String line = br.readLine();
      return line;
    }
    catch (Exception e) {
      return "(unknown)";
    }
  }

  private String calcDescribe() {
    try {
      Process p = new ProcessBuilder("git", "describe", "--always", "--dirty").start();
      p.waitFor();
      BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String line = br.readLine();
      return line;
    }
    catch (Exception e) {
      return "(unknown)";
    }
  }

  private String calcCompiledOn() {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    Date date = new Date();
    String s = dateFormat.format(date);
    return s;
  }

  private String calcCompiledBy() {
    return System.getProperty("user.name");
  }

  void emitBuildVersionJavaFileIfNecessary(File fileName, String projectVersion) {
    try {
      String branchName = calcBranch();
      String lastCommitHash = calcLastCommitHash();
      String describe = calcDescribe();
      String compiledOn = calcCompiledOn();
      String compiledBy = calcCompiledBy();

      boolean needToEmit = false;

      if (! fileName.exists()) {
        System.out.print("NOTE: emitBuildVersionJava found no file, emitting new file");
        needToEmit = true;
      }

      int found = 0;
      if (! needToEmit) {
        final String[][] stuffToCheck = {
                {"branchName", branchName},
                {"lastCommitHash", lastCommitHash},
                {"projectVersion", projectVersion}
        };

        BufferedReader br = new BufferedReader(new FileReader(fileName));
        String line = br.readLine();

        while ((! needToEmit) && (line != null)) {
          for (String[] feature : stuffToCheck) {
            String name = feature[0];
            String value = feature[1];
            Pattern p = Pattern.compile(".*" + name + ".*\"(.*)\";.*");
            Matcher m = p.matcher(line);
            boolean b = m.matches();
            if (b) {
              found++;
              String v = m.group(1);
              if (!value.equals(v)) {
                System.out.print("NOTE: emitBuildVersionJava found a mismatch, emitting new file (" + value + " not equal " + v + ")");
                needToEmit = true;
                break;
              }
            }
          }

          line = br.readLine();
        }
      }

      if ((! needToEmit) && (found != 3)) {
        System.out.print("NOTE: emitBuildVersionJava found too few things to check, emitting new file");
        needToEmit = true;
      }

      if (! needToEmit) {
        System.out.print("NOTE: emitBuildVersionJava found a match, nothing to do");
        return;
      }

      PrintWriter writer = new PrintWriter(fileName);
      writer.println("package water.init;");
      writer.println("");
      writer.println("public class BuildVersion extends AbstractBuildVersion {");
      writer.println("    public String branchName()     { return \"" + branchName     + "\"; }");
      writer.println("    public String lastCommitHash() { return \"" + lastCommitHash + "\"; }");
      writer.println("    public String describe()       { return \"" + describe       + "\"; }");
      writer.println("    public String projectVersion() { return \"" + projectVersion + "\"; }");
      writer.println("    public String compiledOn()     { return \"" + compiledOn     + "\"; }");
      writer.println("    public String compiledBy()     { return \"" + compiledBy     + "\"; }");
      writer.println("}");
      writer.close();
    }
    catch (Exception e) {
      System.out.println("");
      System.out.println("ERROR:  H2OBuildVersion emitBuildVersionJavaFileIfNecessary failed");
      System.out.println("");
      System.out.println(e);
      System.out.println("");
      System.exit(1);
    }
  }
}
