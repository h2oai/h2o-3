package water;

import water.util.Log;

/**
 * This Extension is used to regularly report specific metrics about the system.
 *
 * There are also other metrics collected in the system using the TelemetryService. The difference between the
 * Telemetry Service and this extension is that the Telemetry service triggers the telemetry notifications based on some
 * events, however this extension is being run in a loop with specified sampling timeout.
 */
public class SamplingTelemetryExtension extends AbstractH2OExtension {
    //sampling period in seconds
    private int samplingTimeout = -1;

    @Override
    public String getExtensionName() {
        return "SamplingTelemetry";
    }

    @Override
    public String[] parseArguments(String[] args) {
        return parseTelemetrySamplingPeriod(args);
    }

    @Override
    public void printHelp() {
        System.out.println(
                "\nTelemetry extension:\n" +
                        "    -telemetry_window\n" +
                        "          How often the sampling is done (in seconds). If not set, telemetry is off.\n");
    }

    @Override
    public void onLocalNodeStarted() {
        if (samplingTimeout > 0) {
            new TelemetryThread().start();
        }
    }

    private String[] parseTelemetrySamplingPeriod(String args[]) {
        for (int i = 0; i < args.length; i++) {
            H2O.OptString s = new H2O.OptString(args[i]);
            if (s.matches("telemetry_window")) {
                samplingTimeout = s.parseInt(args[i + 1]);
                String[] new_args = new String[args.length - 2];
                System.arraycopy(args, 0, new_args, 0, i);
                System.arraycopy(args, i + 2, new_args, i, args.length - (i + 2));
                return new_args;
            }
        }
        return args;
    }

    private class TelemetryThread extends Thread {

        TelemetryThread() {
            super("TelemetryThread");
            // don't prevent JVM exit with this thread
            setDaemon(true);
        }

        @Override
        public void run() {
            Log.debug("H2O Telemetry thread started.");
            while (true) {

                TelemetryService.getInstance().report(H2O.SELF);

                try {
                    Thread.sleep(samplingTimeout * 1000);
                } catch (InterruptedException ignore) {
                }
            }
        }
    }
}
