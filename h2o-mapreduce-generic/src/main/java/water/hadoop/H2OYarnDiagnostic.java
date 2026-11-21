package water.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationResourceUsageReport;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.NodeState;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.api.records.YarnClusterMetrics;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class H2OYarnDiagnostic {
    static long totalRequestedVirtualCores(int numNodes, Configuration conf) {
        long worker = conf.getInt("mapreduce.map.cpu.vcores", 1);
        long am = conf.getInt("yarn.app.mapreduce.am.resource.cpu-vcores", 1);
        return (long) numNodes * worker + am;
    }

    static long availableMemory(List<NodeReport> reports) {
        long result = 0;
        if (reports != null) for (NodeReport node : reports) {
            if (node != null && node.getNodeState() == NodeState.RUNNING && node.getCapability() != null) {
                Resource used = node.getUsed();
                result += Math.max(0, (long) node.getCapability().getMemory() - (used == null ? 0 : used.getMemory()));
            }
        }
        return result;
    }

    static long availableVirtualCores(List<NodeReport> reports) {
        long result = 0;
        if (reports != null) for (NodeReport node : reports) {
            if (node != null && node.getNodeState() == NodeState.RUNNING && node.getCapability() != null) {
                Resource used = node.getUsed();
                result += Math.max(0, (long) node.getCapability().getVirtualCores() - (used == null ? 0 : used.getVirtualCores()));
            }
        }
        return result;
    }

    // These are known up front.
    Configuration conf;
    String applicationId;
    String queueName;
    int numNodes;
    int nodeMemoryMb;
    long nodeVirtualCores;
    long applicationMasterVirtualCores;
    int numNodesStarted;

    // Fill these in as we process the queue information, and remember the
    // answers for printing helpful diagnostics.
    long queueAvailableMemory;
    long queueAvailableVirtualCores;

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.out.println("usage:  applicationId queueName numNodes nodeMemoryMb numNodesStarted");
            System.exit(1);
        }

        diagnose(args[0], args[1], Integer.valueOf(args[2]), Integer.valueOf(args[3]), Integer.valueOf(args[4]), new YarnConfiguration());
    }

    /**
     * The assumption is this method doesn't get called unless a problem occurred.
     *
     * @param queueName YARN queue name
     * @param numNodes Requested number of worker containers (not including AM)
     * @param nodeMemoryMb Requested worker container size
     * @param numNodesStarted Number of containers that actually got started before giving up
     * @throws Exception
     */
	public static void diagnose(String applicationId, String queueName, int numNodes, int nodeMemoryMb, int numNodesStarted) throws Exception {
        diagnose(applicationId, queueName, numNodes, nodeMemoryMb, numNodesStarted, new YarnConfiguration());
    }

    /** Uses the same effective Hadoop configuration as the submitting driver. */
    public static void diagnose(String applicationId, String queueName, int numNodes, int nodeMemoryMb, int numNodesStarted, Configuration conf) throws Exception {
        H2OYarnDiagnostic client = new H2OYarnDiagnostic();
        client.conf = conf;
        client.applicationId = applicationId;
        client.queueName = queueName;
        client.numNodes = numNodes;
        client.nodeMemoryMb = nodeMemoryMb;
        client.nodeVirtualCores = conf.getInt("mapreduce.map.cpu.vcores", 1);
        client.applicationMasterVirtualCores = conf.getInt("yarn.app.mapreduce.am.resource.cpu-vcores", 1);
        client.numNodesStarted = numNodesStarted;
        client.run();
    }

    public H2OYarnDiagnostic() throws Exception {
        this(new YarnConfiguration());
    }

    private H2OYarnDiagnostic(Configuration conf) throws Exception {
        this.conf = conf;
	}

    private void run() throws IOException, YarnException {
        YarnClient yarnClient;
        yarnClient = YarnClient.createYarnClient();
        yarnClient.init(conf);
        yarnClient.start();

        List<NodeReport> clusterNodeReports = yarnClient.getNodeReports();
        List<QueueInfo> rootQueues = yarnClient.getRootQueueInfos();
        QueueInfo queueInfo = yarnClient.getQueueInfo(this.queueName);
        if (queueInfo == null) {
            printErrorDiagnosis("Queue not found (" + this.queueName + ")");
            return;
        }

        System.out.println("");
        printYarnClusterMetrics(yarnClient);
        System.out.println("");
        printClusterNodeReports(clusterNodeReports);
        System.out.println("");
        printQueueInfo(queueInfo);
        System.out.println("");
        printQueueCapacity(clusterNodeReports, queueInfo, rootQueues);
        System.out.println("");
        printDiagnosis(clusterNodeReports);
    }

    final int LEFT = 0;
    final int RIGHT = 1;

    private String prettyPrintString(String s, int width, int justification) {
        if (justification == LEFT) {
            return String.format("%-" + width + "s", s);
        }
        else {
            return String.format("%" + width + "s", s);
        }
    }

    private String prettyPrintMb(int mb) {
        return prettyPrintMb(mb, true);
    }

    private String prettyPrintMb(long mb) {
        return prettyPrintMb(mb, true);
    }

    private String prettyPrintMb(int mb, boolean printUnits) {
        double gb = (double)mb / 1024.0;
        String s = String.format("%.1f", gb);
        if (printUnits) {
            s += " GB";
        }
        return s;
    }

    private String prettyPrintMb(long mb, boolean printUnits) {
        double gb = (double)mb / 1024.0;
        String s = String.format("%.1f", gb);
        return printUnits ? s + " GB" : s;
    }

    private String prettyPrintCapacity(float cap) {
        return String.format("%.2f", cap);
    }

    private void printYarnClusterMetrics(YarnClient yarnClient) throws IOException, YarnException {
        System.out.println("----- YARN cluster metrics -----");
        YarnClusterMetrics clusterMetrics = yarnClient.getYarnClusterMetrics();
        System.out.println("Number of YARN worker nodes: " + clusterMetrics.getNumNodeManagers());
    }

    private void printClusterNodeReports(List<NodeReport> clusterNodeReports) throws IOException, YarnException {
        ArrayList<String> nodes = new ArrayList<String>();
        ArrayList<String> racks = new ArrayList<String>();
        ArrayList<String> states = new ArrayList<String>();
        ArrayList<String> containers = new ArrayList<String>();
        ArrayList<String> gbs = new ArrayList<String>();
        ArrayList<String> vcores = new ArrayList<String>();

        System.out.println("----- Nodes -----");
        for (NodeReport node : clusterNodeReports) {
            Resource capability = node.getCapability();
            Resource used = node.getUsed();
            nodes.add("Node: http://" + node.getHttpAddress());
            racks.add("Rack: " + node.getRackName());
            states.add("" + node.getNodeState());
            containers.add("" + node.getNumContainers() + " containers used");
            gbs.add(prettyPrintMb(used.getMemory(), false) + " / " + prettyPrintMb(capability.getMemory(), false) + " GB used");
            vcores.add(used.getVirtualCores() + " / " + capability.getVirtualCores() + " vcores used");
        }

        ArrayList<ArrayList<String>> cols = new ArrayList<ArrayList<String>>();
        cols.add(nodes);
        cols.add(racks);
        cols.add(states);
        cols.add(containers);
        cols.add(gbs);
        cols.add(vcores);
        int numCols = cols.size();

        int[] colJustifications = new int[numCols];
        colJustifications[0] = LEFT;
        colJustifications[1] = LEFT;
        colJustifications[2] = RIGHT;
        colJustifications[3] = RIGHT;
        colJustifications[4] = RIGHT;
        colJustifications[5] = RIGHT;

        int[] colWidths = new int[numCols];
        for (int j = 0; j < numCols; j++) {
            for (String s : cols.get(j)) {
                if (s.length() > colWidths[j]) {
                    colWidths[j] = s.length();
                }
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < numCols; j++) {
                if (j == 1) {
                    System.out.print(" ");
                }
                else if (j > 1) {
                    System.out.print(", ");
                }

                System.out.print(prettyPrintString(cols.get(j).get(i), colWidths[j], colJustifications[j]));
            }

            System.out.println("");
        }
    }

    private void printQueueInfo2(QueueInfo queueInfo) throws IOException, YarnException {
        System.out.println("Queue name:            " + queueInfo.getQueueName());
        System.out.println("    Queue state:       " + queueInfo.getQueueState());
        System.out.println("    Current capacity:  " + prettyPrintCapacity(queueInfo.getCurrentCapacity()));
		System.out.println("    Capacity:          " + prettyPrintCapacity(queueInfo.getCapacity()));
        System.out.println("    Maximum capacity:  " + prettyPrintCapacity(queueInfo.getMaximumCapacity()));
        System.out.println("    Application count: " + queueInfo.getApplications().size());
        List<ApplicationReport> applications = queueInfo.getApplications();
        if (applications.size() > 0) {
            System.out.println("    ----- Applications in this queue -----");
        }
        for (ApplicationReport ar : applications){
            System.out.println("    Application ID:                  " + ar.getApplicationId()
                    + " (" + ar.getName() + ")");
            System.out.println("        Started:                     " + ar.getUser()
                    + " (" + new Date(ar.getStartTime()).toString() + ")");
            YarnApplicationState as = ar.getYarnApplicationState();
            if (as != YarnApplicationState.RUNNING) {
                System.out.println("        Application state:           " + ar.getYarnApplicationState());
            }
            System.out.println("        Tracking URL:                " + ar.getTrackingUrl());
            System.out.println("        Queue name:                  " + ar.getQueue());
            ApplicationResourceUsageReport ur = ar.getApplicationResourceUsageReport();
            System.out.println("        Used/Reserved containers:    "
                    + ur.getNumUsedContainers()
                    + " / " + ur.getNumReservedContainers()
            );
            System.out.println("        Needed/Used/Reserved memory: "
                    + prettyPrintMb(ur.getNeededResources().getMemory())
                    + " / " + prettyPrintMb(ur.getUsedResources().getMemory())
                    + " / " + prettyPrintMb(ur.getReservedResources().getMemory())
            );
            System.out.println("        Needed/Used/Reserved vcores: "
                            + ur.getNeededResources().getVirtualCores()
                            + " / " + ur.getUsedResources().getVirtualCores()
                            + " / " + ur.getReservedResources().getVirtualCores()
            );
        }
        List<QueueInfo> childQueues = queueInfo.getChildQueues();
        for (QueueInfo q : childQueues) {
            printQueueInfo2(q);
        }
    }

    private void printQueueInfo(QueueInfo queueInfo) throws IOException, YarnException {
        System.out.println("----- Queues -----");
        if (queueInfo == null) {
            System.out.println("Queue '" + this.queueName + "' not found");
            return;
        }
        printQueueInfo2(queueInfo);
    }

	private double calcFractionalCapability(double startingValue, List<QueueInfo> queues, String queueToFind) {
		if (queues == null) {
			return -1.0;
		}

		double totalCapacityAtLevel = 0;
		for (QueueInfo qi : queues) {
			totalCapacityAtLevel += qi.getCapacity();
		}
        for (QueueInfo qi : queues) {
			if (qi.getQueueName().equals(queueToFind)) {
				return startingValue * qi.getCapacity() / totalCapacityAtLevel;
			}
		}
        for (QueueInfo qi : queues) {
			double newStartingValue = startingValue * qi.getCapacity() / totalCapacityAtLevel;
			double rv = calcFractionalCapability(newStartingValue, qi.getChildQueues(), queueToFind);
			if (rv > 0) {
				return rv;
			}
		}

        throw new RuntimeException("Diagnostic failed, please contact support@h2o.ai");
	}

    static long calcUsedMemory(QueueInfo queueInfo) {
		if (queueInfo == null) {
			return 0;
		}

        long memory = 0;
        if (queueInfo.getApplications() != null) for (ApplicationReport ar : queueInfo.getApplications()) {
            if (ar.getApplicationResourceUsageReport() != null && ar.getApplicationResourceUsageReport().getUsedResources() != null) {
                memory += ar.getApplicationResourceUsageReport().getUsedResources().getMemory();
            }
		}
        if (queueInfo.getChildQueues() != null) for (QueueInfo qi : queueInfo.getChildQueues()) {
			memory += calcUsedMemory(qi);
		}
		return memory;
	}

    static long calcUsedVirtualCores(QueueInfo queueInfo) {
		if (queueInfo == null) {
			return 0;
		}

        long vc = 0;
        if (queueInfo.getApplications() != null) for (ApplicationReport ar : queueInfo.getApplications()) {
            if (ar.getApplicationResourceUsageReport() != null && ar.getApplicationResourceUsageReport().getUsedResources() != null) {
                vc += ar.getApplicationResourceUsageReport().getUsedResources().getVirtualCores();
            }
		}
        if (queueInfo.getChildQueues() != null) for (QueueInfo qi : queueInfo.getChildQueues()) {
            vc += calcUsedVirtualCores(qi);
		}
		return vc;
	}

	private void printQueueCapacity(List<NodeReport> clusterNodeReports, QueueInfo queueInfo, List<QueueInfo> rootQueues) throws IOException, YarnException {
        long clusterMemory = availableMemory(clusterNodeReports);
        long clusterVirtualCores = availableVirtualCores(clusterNodeReports);
		String queueToFind = queueInfo.getQueueName();

        double queueCapacityMemory = calcFractionalCapability(clusterMemory, rootQueues, queueToFind);
        double queueCapacityVirtualCores = calcFractionalCapability(clusterVirtualCores, rootQueues, queueToFind);
        long queueUsedMemory = calcUsedMemory(queueInfo);
        long queueUsedVirtualCores = calcUsedVirtualCores(queueInfo);
        System.out.println("Queue '" + queueInfo.getQueueName() + "' approximate utilization: "
                + prettyPrintMb(queueUsedMemory, false) + " / " + prettyPrintMb((int)queueCapacityMemory, false) + " GB used, "
                + queueUsedVirtualCores + " / " + (int)queueCapacityVirtualCores + " vcores used");

        long queueAvailableMemory = (long)queueCapacityMemory - queueUsedMemory;
        if (queueAvailableMemory < 0) {
            queueAvailableMemory = 0;
        }
        this.queueAvailableMemory = queueAvailableMemory;

        long queueAvailableVirtualCores = (long)queueCapacityVirtualCores - queueUsedVirtualCores;
        if (queueAvailableVirtualCores < 0) {
            queueAvailableVirtualCores = 0;
        }
        this.queueAvailableVirtualCores = queueAvailableVirtualCores;
	}

    private void printBar() {
        System.out.println("----------------------------------------------------------------------");
    }

    private int numPrinted = 0;

    private void printErrorDiagnosis(String s) {
        System.out.println("ERROR:   " + s);
        numPrinted++;
    }

    private void printWarningDiagnosis(String s) {
        System.out.println("WARNING: " + s);
        numPrinted++;
    }

    private void printDiagnosis(List<NodeReport> clusterNodeReports) throws IOException, YarnException {
        printBar();
        System.out.println("");
        System.out.println("Requested worker containers: " + this.numNodes);
        System.out.println("Worker vcores per container: " + this.nodeVirtualCores);
        System.out.println("ApplicationMaster vcores: " + this.applicationMasterVirtualCores);
        System.out.println("Total estimated vcore request: " + ((long) this.numNodes * this.nodeVirtualCores + this.applicationMasterVirtualCores));
        System.out.println("Estimated queue available vcores: " + this.queueAvailableVirtualCores);
        System.out.println("Estimated queue available memory: " + prettyPrintMb(this.queueAvailableMemory));
        System.out.println("");

        // Check if the requested container size exceeds the available space on any node.
        {
            boolean containerFitsOnSomeNode = false;
            for (NodeReport node: clusterNodeReports) {
                if (node.getNodeState() == NodeState.RUNNING) {
                    Resource capability = node.getCapability();
                    if (capability == null) continue;
                    int m = capability.getMemory();
                    if (m >= this.nodeMemoryMb) {
                        containerFitsOnSomeNode = true;
                        break;
                    }
                }
            }

            if (! containerFitsOnSomeNode) {
                printErrorDiagnosis("Job container memory request (" + prettyPrintMb(nodeMemoryMb) + ") does not fit on any YARN cluster node");
            }
        }

        // Check if the requested job cumulative container size exceeds the space in the cluster.
        {
            long n = availableMemory(clusterNodeReports);

            long jobMb = (long) this.numNodes * this.nodeMemoryMb;
            if (n < jobMb) {
                printErrorDiagnosis("Job memory request (" + prettyPrintMb(jobMb) + ") exceeds available YARN cluster memory (" + prettyPrintMb(n) + ")");
            }
        }

        // Check if there are at least N virtual cores available in the cluster.
        {
            long n = availableVirtualCores(clusterNodeReports);

            long jobVirtualCores = (long) this.numNodes * this.nodeVirtualCores + this.applicationMasterVirtualCores;
            if (n < jobVirtualCores) {
                printErrorDiagnosis("YARN cluster available virtual cores (" + n + ") < requested H2O containers (" + jobVirtualCores + ")");
            }
        }

        // Queue availability messages should just be warnings, rather than
        // errors, since we don't *really* know how the scheduler allocates
        // resources, and the calculation of partial capacities is a best
        // guess.  But the info could be helpful, so show it.

        // Check if the requested job cumulative container size exceeds the space in the queue.
        {
            long jobMb = (long) this.numNodes * this.nodeMemoryMb;
            if (this.queueAvailableMemory < jobMb) {
                printWarningDiagnosis("Job memory request (" + prettyPrintMb(jobMb) + ") exceeds queue available memory capacity (" + prettyPrintMb(this.queueAvailableMemory) + ")");
            }
        }

        // Check if the requested job cumulative virtual cores exceeds the space in the queue.
        {
            long jobVirtualCores = (long) this.numNodes * this.nodeVirtualCores + this.applicationMasterVirtualCores;
            if (this.queueAvailableVirtualCores < jobVirtualCores) {
                long jobMb = (long) this.numNodes * this.nodeMemoryMb;
                String memoryNote = this.queueAvailableMemory >= jobMb
                        ? "Memory capacity appears sufficient. YARN vcore contention is likely preventing H2O worker containers from starting."
                        : "Memory and vcore capacity may both be constraining the request.";
                printWarningDiagnosis("Estimated queue vcore availability is insufficient for the requested H2O containers.\n"
                        + "Requested worker vcores: " + ((long) this.numNodes * this.nodeVirtualCores) + "\n"
                        + "Requested ApplicationMaster vcores: " + this.applicationMasterVirtualCores + "\n"
                        + "Total requested vcores: " + jobVirtualCores + "\n"
                        + "Estimated queue available vcores: " + this.queueAvailableVirtualCores + "\n\n"
                        + memoryNote + "\n"
                        + "Suggested actions: review mapreduce.map.cpu.vcores, yarn.app.mapreduce.am.resource.cpu-vcores, the requested worker count, and queue utilization.");
            }
        }

        if ((numNodesStarted > 0) && (numNodesStarted < numNodes)) {
            printErrorDiagnosis("Only " + numNodesStarted + " out of the requested " + numNodes + " worker containers were started due to YARN cluster resource limitations");
        }

        // Default warning.
        if (numPrinted == 0) {
            System.out.println("ERROR: Unable to start any H2O nodes; please contact your YARN administrator.");
            System.out.println("");
            System.out.println("       A common cause for this is the requested container size (" + prettyPrintMb(this.nodeMemoryMb) + ")");
            System.out.println("       exceeds the following YARN settings:");
            System.out.println("");
            System.out.println("           yarn.nodemanager.resource.memory-mb");
            System.out.println("           yarn.scheduler.maximum-allocation-mb");
        }

        System.out.println("");
        printBar();
    }
}
