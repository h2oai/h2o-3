package water.genmodel;

abstract public class AbstractBuildVersion {

  abstract public String branchName();
  abstract public String lastCommitHash();
  abstract public String describe();
  abstract public String projectVersion();
  abstract public String compiledOn();
  abstract public String compiledBy();
  @Override public String toString() {
    return "H2O-GENMODEL v"+projectVersion()+ " ("+branchName()+" - "+lastCommitHash()+")";
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

  private String getValue(String name) {
    switch (name) {
      case "projectVersion":
        return projectVersion();
      case "lastCommitHash":
        return lastCommitHash();
      case "describe":
        return describe();
      case "compiledOn":
        return compiledOn();
      case "compiledBy":
        return compiledBy();
      case "branchName":
        return branchName();
      default:
        return null;
    }
  }
  
  public static AbstractBuildVersion getBuildVersion() {
    AbstractBuildVersion abv = AbstractBuildVersion.UNKNOWN_VERSION;
    try {
      Class klass = Class.forName("water.genmodel.BuildVersion");
      java.lang.reflect.Constructor constructor = klass.getConstructor();
      abv = (AbstractBuildVersion) constructor.newInstance();
    } catch (Exception ignore) { }
    return abv;
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      args = new String[]{"projectVersion"};
    }
    AbstractBuildVersion buildVersion = getBuildVersion();
    System.out.print(buildVersion.getValue(args[0]));
    for (int i = 1; i < args.length; i++) {
      System.out.print(' ');
      System.out.print(buildVersion.getValue(args[i]));
    }
    System.out.println();
  }

}
