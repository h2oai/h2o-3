package water;

import java.util.UUID;

public class AccuracyTestingUtil extends TestUtil {
    private static boolean _stall_called_before = false;

    // H2O cloud setup utils
    public static void setupH2OCloud(int numNodes, String logDir) {
        stall_till_cloudsize(numNodes, logDir);
        _initial_keycnt = H2O.store_size();
    }

    // ==== Test Setup & Teardown Utilities ====
    // Stall test until we see at least X members of the Cloud
    public static void stall_till_cloudsize(int x, String logDir) {
        if (! _stall_called_before) {
            if (H2O.getCloudSize() < x) {
                // Leader node, where the tests execute from.
                String cloudName = UUID.randomUUID().toString();
                String[] args = new String[]{"-name",cloudName,"-ice_root",find_test_file_static(logDir + "/results").
                  toString()};
                H2O.main(args);

                // Secondary nodes, skip if expected to be pre-built
                if( System.getProperty("ai.h2o.skipNodeCreation") == null )
                    for( int i = 0; i < x-1; i++ )
                        new NodeContainer(args).start();

                H2O.waitForCloudSize(x, 30000);

                _stall_called_before = true;
            }
        }
    }
}
