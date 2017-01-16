package water;

/**
 * Helps you avoid wrapping an exception into RTE. Just throws whatever you like.
 */
public class AnyThrow {

  public static void throwUnchecked(Throwable e) {
    AnyThrow.<RuntimeException>throwAny(e);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable e) throws E {
    throw (E)e;
  }
}
