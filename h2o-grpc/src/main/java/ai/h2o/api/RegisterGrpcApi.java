package ai.h2o.api;

import ai.h2o.api.proto.core.JobService;
import io.grpc.ServerBuilder;


/**
 */
abstract class RegisterGrpcApi {

  static void registerWithServer(ServerBuilder sb) {
    sb.addService(new JobService());
  }
}
