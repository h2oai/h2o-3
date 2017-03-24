package ai.h2o.api.proto.core;

import ai.h2o.api.GrpcUtils;
import io.grpc.stub.StreamObserver;
import water.H2O;
import water.H2ONode;
import water.HeartBeat;
import water.Paxos;
import water.util.PrettyPrint;

import java.util.Date;

/**
 */
public class ClusterService extends ClusterGrpc.ClusterImplBase {

  @Override
  public void status(Empty request, StreamObserver<ClusterInfo> responseObserver) {
    try {
      int numUnhealthy = 0;
      long now = System.currentTimeMillis();
      for (H2ONode node: H2O.CLOUD.members()) {
        if (!node.isHealthy(now))
          numUnhealthy ++;
      }

      ClusterInfo cluster = ClusterInfo.newBuilder()
          .setVersion(H2O.ABV.projectVersion())
          .setBranchName(H2O.ABV.branchName())
          .setBuildNumber(H2O.ABV.buildNumber())
          .setBuildAge(PrettyPrint.toAge(H2O.ABV.compiledOnDate(), new Date()))

          .setName(H2O.ARGS.name)
          .setNumNodes(H2O.CLOUD.size())
          .setUptimeMs(System.currentTimeMillis() - H2O.START_TIME_MILLIS.get())
          .setHasConsensus(Paxos._commonKnowledge)
          .setIsLocked(Paxos._cloudLocked)
          .setNumNodesUnhealthy(numUnhealthy)
          .setClientMode(H2O.ARGS.client)

          .build();

      responseObserver.onNext(cluster);
      responseObserver.onCompleted();
    } catch (Throwable ex) {
      GrpcUtils.sendError(ex, responseObserver, ClusterInfo.class);
    }
  }

  @Override
  public void nodes(Empty request, StreamObserver<NodesInfo> responseObserver) {
    try {
      NodesInfo.Builder nb = NodesInfo.newBuilder();
      for (H2ONode node : H2O.CLOUD.members()) {
        HeartBeat heartBeat = node._heartbeat;

        NodeInfo.Builder nib = NodeInfo.newBuilder()
            .setAddress(node.getIpPortString())
            .setIsHealthy(node.isHealthy())
            .setPid(heartBeat._pid)
            .setNumCpus(heartBeat._num_cpus)
            .setNumCpusAllowed(heartBeat._cpus_allowed)

            .setSysLoad(heartBeat._system_load_average)
            .setMyCpuPercentage(Float.NaN)
            .setSysCpuPercentage(Float.NaN)
            .setGflops(heartBeat._gflops)

            .setMemoryBandwidth(heartBeat._membw)
            .setMemoryData(heartBeat.get_kv_mem())
            .setMemoryJava(heartBeat.get_pojo_mem())
            .setMemoryFree(heartBeat.get_free_mem())

            .setNumKeys(heartBeat._keys)
            .setNumThreads(heartBeat._nthreads)
            .setNumActiveRpcs(heartBeat._rpcs)
            .setNumOpenTcps(heartBeat._tcps_active)
            .setNumOpenFds(heartBeat._process_num_open_fds);

        int nValidPriorities = 0;
        for (int priority = 0; priority <= H2O.MAX_PRIORITY; priority++) {
          short fjq = heartBeat._fjqueue[priority];
          short fjt = heartBeat._fjthrds[priority];
          assert (fjq == -1) == (fjt == -1) : "fjqueue and fjthreads are out of sync at priority " + priority;
          if (fjq >= 0) {
            nib.addFjQueueCount(fjq);
            nib.addFjThreadCount(fjt);
            nValidPriorities ++;
          }
        }
        // This is just a sanity check that the number of queues was not increased
        // surreptitiously without verifying that client code still works
        assert nValidPriorities == 8 : "Unexpected number of priority queues";

        nb.addNode(nib);
      }

      responseObserver.onNext(nb.build());
      responseObserver.onCompleted();
    } catch (Throwable ex) {
      GrpcUtils.sendError(ex, responseObserver, NodesInfo.class);
    }
  }
}
