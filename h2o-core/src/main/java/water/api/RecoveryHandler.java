package water.api;

import hex.faulttolerance.Recovery;
import water.H2O;
import water.Iced;
import water.api.schemas3.SchemaV3;

import java.util.Optional;

import static water.api.API.Direction.INPUT;

public class RecoveryHandler extends Handler {
    
    public static class ResumeV3 extends SchemaV3<Iced, ResumeV3> {

        @API(help = "Full path to the directory with recovery data", direction = INPUT)
        public String recovery_dir;

    }

    public ResumeV3 resume(final int version, final ResumeV3 params) {
        String recoveryDir;
        if (params.recovery_dir != null && params.recovery_dir.length() > 0) {
            recoveryDir = params.recovery_dir;
        } else {
            recoveryDir = H2O.ARGS.auto_recovery_dir;
        }
        Recovery.autoRecover(Optional.ofNullable(recoveryDir));
        return params;
    }

}
