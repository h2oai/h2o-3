package water.util;

import org.joda.time.DateTimeZone;
import water.*;
import water.api.RestApiExtension;
import water.parser.ParseTime;

import java.lang.management.ManagementFactory;
import java.util.*;

public class ReproducibilityInformationUtils {
  public static TwoDimTable createNodeInformationTable() {
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();

    colHeaders.add("node"); colTypes.add("int"); colFormat.add("%d");
    colHeaders.add("h2o"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("healthy"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("last_ping"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("num_cpus"); colTypes.add("int"); colFormat.add("%d");
    colHeaders.add("sys_load"); colTypes.add("double"); colFormat.add("%.5f");
    colHeaders.add("mem_value_size"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("free_mem"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("pojo_mem"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("swap_mem"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("free_disc"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("max_disc"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("pid"); colTypes.add("int"); colFormat.add("%d");
    colHeaders.add("num_keys"); colTypes.add("int"); colFormat.add("%d");
    colHeaders.add("tcps_active"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("open_fds"); colTypes.add("int"); colFormat.add("%d");
    colHeaders.add("rpcs_active"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("nthreads"); colTypes.add("int"); colFormat.add("%d");
    colHeaders.add("is_leader"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("total_mem"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("max_mem"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("java_version"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("jvm_launch_parameters"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("os_version"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("machine_physical_mem"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("machine_locale"); colTypes.add("string"); colFormat.add("%s");

    H2ONode[] members = H2O.CLOUD.members();

    final int rows = members.length;
    TwoDimTable table = new TwoDimTable(
            "Node Information", null,
            new String[rows],
            colHeaders.toArray(new String[0]),
            colTypes.toArray(new String[0]),
            colFormat.toArray(new String[0]),
            "");
    NodeInfoTask info = new NodeInfoTask().doAllNodes();

    for (int row = 0; row < rows; row++) {
      int col = 0;
      table.set(row, col++, members[row].index());
      table.set(row, col++, members[row].getIpPortString());
      table.set(row, col++, Boolean.toString(members[row].isHealthy()));
      table.set(row, col++, members[row]._last_heard_from);
      table.set(row, col++, (int) members[row]._heartbeat._num_cpus);
      table.set(row, col++, members[row]._heartbeat._system_load_average);
      table.set(row, col++, members[row]._heartbeat.get_kv_mem());
      table.set(row, col++, members[row]._heartbeat.get_free_mem());
      table.set(row, col++, members[row]._heartbeat.get_pojo_mem());
      table.set(row, col++, members[row]._heartbeat.get_swap_mem());
      table.set(row, col++, members[row]._heartbeat.get_free_disk());
      table.set(row, col++, members[row]._heartbeat.get_max_disk());
      table.set(row, col++, members[row]._heartbeat._pid);
      table.set(row, col++, members[row]._heartbeat._keys);
      table.set(row, col++, members[row]._heartbeat._tcps_active);
      table.set(row, col++, members[row]._heartbeat._process_num_open_fds);
      table.set(row, col++, members[row]._heartbeat._rpcs);
      table.set(row, col++, (int) members[row]._heartbeat._nthreads);
      table.set(row, col++, Boolean.toString(row == H2O.CLOUD.leader().index() ? true : false));
      for (int i=0; i < rows; i++) {
        if (members[row].index() == info.index[i]) {
          table.set(row, col++, info.totalMem[i]);
          table.set(row, col++, info.maxMem[i]);
          table.set(row, col++, info.javaVersion[i]);
          table.set(row, col++, info.jvmLaunchParameters[i]);
          table.set(row, col++, info.osVersion[i]);
          table.set(row, col++, info.machinePhysicalMem[i]);
          table.set(row, col++, info.machineLocale[i]);
          break;
        }
      }
    }
    return table;
  }

  public static class NodeInfoTask extends MRTask<NodeInfoTask> {

    private long[] totalMem;
    private long[] maxMem;
    private String[] javaVersion;
    private String[] jvmLaunchParameters;
    private String[] osVersion;
    private long[] machinePhysicalMem;
    private String[] machineLocale;
    private int[] index;

    @Override
    public void setupLocal() {
      totalMem = new long[H2O.CLOUD.size()];
      maxMem = new long[H2O.CLOUD.size()];
      Arrays.fill(totalMem, -1);
      Arrays.fill(maxMem, -1);
      
      javaVersion = new String[H2O.CLOUD.size()];
      jvmLaunchParameters = new String[H2O.CLOUD.size()];
      osVersion = new String[H2O.CLOUD.size()];
      machinePhysicalMem = new long[H2O.CLOUD.size()];
      machineLocale = new String[H2O.CLOUD.size()];
      
      index = new int[H2O.CLOUD.size()];
      Arrays.fill(index, -1);

      if (H2O.ARGS.client) { // do not fill node info on client node
        return;
      }
      Runtime runtime = Runtime.getRuntime();
      totalMem[H2O.SELF.index()] = runtime.totalMemory();
      maxMem[H2O.SELF.index()] = runtime.maxMemory();
      javaVersion[H2O.SELF.index()] = "Java " + System.getProperty("java.version") + " (from " + System.getProperty("java.vendor") + ")";
      jvmLaunchParameters[H2O.SELF.index()] = ManagementFactory.getRuntimeMXBean().getInputArguments().toString();
      osVersion[H2O.SELF.index()] = System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")";
      machinePhysicalMem[H2O.SELF.index()] = OSUtils.getTotalPhysicalMemory();
      machineLocale[H2O.SELF.index()] = Locale.getDefault().toString();
      index[H2O.SELF.index()] = H2O.SELF.index();
    }

    @Override
    public void reduce(final NodeInfoTask other) {
      for (int i = 0; i < H2O.CLOUD.size(); i++) {
        if (other.index[i] > -1) 
          index[i] = other.index[i];
        if (other.totalMem[i] > -1) 
          totalMem[i] = other.totalMem[i];
        if (other.maxMem[i] > -1)
          maxMem[i] = other.maxMem[i];
        if (other.javaVersion != null)
          javaVersion[i] = other.javaVersion[i];
        if (other.jvmLaunchParameters[i] != null)
          jvmLaunchParameters[i] = other.jvmLaunchParameters[i];
        if (other.osVersion != null)
          osVersion[i] = other.osVersion[i];
        if (other.machinePhysicalMem[i] > -1)
          machinePhysicalMem[i] = other.machinePhysicalMem[i];
        if (other.machineLocale[i] != null)
          machineLocale[i] = other.machineLocale[i];
      }
    }
  }

  public static TwoDimTable createClusterConfigurationTable() {
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();

    colHeaders.add("H2O cluster uptime"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("H2O cluster timezone"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("H2O data parsing timezone"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("H2O cluster version"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("H2O cluster version age"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("H2O cluster name"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("H2O cluster total nodes"); colTypes.add("int"); colFormat.add("%d");
    colHeaders.add("H2O cluster free memory"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("H2O cluster total cores"); colTypes.add("int"); colFormat.add("%d");
    colHeaders.add("H2O cluster allowed cores"); colTypes.add("int"); colFormat.add("%d");
    colHeaders.add("H2O cluster status"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("H2O internal security"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("H2O API Extensions"); colTypes.add("string"); colFormat.add("%s");

    H2ONode[] members = H2O.CLOUD.members();
    long freeMem = 0;
    int totalCores = 0;
    int clusterAllowedCores = 0;
    int unhealthlyNodes = 0;
    boolean locked = Paxos._cloudLocked;
    for (int i = 0; i < members.length; i++) {
      freeMem += members[i]._heartbeat.get_free_mem();
      totalCores += members[i]._heartbeat._num_cpus;
      clusterAllowedCores += members[i]._heartbeat._cpus_allowed;
      if (!members[i].isHealthy()) unhealthlyNodes++;
    }
    String status = locked ? "locked" : "accepting new members";
    status += unhealthlyNodes > 0 ? ", " + unhealthlyNodes + " nodes are not healthly" : ", healthly";
    String apiExtensions = "";
    for (RestApiExtension ext : ExtensionManager.getInstance().getRestApiExtensions()) {
      if (apiExtensions.isEmpty())
        apiExtensions += ext.getName();
      else
        apiExtensions += ", " + ext.getName();
    }

    final int rows = 1;
    TwoDimTable table = new TwoDimTable(
            "Cluster Configuration", null,
            new String[rows],
            colHeaders.toArray(new String[0]),
            colTypes.toArray(new String[0]),
            colFormat.toArray(new String[0]),
            "");
    int row = 0;
    int col = 0;
    table.set(row, col++, System.currentTimeMillis() - H2O.START_TIME_MILLIS.get());
    table.set(row, col++, DateTimeZone.getDefault().toString());
    table.set(row, col++, ParseTime.getTimezone().toString());
    table.set(row, col++, H2O.ABV.projectVersion());
    table.set(row, col++, PrettyPrint.toAge(H2O.ABV.compiledOnDate(), new Date()));
    table.set(row, col++, H2O.ARGS.name);
    table.set(row, col++, H2O.CLOUD.size());
    table.set(row, col++, freeMem);
    table.set(row, col++, totalCores);
    table.set(row, col++, clusterAllowedCores);
    table.set(row, col++, status);
    table.set(row, col++, Boolean.toString(H2OSecurityManager.instance().securityEnabled));
    table.set(row, col++, apiExtensions);

    return table;
  }
}
