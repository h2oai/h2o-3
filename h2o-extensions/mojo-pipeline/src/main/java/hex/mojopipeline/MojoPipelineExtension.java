package hex.mojopipeline;

import ai.h2o.mojos.runtime.api.PipelineLoaderFactory;
import water.AbstractH2OExtension;
import water.util.Log;

import java.util.ServiceLoader;

public class MojoPipelineExtension extends AbstractH2OExtension {

  private static String NAME = "MojoPipeline";

  private boolean _initialized;
  private boolean _enabled;

  @Override
  public String getExtensionName() {
    return NAME;
  }

  @Override
  public void onLocalNodeStarted() {
  }

  @Override
  public boolean isEnabled() {
    if (! _initialized) {
      initialize();
    }
    return _enabled;
  }

  private synchronized void initialize() {
    _enabled = hasMojoRuntime();
    _initialized = true;
    if (! _enabled) {
      Log.debug("MOJO Runtime not found");
    }
  }

  private boolean hasMojoRuntime() {
    // relying on implementation - need to improve MOJO2 API
    return ServiceLoader.load(PipelineLoaderFactory.class).iterator().hasNext();
  }

}
