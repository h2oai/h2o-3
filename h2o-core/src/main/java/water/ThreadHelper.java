package water;

class ThreadHelper {

  static void initCommonThreadProperties(Thread t) {
    initCommonThreadProperties(H2O.ARGS, t);
  }

  static void initCommonThreadProperties(H2O.OptArgs args, Thread t) {
    if (args.embedded) {
      t.setDaemon(true);
    }
  }

}
