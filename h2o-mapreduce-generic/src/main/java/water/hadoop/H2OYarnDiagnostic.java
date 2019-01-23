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
    // These are known up front.
    Configuration conf;
    String applicationId;
    String queueName;
    int numNodes;
    int nodeMemoryMb;
    int nodeVirtualCores;
    int numNodesStarted;

    // Fill these in as we process the queue information, and remember the
    // answers for printing helpful diagnostics.
    int queueAvailableMemory;
    int queueAvailableVirtualCores;

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.out.println("usage:  applicationId queueName numNodes nodeMemoryMb numNodesStarted");
            System.exit(1);
        }

        diagnose(args[0], args[1], Integer.valueOf(args[2]), Integer.valueOf(args[3]), Integer.valueOf(args[4]));
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
        H2OYarnDiagnostic client = new H2OYarnDiagnostic();
        client.applicationId = applicationId;
        client.queueName = queueName;
        client.numNodes = numNodes;
        client.nodeMemoryMb = nodeMemoryMb;
        client.nodeVirtualCores = 1;
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

    private String prettyPrintMb(int mb, boolean printUnits) {
        double gb = (double)mb / 1024.0;
        String s = String.format("%.1f", gb);
        if (printUnits) {
            s += " GB";
        }
        return s;
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

	private int calcUsedMemory(QueueInfo queueInfo) {
		if (queueInfo == null) {
			return 0;
		}

		int memory = 0;
		for (ApplicationReport ar : queueInfo.getApplications()) {
			memory += ar.getApplicationResourceUsageReport().getUsedResources().getMemory();
		}
		for (QueueInfo qi : queueInfo.getChildQueues()) {
			memory += calcUsedMemory(qi);
		}
		return memory;
	}

	private int calcUsedVirtualCores(QueueInfo queueInfo) {
		if (queueInfo == null) {
			return 0;
		}

		int vc = 0;
		for (ApplicationReport ar : queueInfo.getApplications()) {
			vc += ar.getApplicationResourceUsageReport().getUsedResources().getVirtualCores();
		}
		for (QueueInfo qi : queueInfo.getChildQueues()) {
			vc += calcUsedMemory(qi);
		}
		return vc;
	}

	private void printQueueCapacity(List<NodeReport> clusterNodeReports, QueueInfo queueInfo, List<QueueInfo> rootQueues) throws IOException, YarnException {
		int clusterMemory = 0;
		int clusterVirtualCores = 0;
		for (NodeReport node : clusterNodeReports) {
			if (node.getNodeState() == NodeState.RUNNING) {
				Resource capability = node.getCapability();
				clusterMemory += capability.getMemory();
				clusterVirtualCores += capability.getVirtualCores();
			}
		}

		String queueToFind = queueInfo.getQueueName();

        double queueCapacityMemory = calcFractionalCapability(clusterMemory, rootQueues, queueToFind);
        double queueCapacityVirtualCores = calcFractionalCapability(clusterVirtualCores, rootQueues, queueToFind);
        int queueUsedMemory = calcUsedMemory(queueInfo);
        int queueUsedVirtualCores = calcUsedVirtualCores(queueInfo);
        System.out.println("Queue '" + queueInfo.getQueueName() + "' approximate utilization: "
                + prettyPrintMb(queueUsedMemory, false) + " / " + prettyPrintMb((int)queueCapacityMemory, false) + " GB used, "
                + queueUsedVirtualCores + " / " + (int)queueCapacityVirtualCores + " vcores used");

        int queueAvailableMemory = (int)queueCapacityMemory - queueUsedMemory;
        if (queueAvailableMemory < 0) {
            queueAvailableMemory = 0;
        }
        this.queueAvailableMemory = queueAvailableMemory;

        int queueAvailableVirtualCores = (int)queueCapacityVirtualCores - queueUsedVirtualCores;
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

        // Check if the requested container size exceeds the available space on any node.
        {
            boolean containerFitsOnSomeNode = false;
            for (NodeReport node: clusterNodeReports) {
                if (node.getNodeState() == NodeState.RUNNING) {
                    Resource capability = node.getCapability();
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
            int n = 0;
            for (NodeReport node: clusterNodeReports) {
                if (node.getNodeState() == NodeState.RUNNING) {
                    Resource capability = node.getCapability();
                    n += capability.getMemory();
                }
            }

            int jobMb = this.numNodes * this.nodeMemoryMb;
            if (n < jobMb) {
                printErrorDiagnosis("Job memory request (" + prettyPrintMb(jobMb) + ") exceeds available YARN cluster memory (" + prettyPrintMb(n) + ")");
            }
        }

        // Check if there are at least N virtual cores available in the cluster.
        {
            int n = 0;
            for (NodeReport node: clusterNodeReports) {
                if (node.getNodeState() == NodeState.RUNNING) {
                    Resource capability = node.getCapability();
                    n += capability.getVirtualCores();
                }
            }

            int jobVirtualCores = this.numNodes * this.nodeVirtualCores;
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
            int jobMb = this.numNodes * this.nodeMemoryMb;
            if (this.queueAvailableMemory < jobMb) {
                printWarningDiagnosis("Job memory request (" + prettyPrintMb(jobMb) + ") exceeds queue available memory capacity (" + prettyPrintMb(this.queueAvailableMemory) + ")");
            }
        }

        // Check if the requested job cumulative virtual cores exceeds the space in the queue.
        {
            int jobVirtualCores = this.numNodes * this.nodeVirtualCores;
            if (this.queueAvailableVirtualCores < jobVirtualCores) {
                printWarningDiagnosis("Job virtual cores request (" + jobVirtualCores + ") exceeds queue available virtual cores capacity (" + this.queueAvailableVirtualCores + ")");
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
