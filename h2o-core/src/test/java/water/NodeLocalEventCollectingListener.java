package water;

import org.junit.Ignore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

@Ignore
public class NodeLocalEventCollectingListener extends TestBase implements H2OListenerExtension {

  private HashMap<String, ArrayList<Object[]>> reports;
  @Override
  public String getName() {
    return "LocalEverExpandingListener";
  }

  @Override
  public void init() {
      reports = new HashMap<>();
  }

  @Override
  public void report(String ctrl, Object... data) {
    if(!reports.containsKey(ctrl)){
      reports.put(ctrl, new ArrayList<Object[]>());
    }
    reports.get(ctrl).add(data);
  }

  public ArrayList<Object[]> getData(String ctrl) {
    return reports.get(ctrl);
  }

  public void clear(){
    reports = new HashMap<>();
  }

  public static NodeLocalEventCollectingListener getFreshInstance() {
    Collection<H2OListenerExtension> listenerExtensions = ExtensionManager.getInstance().getListenerExtensions();
    NodeLocalEventCollectingListener ext = (NodeLocalEventCollectingListener) listenerExtensions.iterator().next();
    ext.clear();
    return ext;
  }

}
