package water.init;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

abstract public class AbstractBuildVersion {

  // A date format use for compiledOn field
  static String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
  // Threshold to identify too old version
  static int TOO_OLD_THRESHOLD = 100 /* days */;
  // A link to the latest version identifier
  static String LATEST_STABLE_URL = " http://h2o-release.s3.amazonaws.com/h2o/latest_stable";
  // Pattern to extract version from URL
  static Pattern VERSION_EXTRACT_PATTERN = Pattern.compile(".*h2o-(.*).zip");
  // Devel version has a specific patch number X.Y.Z.99999
  public static String DEVEL_VERSION_PATCH_NUMBER = "99999";

  abstract public String branchName();
  abstract public String lastCommitHash();
  abstract public String describe();
  abstract public String projectVersion();
  abstract public String compiledOn();
  abstract public String compiledBy();
  @Override public String toString() {
    return "H2O v"+projectVersion()+ " ("+branchName()+" - "+lastCommitHash()+")";
  }

  public String buildNumber() {
    String pv = projectVersion();
    if (pv.equals(UNKNOWN_VERSION_MARKER)) {
      return UNKNOWN_VERSION_MARKER;
    }

    String[] split_pv = pv.split("\\.");
    String bn = split_pv[split_pv.length-1];
    return(bn);
  }

  /** Returns compile date for this H2O version or null. */
  public final Date compiledOnDate() {
    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
    try {
      return dateFormat.parse(compiledOn());
    } catch (ParseException e) {
      return null;
    }
  }

  public final boolean isTooOld() {
    Date compileTime = compiledOnDate();
    if (compileTime == null) return false;
    long timeDiff = System.currentTimeMillis() - compileTime.getTime();
    long days = timeDiff / (24*60*60*1000L) /* msec per day */;
    return days > TOO_OLD_THRESHOLD;
  }

  public boolean isDevVersion() {
    return projectVersion().equals(UNKNOWN_VERSION_MARKER) || projectVersion().endsWith(DEVEL_VERSION_PATCH_NUMBER);
  }

  /** Dummy version of H2O. */
  private static final String UNKNOWN_VERSION_MARKER = "(unknown)";
  public static final AbstractBuildVersion UNKNOWN_VERSION = new AbstractBuildVersion() {
      @Override public String projectVersion() { return UNKNOWN_VERSION_MARKER; }
      @Override public String lastCommitHash() { return UNKNOWN_VERSION_MARKER; }
      @Override public String describe()   { return UNKNOWN_VERSION_MARKER; }
      @Override public String compiledOn() { return UNKNOWN_VERSION_MARKER; }
      @Override public String compiledBy() { return UNKNOWN_VERSION_MARKER; }
      @Override public String branchName() { return UNKNOWN_VERSION_MARKER; }
    };
}
