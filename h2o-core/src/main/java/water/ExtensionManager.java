package water;


import water.api.RequestServer;
import water.api.RestApiExtension;
import water.api.SchemaServer;
import water.server.RequestAuthExtension;
import water.util.Log;
import water.util.StringUtils;

import java.util.*;

public class ExtensionManager {

  private static ExtensionManager extManager = new ExtensionManager();

  /** System property to force enable/disable named REST API extension */
  private static String PROP_TOGGLE_REST_EXT = H2O.OptArgs.SYSTEM_PROP_PREFIX + "ext.rest.toggle.";
  
  /** System property to force enable/disable named Core extension */
  private static String PROP_TOGGLE_CORE_EXT = H2O.OptArgs.SYSTEM_PROP_PREFIX + "ext.core.toggle.";

  private ExtensionManager(){
  }

  public static ExtensionManager getInstance(){
    return extManager;
  }


  private HashMap<String, AbstractH2OExtension> coreExtensions = new HashMap<>();
  private HashMap<String, RestApiExtension> restApiExtensions = new HashMap<>();
  private HashMap<String, H2OListenerExtension> listenerExtensions = new HashMap<>();
  private HashMap<String, RequestAuthExtension> authExtensions = new HashMap<>();
  private long registerCoreExtensionsMillis = 0;
  private long registerListenerExtensionsMillis = 0;
  private long registerAuthExtensionsMillis = 0;
  // Be paranoid and check that this doesn't happen twice.
  private boolean extensionsRegistered = false;
  private boolean restApiExtensionsRegistered = false;
  private boolean listenerExtensionsRegistered = false;
  private boolean authExtensionsRegistered = false;

  public StringBuilder makeExtensionReport(StringBuilder sb) {
    try {
      // Core
      String[] coreExts = getCoreExtensionNames().clone();
      for (int i = 0; i < coreExts.length; i++)
        if (! isCoreExtensionEnabled(coreExts[i]))
          coreExts[i] = coreExts[i] + "(disabled)";
      sb.append("Core Extensions: ").append(StringUtils.join(",", coreExts)).append("; ");
      // Rest
      String[] restExts = getRestApiExtensionNames().clone();
      for (int i = 0; i < restExts.length; i++) {
        RestApiExtension restExt = restApiExtensions.get(restExts[i]);
        if (restExt == null)
          restExts[i] = restExts[i] + "(???)";
        else if (! isEnabled(restExt))
          restExts[i] = restExts[i] + "(disabled)";
      }
      sb.append("Rest Extensions: ").append(StringUtils.join(",", restExts)).append("; ");
      // Listeners
      String[] listernerExts = getListenerExtensionNames();
      sb.append("Listener Extensions: ").append(StringUtils.join(",", listernerExts)).append("; ");
    } catch (Exception e) {
      Log.err("Failed to generate the extension report", e);
      sb.append(e.getMessage());
    }
    return sb;
  }

  public Collection<AbstractH2OExtension> getCoreExtensions() {
    return coreExtensions.values();
  }

  public boolean isCoreExtensionsEnabled(String extensionName){
    AbstractH2OExtension ext = coreExtensions.get(extensionName);
    return ext != null && ext.isEnabled();
  }

  /**
   * Register H2O extensions.
   * <p/>
   * Use SPI to find all classes that extends water.AbstractH2OExtension
   * and call H2O.addCoreExtension() for each.
   */
  public void registerCoreExtensions() {
    if (extensionsRegistered) {
      throw H2O.fail("Extensions already registered");
    }

    long before = System.currentTimeMillis();
    ServiceLoader<AbstractH2OExtension> extensionsLoader = ServiceLoader.load(AbstractH2OExtension.class);
    for (AbstractH2OExtension ext : extensionsLoader) {
      if (isEnabled(ext)) {
        ext.init();
        coreExtensions.put(ext.getExtensionName(), ext);
      }
    }
    extensionsRegistered = true;
    registerCoreExtensionsMillis = System.currentTimeMillis() - before;
  }

  public Collection<RestApiExtension> getRestApiExtensions(){
    return restApiExtensions.values();
  }

  private boolean areDependantCoreExtensionsEnabled(List<String> names){
    for(String name: names){
      AbstractH2OExtension ext = coreExtensions.get(name);
      if(ext == null || !ext.isEnabled()){
        return false;
      }
    }
    return true;
  }

