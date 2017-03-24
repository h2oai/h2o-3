package ai.h2o.api;

import ai.h2o.api.proto.core.ClusterService;
import ai.h2o.api.proto.core.JobService;
import ai.h2o.api.proto.frames.CreateFrameService;
import io.grpc.ServerBuilder;


/**
 */
abstract class RegisterGrpcApi {

  static void registerWithServer(ServerBuilder sb) {
    sb.addService(new ClusterService());
    sb.addService(new JobService());
    sb.addService(new CreateFrameService());
  }
}
