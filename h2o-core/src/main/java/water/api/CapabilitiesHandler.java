package water.api;

import water.AbstractH2OExtension;
import water.ExtensionManager;
import water.api.schemas3.CapabilitiesV3;
import water.api.schemas3.CapabilityEntryV3;

import java.util.ArrayList;


public class CapabilitiesHandler extends Handler{

  private ArrayList<CapabilityEntryV3> getCoreExtensionEntries(){
    ArrayList<CapabilityEntryV3> entries = new ArrayList<>();
    for(AbstractH2OExtension ext: ExtensionManager.getInstance().getCoreExtensions()){
      entries.add(new CapabilityEntryV3(ext.getExtensionName()));
    }
    return entries;
  }

  private ArrayList<CapabilityEntryV3> getRestAPIExtensionEntries(){
    ArrayList<CapabilityEntryV3> entries = new ArrayList<>();
    for(RestApiExtension ext: ExtensionManager.getInstance().getRestApiExtensions()){
      entries.add(new CapabilityEntryV3(ext.getName()));
    }
    return entries;
  }


  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CapabilitiesV3 listAll(int version, CapabilitiesV3 s) {
    ArrayList<CapabilityEntryV3> entries = new ArrayList<>();
    entries.addAll(getCoreExtensionEntries());
    entries.addAll(getRestAPIExtensionEntries());
    s.capabilities = entries.toArray(new CapabilityEntryV3[entries.size()]);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CapabilitiesV3 listCore(int version, CapabilitiesV3 s) {
    ArrayList<CapabilityEntryV3> entries = new ArrayList<>();
    entries.addAll(getCoreExtensionEntries());
    s.capabilities = entries.toArray(new CapabilityEntryV3[entries.size()]);
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CapabilitiesV3 listRest(int version, CapabilitiesV3 s) {
    ArrayList<CapabilityEntryV3> entries = new ArrayList<>();
    entries.addAll(getRestAPIExtensionEntries());
    s.capabilities = entries.toArray(new CapabilityEntryV3[entries.size()]);
    return s;
  }
}
