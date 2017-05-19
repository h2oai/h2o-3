package org.apache.hadoop.mapreduce.v2.app;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.mapreduce.v2.app.client.ClientService;
import org.apache.hadoop.mapreduce.v2.util.MRWebAppUtil;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.ShutdownHookManager;
import org.apache.hadoop.yarn.YarnUncaughtExceptionHandler;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.Clock;
import org.apache.hadoop.yarn.util.ConverterUtils;

import java.io.IOException;

/**
 * H2O Specific MRAppMaster
 */
public class H2OMRAppMaster extends MRAppMaster {
  private static final Log LOG = LogFactory.getLog(H2OMRAppMaster.class);

  public H2OMRAppMaster(ApplicationAttemptId applicationAttemptId,
               ContainerId containerId,
               String nmHost, int nmPort, int nmHttpPort, long appSubmitTime) {
    super(applicationAttemptId, containerId, nmHost, nmPort, nmHttpPort, appSubmitTime);
  }

  //
  // This is boot code from org.apache.hadoop.mapreduce.v2.app.MRAppMaster from.
  // See: https://github.com/apache/hadoop/blame/trunk/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-app/src/main/java/org/apache/hadoop/mapreduce/v2/app/MRAppMaster.java
  // for the latest changes.
  //
  public static void main(String[] args) {
    try {
      // --- Start ---
      Thread.setDefaultUncaughtExceptionHandler(new YarnUncaughtExceptionHandler());
      String containerIdStr =
          System.getenv(ApplicationConstants.Environment.CONTAINER_ID.name());
      String nodeHostString = System.getenv(ApplicationConstants.Environment.NM_HOST.name());
      String nodePortString = System.getenv(ApplicationConstants.Environment.NM_PORT.name());
      String nodeHttpPortString =
          System.getenv(ApplicationConstants.Environment.NM_HTTP_PORT.name());
      String appSubmitTimeStr =
          System.getenv(ApplicationConstants.APP_SUBMIT_TIME_ENV);

      validateInputParam(containerIdStr,
                         ApplicationConstants.Environment.CONTAINER_ID.name());
      validateInputParam(nodeHostString, ApplicationConstants.Environment.NM_HOST.name());
      validateInputParam(nodePortString, ApplicationConstants.Environment.NM_PORT.name());
      validateInputParam(nodeHttpPortString,
                         ApplicationConstants.Environment.NM_HTTP_PORT.name());
      validateInputParam(appSubmitTimeStr,
                         ApplicationConstants.APP_SUBMIT_TIME_ENV);

      ContainerId containerId = ConverterUtils.toContainerId(containerIdStr);
      ApplicationAttemptId applicationAttemptId =
          containerId.getApplicationAttemptId();
      long appSubmitTime = Long.parseLong(appSubmitTimeStr);
      // --- End ---

      // We need to create H2OMRAppMaster instead of default MR master
      MRAppMaster appMaster =
          new H2OMRAppMaster(applicationAttemptId, containerId, nodeHostString,
                             Integer.parseInt(nodePortString),
                             Integer.parseInt(nodeHttpPortString), appSubmitTime);
      // --- Start ---
      ShutdownHookManager.get().addShutdownHook(
          new MRAppMasterShutdownHook(appMaster), SHUTDOWN_HOOK_PRIORITY);
      JobConf conf = new JobConf(new YarnConfiguration());
      conf.addResource(new Path(MRJobConfig.JOB_CONF_FILE));

      MRWebAppUtil.initialize(conf);
      String jobUserName = System
          .getenv(ApplicationConstants.Environment.USER.name());
      conf.set(MRJobConfig.USER_NAME, jobUserName);
      // Do not automatically close FileSystem objects so that in case of
      // SIGTERM I have a chance to write out the job history. I'll be closing
      // the objects myself.
      conf.setBoolean("fs.automatic.close", false);
      initAndStartAppMaster(appMaster, conf, jobUserName);
    } catch (Throwable t) {
      LOG.fatal("Error starting MRAppMaster", t);
      ExitUtil.terminate(1, t);
    }
    // -- End ---
  }

  private static void validateInputParam(String value, String param)
      throws IOException {
    if (value == null) {
      String msg = param + " is null";
      LOG.error(msg);
      throw new IOException(msg);
    }
  }
}
