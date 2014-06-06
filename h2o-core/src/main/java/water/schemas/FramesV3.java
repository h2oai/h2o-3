package water.schemas;

import water.H2O;
import water.api.FramesHandler;
import water.api.Handler;

public class FramesV3 extends FramesBase {
    // Version-  and Schema-specific filling into the handler
    @Override public FramesBase fillInto( FramesHandler h ) {
        FramesBase f = super.fillInto(h);
        return f;
    }

    // Version-  and Schema-specific filling into the handler
    @Override public FramesBase fillFrom( FramesHandler h ) {
        FramesBase f = super.fillFrom(h);
        return f;
    }
}
