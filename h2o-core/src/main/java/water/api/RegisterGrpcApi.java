package water.api;

import ai.h2o.api.proto.core.JobService;
import io.grpc.ServerBuilder;


/**
 */
public abstract class RegisterGrpcApi {

  public static void registerWithServer(ServerBuilder sb) {
    sb.addService(new JobService());
  }
}
