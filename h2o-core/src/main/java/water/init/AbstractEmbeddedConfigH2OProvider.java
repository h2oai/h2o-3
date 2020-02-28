package water.init;

public abstract class AbstractEmbeddedConfigH2OProvider {

  public abstract String getName();

  public void init() {}
  
  public boolean isActive() {
    return false;
  }
  
  public abstract AbstractEmbeddedH2OConfig getConfig();
  
}
