package hex.tree.xgboost.remote;

import water.server.ServletMeta;
import water.server.ServletProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RemoteXGBoostServletProvider implements ServletProvider {

    private static final List<ServletMeta> SERVLETS = Collections.unmodifiableList(Arrays.asList(
        new ServletMeta("/3/XGBoostExecutor.upload", RemoteXGBoostUploadServlet.class)
    ));

    @Override
    public List<ServletMeta> servlets() {
        return SERVLETS;
    }

    @Override
    public int priority() {
        return 0;
    }

}
