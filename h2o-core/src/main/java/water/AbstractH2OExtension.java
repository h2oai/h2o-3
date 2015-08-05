package water;

import water.init.AbstractBuildVersion;
import water.util.Log;

@SuppressWarnings("unused")
public abstract class AbstractH2OExtension {
  /**
   * @return The name of this extension.
   */
  public abstract String getExtensionName();

  /**
   * Any up-front initialization that needs to happen before H2O is started.
   * This is called in H2OApp before H2O.main() is called.
   */
  public void init() {}

  /**
   * Print stuff for
   * java -jar h2o.jar -help
   */
  public void printHelp() {}

  /**
   * To be called by parseArguments() on a failure.
   * @param message Message to give to the user.
   */
  public static void parseFailed(String message) {
    H2O.parseFailed(message);
  }

  /**
   * Parse arguments used by this extension.
   * Call parseFailed() above on a failure, which will exit H2O.
   *
   * @param args List of arguments this extension might want to consume.
   * @return Modified list with the ones consumed by this extension removed.
   */
  public String[] parseArguments(String[] args) {
    return args;
  }

  /**
   * Validate arguments used by this extension.
   */
  public void validateArguments() {}

  /**
   * Get extension-specific build information.
   *
   * @return build information.
   */
  public abstract AbstractBuildVersion getBuildVersion();

  /**
   * Print a short message when the extension finishes initializing.
   */
  public void printInitialized() {
    Log.info(getExtensionName() + " extension initialized");
  }
}
