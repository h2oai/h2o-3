package water.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationResourceUsageReport;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.NodeState;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.api.records.Resource;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class H2OYarnDiagnosticTest {
   
    @Test
    public void aggregatesVcoresThroughNestedQueues() {
        QueueInfo leaf = mock(QueueInfo.class);
        QueueInfo child = mock(QueueInfo.class);
        QueueInfo root = mock(QueueInfo.class);

        ApplicationReport leafApp = application(7);
        ApplicationReport childApp = application(3);
        ApplicationReport rootApp = application(2);

        when(leaf.getApplications()).thenReturn(
            Collections.singletonList(leafApp)
        );
        when(leaf.getChildQueues()).thenReturn(
            Collections.<QueueInfo>emptyList()
        );

        when(child.getApplications()).thenReturn(
            Collections.singletonList(childApp)
        );
        when(child.getChildQueues()).thenReturn(
            Collections.singletonList(leaf)
        );

        when(root.getApplications()).thenReturn(
            Collections.singletonList(rootApp)
        );
        when(root.getChildQueues()).thenReturn(
            Collections.singletonList(child)
        );

        assertEquals(12, H2OYarnDiagnostic.calcUsedVirtualCores(root));
    }

    @Test 
    public void usesConfiguredWorkerAndAmVcoresWithDefaults() {
        Configuration conf = new Configuration(false);
        conf.setInt("mapreduce.map.cpu.vcores", 4);
        conf.setInt("yarn.app.mapreduce.am.resource.cpu-vcores", 6);
        assertEquals(26, H2OYarnDiagnostic.totalRequestedVirtualCores(5, conf));
        assertEquals(2, H2OYarnDiagnostic.totalRequestedVirtualCores(1, new Configuration(false)));
    }

    @Test 
    public void subtractsUsedResourcesAndHandlesMissingUsage() {
        NodeReport used = mock(NodeReport.class);
        when(used.getNodeState()).thenReturn(NodeState.RUNNING);
        when(used.getCapability()).thenReturn(Resource.newInstance(1000, 10));
        when(used.getUsed()).thenReturn(Resource.newInstance(400, 4));
        NodeReport missing = mock(NodeReport.class);
        when(missing.getNodeState()).thenReturn(NodeState.RUNNING);
        when(missing.getCapability()).thenReturn(Resource.newInstance(500, 5));
        when(missing.getUsed()).thenReturn(null);
        assertEquals(1100, H2OYarnDiagnostic.availableMemory(Arrays.asList(used, missing)));
        assertEquals(11, H2OYarnDiagnostic.availableVirtualCores(Arrays.asList(used, missing)));
    }

    private static ApplicationReport application(int vcores) {
        ApplicationReport app = mock(ApplicationReport.class);
        ApplicationResourceUsageReport usage = mock(ApplicationResourceUsageReport.class);
        when(app.getApplicationResourceUsageReport()).thenReturn(usage);
        when(usage.getUsedResources()).thenReturn(Resource.newInstance(0, vcores));
        return app;
    }
}
