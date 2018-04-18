package water;
import water.util.Log;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

public class TelemetryExtension extends AbstractH2OExtension {
    //sampling period in seconds
    int samplingTimeout = 10;
    boolean isEnabled = false;

    @Override
    public String getExtensionName() {
        return "Telemetry";
    }

    @Override
    public String[] parseArguments(String[] args) {
        return parseTelemetrySamplingPeriod(parseTelemetryEnabled(args));
    }

    @Override
    public void printHelp() {
        System.out.println(
                "\nTelemetry extension:\n" +
                        "    -enable_telemetry\n" +
                        "          Switches telementry on (=true/false)\n" +
                        "    -telemetry_sampling_period\n" +
                        "          How often sampling is done (in seconds).\n");
    }

    @Override
    public void onLocalNodeStarted() {
        new TelemetryThread().start();
    }

    private String[] parseTelemetryEnabled(String[] args){
        for (int i = 0; i < args.length; i++) {
            H2O.OptString s = new H2O.OptString(args[i]);
            if(s.matches("enable_telemetry")){
                isEnabled = true;
                String[] new_args = new String[args.length - 1];
                System.arraycopy(args, 0, new_args, 0, i);
                System.arraycopy(args, i + 1, new_args, i, args.length - (i + 1));
                return new_args;
            }
        }
        return args;
    }
    private String[] parseTelemetrySamplingPeriod(String args[]){
        for (int i = 0; i < args.length; i++) {
            H2O.OptString s = new H2O.OptString(args[i]);
            if(s.matches("telemetry_sampling_period")){
                samplingTimeout = s.parseInt(args[i + 1]);
                String[] new_args = new String[args.length - 2];
                System.arraycopy(args, 0, new_args, 0, i);
                System.arraycopy(args, i + 2, new_args, i, args.length - (i + 2));
                return new_args;
            }
        }
        return args;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    private class TelemetryThread extends Thread {
        public TelemetryThread() {
            super("TelemetryThread");
            // don't prevent JVM exit with this thread
            setDaemon(true);
        }

        private String getMemInfo(){
            int mb = 1024*1024;
            Runtime runtime = Runtime.getRuntime();
            String format = "|%1$-23s|%2$-10s|%3$-10s|%4$-10s|%5$-10s|%6$-10s\n";
            String ex[] = {"Mem[MB]/GC[ms] metrics:", "Total mem", "Free mem", "Max Mem","GC Cnt","GC Dur"};
            String result = String.format(format, ex);

            String ex2[] = {"",
                    String.valueOf(runtime.totalMemory()/mb),
                    String.valueOf(runtime.freeMemory() / mb),
                    String.valueOf(runtime.maxMemory() / mb),
                    String.valueOf(getGcCount()),
                    String.valueOf(getGcTime())};

            result += String.format(format, ex2);
            return result;
        }

        private long getGcTime(){
            long sum = 0;
            for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()){
                long count = b.getCollectionTime();
                if (count != -1) { sum +=  count; }
            }
            return sum;
        }

        private long getGcCount() {
            long sum = 0;
            for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
                long count = b.getCollectionCount();
                if (count != -1) { sum +=  count; }
            }
            return sum;
        }

        @Override
        public void run() {
            Log.debug("H2O Telemetry thread started.");
            Log.debug("Collecting metrics: Used mem | Free mem | Total Mem| Max Mem| GC Cnt| GC Dur");
            while (true) {
                if (Log.getLogLevel() == Log.DEBUG) {
                    Log.debug(getMemInfo());
                }

                try {
                    Thread.sleep(samplingTimeout * 1000);
                } catch (InterruptedException ignore) {
                }
            }
        }
    }
}
