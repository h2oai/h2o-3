import java.io.File;
import java.io.PrintWriter;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.Runtime;
import java.lang.RuntimeException;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class H2OBuildVersion {
  // Passed in by caller.
  File _rootDir;
  String _versionFromGradle;

  // Calculated.
  String _branch;
  String _buildMajorVersion;
  String _buildMinorVersion;
  String _buildIncrementalVersion;
  String _buildNumber;

  H2OBuildVersion(File rootDir, String versionFromGradle) {
    _rootDir = rootDir;
    _versionFromGradle = versionFromGradle;
    calc();
  }

  private String calcBuildNumber(File rootDir, String versionFromGradle) {
    try {
      String buildNumberFileName = rootDir.toString() + File.separator + "gradle" + File.separator + "buildnumber.properties";
      File f = new File(buildNumberFileName);
      if (!f.exists()) {
        return "99999";
      }

      BufferedReader br = new BufferedReader(new FileReader(buildNumberFileName));
      String line = br.readLine();
      while (line != null) {
        Pattern p = Pattern.compile("BUILD_NUMBER\\s*=\\s*(\\S+)");
        Matcher m = p.matcher(line);
        boolean b = m.matches();
        if (b) {
          br.close();
          String buildNumber = m.group(1);
          return buildNumber;
        }

        line = br.readLine();
      }

      throw new RuntimeException("BUILD_NUMBER property not found in " + buildNumberFileName);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void calc() {
    String majorMinorIncremental;
    if (_versionFromGradle.endsWith("-SNAPSHOT")) {
      majorMinorIncremental = _versionFromGradle.substring(0, _versionFromGradle.length() - "-SNAPSHOT".length());
    }
    else {
      majorMinorIncremental = _versionFromGradle;
    }

    if (! Pattern.matches("\\d+\\.\\d+\\.\\d+", majorMinorIncremental)) {
      throw new RuntimeException("majorMinorIncremental is malformed (" + majorMinorIncremental + ")");
    }

    String[] parts = majorMinorIncremental.split("\\.");
    _buildMajorVersion = parts[0];
    _buildMinorVersion = parts[1];
    _buildIncrementalVersion = parts[2];
    _buildNumber = calcBuildNumber(_rootDir, _versionFromGradle);
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
    return System.getProperty("user.name").replace('\\', '/');
  }

  private String getProjectVersion() {
    String projectVersion = _buildMajorVersion + "." + _buildMinorVersion + "." + _buildIncrementalVersion + "." + _buildNumber;
    return projectVersion;
  }

  //------------------------------------------------------------
  // PUBLIC API BELOW THIS LINE.
  //------------------------------------------------------------

  public String getLastCommitHash() {
    String s = calcLastCommitHash();
    return s;
  }

  public String getBranch() {
    if (_branch == null) {
      _branch = calcBranch();
    }
    return _branch;
  }

  public String getBuildMajorVersion() { return _buildMajorVersion; }
  public String getBuildMinorVersion() { return _buildMinorVersion; }
  public String getBuildIncrementalVersion() { return _buildIncrementalVersion; }
  public String getBuildNumber() { return _buildNumber; }

  public void emitBuildVersionJavaFileIfNecessary(File fileName) {
    try {
      String projectVersion = getProjectVersion();
      String branchName = getBranch();
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

        br.close();
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
