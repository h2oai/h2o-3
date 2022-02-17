package water.hive;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.io.PrintWriter;

public class GenerateHiveToken extends Configured implements Tool {

    private String runAsUser = null;
    private String principal = null;
    private String keytabPath = null;
    private String hiveJdbcUrlPattern = null;
    private String hiveHost = null;
    private String hivePrincipal = null;
    private String tokenFile = null;

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new GenerateHiveToken(), args);
        System.exit(exitCode);
    }

    private void usage() {
        System.out.println("Usage:");
        System.exit(1);
    }

    private void parseArgs(String[] args) {
        int i = 0;
        while (i < args.length) {
            String s = args[i];
            if (s.equals("-run_as_user")) {
                i++;
                if (i >= args.length) {
                    usage();
                }
                runAsUser = args[i];
            } else if (s.equals("-principal")) {
                i++;
                if (i >= args.length) {
                    usage();
                }
                principal = args[i];
            } else if (s.equals("-keytab")) {
                i++;
                if (i >= args.length) {
                    usage();
                }
                keytabPath = args[i];
            } else if (s.equals("-hiveJdbcUrlPattern")) {
                i++;
                if (i >= args.length) {
                    usage();
                }
                hiveJdbcUrlPattern = args[i];
            } else if (s.equals("-hiveHost")) {
                i++;
                if (i >= args.length) {
                    usage();
                }
                hiveHost = args[i];
            } else if (s.equals("-hivePrincipal")) {
                i++;
                if (i >= args.length) {
                    usage();
                }
                hivePrincipal = args[i];
            } else if (s.equals("-tokenFile")) {
                i++;
                if (i >= args.length) {
                    usage();
                }
                tokenFile = args[i];
            } else {
                System.err.println("Unrecognized option " + s);
                System.exit(1);
            }
            i++;
        }
    }

    private void validateArgs() {
        ImpersonationUtils.validateImpersonationArgs(principal, keytabPath, runAsUser, this::error, this::warning);
        if (hivePrincipal == null) {
            error("hive principal name is required (use the '-hivePrincipal' option)");
        }
        if (hiveHost == null && hiveJdbcUrlPattern == null) {
            error("delegation token generator requires Hive host or JDBC URL to be set (use the '-hiveHost' or '-hiveJdbcUrlPattern' option)");
        }
        if (tokenFile == null) {
            error("token file path required (use the '-tokenFile' option)");
        }
        if (!HiveTokenGenerator.isHiveDriverPresent()) {
            error("Hive JDBC driver not available on class-path");
        }
    }

    @Override
    public int run(String[] args) throws IOException, InterruptedException {
        parseArgs(args);
        validateArgs();
        ImpersonationUtils.impersonate(getConf(), principal, keytabPath, runAsUser);
        String token = HiveTokenGenerator.getHiveDelegationTokenIfHivePresent(hiveJdbcUrlPattern, hiveHost, hivePrincipal);
        if (token != null) {
            DelegationTokenPrinter.printToken(token);
            System.out.println("Token generated, writing into file " + tokenFile);
            try (PrintWriter pw = new PrintWriter(tokenFile)) {
                pw.print(token);
            }
            return 0;
        } else {
            System.out.println("No token generated.");
            return 1;
        }
    }

    private void error(String s) {
        System.err.printf("\nERROR: " + "%s\n\n", s);
        usage();
    }

    private void warning(String s) {
        System.err.printf("\nWARNING: " + "%s\n\n", s);
    }

}
