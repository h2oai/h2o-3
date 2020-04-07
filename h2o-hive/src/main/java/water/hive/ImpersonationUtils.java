package water.hive;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.IOException;

public class ImpersonationUtils {
    
    public interface Callback { void call(String msg); }

    public static void validateImpersonationArgs(
        String principal, String keytabPath, String runAsUser,
        Callback error, Callback warn
    ) {
        if (principal != null || keytabPath != null) {
            if (principal == null) {
                error.call("keytab requires a valid principal (use the '-principal' option)");
            }
            if (keytabPath == null) {
                error.call("principal requires a valid keytab path (use the '-keytab' option)");
            }
            if (runAsUser != null) {
                warn.call("will attempt secure impersonation with user from '-run_as_user', " + runAsUser);
            }
        }
    }

    public static void impersonate(Configuration conf, String principal, String keytabPath, String runAsUser) throws IOException {
        if (principal != null && keytabPath != null) {
            UserGroupInformation.setConfiguration(conf);
            UserGroupInformation.loginUserFromKeytab(principal, keytabPath);
            // performs user impersonation (will only work if core-site.xml has hadoop.proxyuser.*.* props set on name node
            if (runAsUser != null) {
                System.out.println("Attempting to securely impersonate user, " + runAsUser);
                UserGroupInformation currentEffUser = UserGroupInformation.getLoginUser();
                UserGroupInformation proxyUser = UserGroupInformation.createProxyUser(runAsUser, currentEffUser);
                UserGroupInformation.setLoginUser(proxyUser);
            }
        } else if (runAsUser != null) {
            UserGroupInformation.setConfiguration(conf);
            UserGroupInformation.setLoginUser(UserGroupInformation.createRemoteUser(runAsUser));
        }
    }
}
