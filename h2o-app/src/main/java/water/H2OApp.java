package water;

public class H2OApp extends H2OStarter {
  
  public static int BAD_JAVA_VERSION_RETURN_CODE = 3;
  
  public static void main(String[] args) {

    if (H2O.checkUnsupportedJava(args))
      System.exit(BAD_JAVA_VERSION_RETURN_CODE);

    start(args, System.getProperty("user.dir"));
  }

  @SuppressWarnings("unused")
  public static void main2(String relativeResourcePath) {
    start(new String[0], relativeResourcePath);
  }
}
