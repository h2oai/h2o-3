package water;

import org.reflections.Reflections;
import water.util.Log;

import java.lang.reflect.Modifier;

public class H2OApp {
  public static void main(String[] args) {
    driver(args, System.getProperty("user.dir"));
  }

  @SuppressWarnings("unused")
  public static void main2(String relativeResourcePath) {
    driver(new String[0], relativeResourcePath);
  }

  private static void driver(String[] args, String relativeResourcePath) {
    // Fire up the H2O Cluster
    H2O.main(args);

    // Register REST API
    register(relativeResourcePath);
    H2O.finalizeRegistration();
  }

  // Be paranoid and check that this doesn't happen twice.
  private static boolean apisRegistered = false;

  /**
   * Register REST API routes.
   *
   * Use reflection to find all classes that inherit from water.api.AbstractRegister
   * and call the register() method for each.
   *
   * @param relativeResourcePath Relative path from running process working dir to find web resources.
   */
  public static void register(String relativeResourcePath) {
    if (apisRegistered) {
      throw H2O.fail("APIs already registered");
    }

    long before = System.currentTimeMillis();

    // Disallow schemas whose parent is in another package because it takes ~4s to do the getSubTypesOf call.
    String[] packages = new String[] { "water", "hex" };

    for (String pkg : packages) {
      Reflections reflections = new Reflections(pkg);
      Log.debug("Registering REST APIs for package: " + pkg);
      for (Class registerClass : reflections.getSubTypesOf(water.api.AbstractRegister.class)) {
        if (!Modifier.isAbstract(registerClass.getModifiers())) {
          try {
            Log.debug("Found REST API registration for class: " + registerClass.getName());
            Object instance = registerClass.newInstance();
            water.api.AbstractRegister r = (water.api.AbstractRegister) instance;
            r.register(relativeResourcePath);
          }
          catch (Exception e) {
            throw H2O.fail(e.toString());
          }
        }
      }
    }

    apisRegistered = true;
    Log.info("Registered REST APIs in: " + (System.currentTimeMillis() - before) + "mS");
  }
}
