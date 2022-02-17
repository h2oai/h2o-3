package hex.example;

import org.apache.log4j.Logger;
import water.AbstractH2OExtension;

public class ExampleExtension extends AbstractH2OExtension {

    private static final Logger LOG = Logger.getLogger(ExampleExtension.class);

    public static String NAME = "Example";

    @Override
    public String getExtensionName() {
        return NAME;
    }

    @Override
    public void onLocalNodeStarted() {
        if (!isEnabled())
            return;
        // write your custom initialization code
        LOG.info("This H2O instance has Example extension enabled");
    }

}
