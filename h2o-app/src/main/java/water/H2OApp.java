package water;

public class H2OApp extends H2OStarter {
  public static void main(String[] args) {
    start(args, System.getProperty("user.dir"));
  }

  @SuppressWarnings("unused")
  public static void main2(String relativeResourcePath) {
    start(new String[0], relativeResourcePath);
  }
}