  /**
   * Register REST API routes.
   *
   * Use reflection to find all classes that inherit from {@link water.api.AbstractRegister}
   * and call the register() method for each.
   *
   */
  public void registerRestApiExtensions() {
    if (restApiExtensionsRegistered) {
      throw H2O.fail("APIs already registered");
    }

    // Log core extension registrations here so the message is grouped in the right spot.
    for (AbstractH2OExtension e : getCoreExtensions()) {
      e.printInitialized();
    }
    Log.info("Registered " + coreExtensions.size() + " core extensions in: " + registerCoreExtensionsMillis + "ms");
    Log.info("Registered H2O core extensions: " + Arrays.toString(getCoreExtensionNames()));

    if(listenerExtensions.size() > 0) {
      Log.info("Registered: " + listenerExtensions.size() + " listener extensions in: " + registerListenerExtensionsMillis + "ms");
      Log.info("Registered Listeners extensions: " + Arrays.toString(getListenerExtensionNames()));
    }
    if(authExtensions.size() > 0) {
      Log.info("Registered: " + authExtensions.size() + " auth extensions in: " + registerAuthExtensionsMillis + "ms");
      Log.info("Registered Auth extensions: " + Arrays.toString(getAuthExtensionNames()));
    }
    long before = System.currentTimeMillis();
    RequestServer.DummyRestApiContext dummyRestApiContext = new RequestServer.DummyRestApiContext();
    ServiceLoader<RestApiExtension> restApiExtensionLoader = ServiceLoader.load(RestApiExtension.class);
    for (RestApiExtension r : restApiExtensionLoader) {
      try {
        if (isEnabled(r)) {
          r.registerEndPoints(dummyRestApiContext);
          r.registerSchemas(dummyRestApiContext);
          restApiExtensions.put(r.getName(), r);
        }
      } catch (Exception e) {
        Log.info("Cannot register extension: " + r + ". Skipping it...");
      }
    }

    restApiExtensionsRegistered = true;

    long registerApisMillis = System.currentTimeMillis() - before;
    Log.info("Registered: " + RequestServer.numRoutes() + " REST APIs in: " + registerApisMillis + "ms");
    Log.info("Registered REST API extensions: " + Arrays.toString(getRestApiExtensionNames()));

    // Register all schemas
    SchemaServer.registerAllSchemasIfNecessary(dummyRestApiContext.getAllSchemas());
  }

  private boolean isEnabled(RestApiExtension r) {
    String forceToggle = System.getProperty(PROP_TOGGLE_REST_EXT + r.getName());
    return forceToggle != null
           ? Boolean.valueOf(forceToggle)
           : areDependantCoreExtensionsEnabled(r.getRequiredCoreExtensions());
  }

  private boolean isEnabled(AbstractH2OExtension r) {
    String forceToggle = System.getProperty(PROP_TOGGLE_CORE_EXT + r.getExtensionName());
    return forceToggle != null
           ? Boolean.valueOf(forceToggle)
           : r.isEnabled();
  }

  private String[] getRestApiExtensionNames(){
    return restApiExtensions.keySet().toArray(new String[restApiExtensions.keySet().size()]);
  }

  private String[] getCoreExtensionNames(){
    return coreExtensions.keySet().toArray(new String[coreExtensions.keySet().size()]);
  }

  private String[] getListenerExtensionNames(){
    return listenerExtensions.keySet().toArray(new String[listenerExtensions.keySet().size()]);
  }

  private String[] getAuthExtensionNames(){
    return authExtensions.keySet().toArray(new String[authExtensions.keySet().size()]);
  }

  public boolean isCoreExtensionEnabled(String name) {
    if (coreExtensions.containsKey(name)) {
      return coreExtensions.get(name).isEnabled();
    } else {
      return false;
    }
  }

  /**
   * Register various listener extensions
   *
   * Use reflection to find all classes that inherit from {@link water.api.AbstractRegister}
   * and call the register() method for each.
   *
   */
  public void registerListenerExtensions() {
    if (listenerExtensionsRegistered) {
      throw H2O.fail("Listeners already registered");
    }

    long before = System.currentTimeMillis();
    ServiceLoader<H2OListenerExtension> extensionsLoader = ServiceLoader.load(H2OListenerExtension.class);
    for (H2OListenerExtension ext : extensionsLoader) {
      ext.init();
      listenerExtensions.put(ext.getName(), ext);
    }
    listenerExtensionsRegistered = true;
    registerListenerExtensionsMillis = System.currentTimeMillis() - before;
  }

  public Collection<H2OListenerExtension> getListenerExtensions(){
    return listenerExtensions.values();
  }

  public void registerAuthExtensions() {
    if (authExtensionsRegistered) {
      throw H2O.fail("Auth extensions already registered");
    }

    long before = System.currentTimeMillis();
    ServiceLoader<RequestAuthExtension> extensionsLoader = ServiceLoader.load(RequestAuthExtension.class);
    for (RequestAuthExtension ext : extensionsLoader) {
      authExtensions.put(ext.getClass().getName(), ext);
    }
    authExtensionsRegistered = true;
    registerAuthExtensionsMillis = System.currentTimeMillis() - before;
  }

  public Collection<RequestAuthExtension> getAuthExtensions(){
    return authExtensions.values();
  }

}
