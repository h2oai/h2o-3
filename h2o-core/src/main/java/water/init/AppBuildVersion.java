package water.init;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Class that reads H2O-3 version information from a property file generated at buildtime.
 */
public class AppBuildVersion extends AbstractBuildVersion {
  private static final String UNKNOWN_VERSION_MARKER = "(unknown)";
  private final Properties properties;

  private AppBuildVersion(Properties properties) {
    this.properties = properties;
  }

  public AppBuildVersion() throws IOException {
    this(loadProperties("META-INF/h2o.build.properties"));
  }

  private static Properties loadProperties(String resource) throws IOException {
    final Properties buildProperties = new Properties();
    try(InputStream is = AppBuildVersion.class.getResourceAsStream(resource)) {
      buildProperties.load(is);
    }
    return buildProperties;
  }

  @Override
  public String branchName() {
    return properties.getProperty("git.branch", UNKNOWN_VERSION_MARKER);
  }

  @Override
  public String lastCommitHash() {
    return properties.getProperty("git.commit.hash", UNKNOWN_VERSION_MARKER);
  }

  @Override
  public String describe() {
    return properties.getProperty("description", UNKNOWN_VERSION_MARKER);
  }

  @Override
  public String projectVersion() {
    return properties.getProperty("project.version", UNKNOWN_VERSION_MARKER);
  }

  @Override
  public String compiledOn() {
    return properties.getProperty("compiled.on", UNKNOWN_VERSION_MARKER);
  }

  @Override
  public String compiledBy() {
    return properties.getProperty("compiled.by", UNKNOWN_VERSION_MARKER);
  }
}
