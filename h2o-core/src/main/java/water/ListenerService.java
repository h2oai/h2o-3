package water;

/**
 * Service used to write to registered H2O listeners
 */
public class ListenerService {

  private static final ListenerService service = new ListenerService();
  private ListenerService(){
  }

  public static ListenerService getInstance(){
    return service;
  }

  public void report(String msg, Object... data){
    for (H2OListenerExtension ext : ExtensionManager.getInstance().getListenerExtensions()) {
      ext.report(msg, data);
    }
  }
}
