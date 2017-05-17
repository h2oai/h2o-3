package ai.h2o.api;

import ai.h2o.api.proto.core.Error;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import water.util.Log;

import java.lang.reflect.Method;

/**
 *
 */
public abstract class GrpcUtils {

  public static <T extends Message> void sendError(Throwable e, StreamObserver<T> responseObserver, Class<T> clz) {
    try {
      Method m = clz.getDeclaredMethod("newBuilder");
      Message.Builder builder = (Message.Builder) m.invoke(null);
      sendError(e, responseObserver, builder);
    } catch (Exception e2) {
      Log.err("GRPC error, unable to pass to client:");
      Log.err(e2);
      Log.err("caused by");
      Log.err(e);
      throw new RuntimeException(e2.getMessage(), e);
    }
  }

  public static <T extends Message> void sendError(
      Throwable e, StreamObserver<T> responseObserver, Message.Builder builder
  ) {
    try {
      Method em = builder.getClass().getDeclaredMethod("setError", Error.class);
      em.invoke(builder, buildError(e, 0));

      responseObserver.onNext((T) builder.build());
      responseObserver.onCompleted();
    } catch (Exception e2) {
      Log.err("GRPC error, unable to pass to client:");
      Log.err(e2);
      Log.err("caused by");
      Log.err(e);
      throw new RuntimeException(e2.getMessage(), e);
    }
  }


  public static Error buildError(Throwable e, int depth) {
    Error.Builder eb = Error.newBuilder();
    eb.setName(e.getClass().getSimpleName());
    if (e.getMessage() != null) {
      eb.setMessage(e.getMessage());
    }
    StringBuilder sb = new StringBuilder();
    for (StackTraceElement st: e.getStackTrace()) {
      sb.append(st.toString()).append("\n");
    }
    eb.setStacktrace(sb.toString());
    if (e.getCause() != null && depth < 3) {
      eb.setCause(buildError(e.getCause(), depth + 1));
    }
    return eb.build();
  }

}
