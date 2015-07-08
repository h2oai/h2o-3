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
    // Register H2O Extensions
    registerExtensions();

    // Fire up the H2O Cluster
    H2O.main(args);

    // Register REST API
    registerRestApis(relativeResourcePath);
    H2O.finalizeRegistration();
  }


  // Be paranoid and check that this doesn't happen twice.
  private static boolean extensionsRegistered = false;
  private static long registerExtensionsMillis = 0;

  /**
   * Register H2O extensions.
   * <p/>
   * Use reflection to find all classes that inherit from water.AbstractH2OExtension
   * and call H2O.addExtension() for each.
   */
  public static void registerExtensions() {
    if (extensionsRegistered) {
      throw H2O.fail("Extensions already registered");
    }

    long before = System.currentTimeMillis();

    // Disallow schemas whose parent is in another package because it takes ~4s to do the getSubTypesOf call.
    String[] packages = new String[]{"water", "hex"};

    for (String pkg : packages) {
      Reflections reflections = new Reflections(pkg);
      for (Class registerClass : reflections.getSubTypesOf(water.AbstractH2OExtension.class)) {
        if (!Modifier.isAbstract(registerClass.getModifiers())) {
          try {
            Object instance = registerClass.newInstance();
            water.AbstractH2OExtension e = (water.AbstractH2OExtension) instance;
            H2O.addExtension(e);
          } catch (Exception e) {
            throw H2O.fail(e.toString());
          }
        }
      }
    }

    for (AbstractH2OExtension e : H2O.getExtensions()) {
      e.init();
    }

    extensionsRegistered = true;

    registerExtensionsMillis = System.currentTimeMillis() - before;
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
  public static void registerRestApis(String relativeResourcePath) {
    if (apisRegistered) {
      throw H2O.fail("APIs already registered");
    }

    // Log extension registrations here so the message is grouped in the right spot.
    for (AbstractH2OExtension e : H2O.getExtensions()) {
      e.printInitialized();
    }
    Log.info("Registered " + H2O.getExtensions().size() + " extensions in: " + registerExtensionsMillis + "mS");

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

    long registerApisMillis = System.currentTimeMillis() - before;
    Log.info("Registered REST APIs in: " + registerApisMillis + "mS");
  }
}
