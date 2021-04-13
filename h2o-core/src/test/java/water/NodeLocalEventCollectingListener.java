package water;

import org.junit.Ignore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Ignore
public class NodeLocalEventCollectingListener implements H2OListenerExtension {

  private ConcurrentHashMap<String, ArrayList<Object[]>> reports;
  @Override
  public String getName() {
    return "LocalEverExpandingListener";
  }

  @Override
  public void init() {
      reports = new ConcurrentHashMap<>();
  }

  @Override
  public void report(String ctrl, Object... data) {
    if(!reports.containsKey(ctrl)){
      reports.put(ctrl, new ArrayList<>());
    }
    reports.get(ctrl).add(data);
  }

  public ArrayList<Object[]> getData(String ctrl) {
    return reports.get(ctrl);
  }

  public void clear(){
    reports = new ConcurrentHashMap<>();
  }

  public static NodeLocalEventCollectingListener getFreshInstance() {
    Collection<H2OListenerExtension> listenerExtensions = ExtensionManager.getInstance().getListenerExtensions();
    NodeLocalEventCollectingListener ext = (NodeLocalEventCollectingListener) listenerExtensions.iterator().next();
    ext.clear();
    return ext;
  }

}
