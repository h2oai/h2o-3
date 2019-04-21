package water.hadoop.mapred;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.ClientCache;
import org.apache.hadoop.mapred.ResourceMgrDelegate;
import org.apache.hadoop.mapred.YARNRunner;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.mapreduce.v2.app.MRAppMaster;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import water.util.CollectionUtils;

/**
 * A H2O specific ProtocolClient
 *
 * This class makes sure that the reported type of the Application is H2O instead of MapReduce.
 * We use standard YARNRunner, but just override application type to H2O.
 *
 * This class is used by H2OYarnClientProtocolProvider, which is loaded dynamically via SPI.
 *
 * Important note: This class is not use in H2O but in Sparkling Water.
 */
public class H2OYARNRunner extends YARNRunner {

  private static final Log LOG = LogFactory.getLog(H2OYARNRunner.class);

  // Here is place which can override default application master
  // to replace it via H2O App master
  private static final Class NEW_APP_MASTER_CLASS = MRAppMaster.class;
  private static final Class DEFAULT_APP_MASTER_CLASS = MRAppMaster.class;

  public H2OYARNRunner(Configuration conf) {
    super(conf);
  }

  public H2OYARNRunner(Configuration conf,
                       ResourceMgrDelegate resMgrDelegate) {
    super(conf, resMgrDelegate);
  }

  public H2OYARNRunner(Configuration conf, ResourceMgrDelegate resMgrDelegate,
                       ClientCache clientCache) {
    super(conf, resMgrDelegate, clientCache);
  }

  @Override
  public ApplicationSubmissionContext createApplicationSubmissionContext(Configuration jobConf,
                                                                         String jobSubmitDir,
                                                                         Credentials ts)
      throws IOException {
    // Change created app context
    LOG.info("Setting application type to H2O");
    ApplicationSubmissionContext appContext = super.createApplicationSubmissionContext(jobConf, jobSubmitDir, ts);
    appContext.setApplicationType("H2O");
    // Modify MRAppMaster commands to point to our master
    if (replaceDefaultAppMaster()) {
      LOG.info("Setting MRAppMaster to " + NEW_APP_MASTER_CLASS.getName().toString());
      ContainerLaunchContext origClc = appContext.getAMContainerSpec();
      ContainerLaunchContext newClc = ContainerLaunchContext.newInstance(
          origClc.getLocalResources(), origClc.getEnvironment(),
          replaceMRAppMaster(origClc.getCommands()),
          null, origClc.getTokens(), origClc.getApplicationACLs());
      LOG.info(newClc);
      appContext.setAMContainerSpec(newClc);
    }
    // And return modified context
    return appContext;
  }

  private List<String> replaceMRAppMaster(List<String> commands) {
    Vector<String> args = new Vector<String>(8);
    for (String cmd : commands) {
      if (cmd.contains(MRJobConfig.APPLICATION_MASTER_CLASS)) {
        cmd = cmd.replace(MRJobConfig.APPLICATION_MASTER_CLASS, NEW_APP_MASTER_CLASS.getName());
      }
      args.add(cmd);
    }
    return args;
  }

  private static boolean replaceDefaultAppMaster() {
    return NEW_APP_MASTER_CLASS != DEFAULT_APP_MASTER_CLASS;
  }
}

