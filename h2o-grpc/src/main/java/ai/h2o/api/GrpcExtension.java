package ai.h2o.api;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import water.AbstractH2OExtension;
import water.H2O;
import water.util.Log;

import java.io.IOException;

/**
 */
public class GrpcExtension extends AbstractH2OExtension {
  private int grpcPort = 0;  // 0 means that GRPC service should not start
  private Server netty;

  @Override
  public String getExtensionName() {
    return "GRPC";
  }

  @Override
  public void printHelp() {
    System.out.println(
        "\nGRPC extension:\n" +
        "    -grpc_port\n" +
        "          Port number on which to start the GRPC service. If not \n" +
        "          specified, GRPC service will not be started."
    );
  }

  @Override
  public String[] parseArguments(String[] args) {
    for (int i = 0; i < args.length - 1; i++) {
      H2O.OptString s = new H2O.OptString(args[i]);
      if (s.matches("grpc_port")) {
        grpcPort = s.parseInt(args[i + 1]);
        String[] new_args = new String[args.length - 2];
        System.arraycopy(args, 0, new_args, 0, i);
        System.arraycopy(args, i + 2, new_args, i, args.length - (i + 2));
        return new_args;
      }
    }
    return args;
  }

  @Override
  public void validateArguments() {
    if (grpcPort < 0 || grpcPort >= 65536) {
      H2O.parseFailed("Invalid port number: " + grpcPort);
    }
  }

  @Override
  public void onLocalNodeStarted() {
    if (grpcPort != 0) {
      ServerBuilder sb = ServerBuilder.forPort(grpcPort);
      RegisterGrpcApi.registerWithServer(sb);
      netty = sb.build();
      try {
        netty.start();
        Log.info("Started GRPC server on localhost:" + grpcPort);
      } catch (IOException e) {
        netty = null;
        throw new RuntimeException("Failed to start the GRPC server on port " + grpcPort, e);
      }
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          if (netty != null) {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            netty.shutdown();
            System.err.println("*** server shut down");
          }
        }
      });
    }
  }

}
