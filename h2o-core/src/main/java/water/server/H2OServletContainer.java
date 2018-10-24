package water.server;

public interface H2OServletContainer {

  String getScheme();

  void start(String ip, int port) throws Exception;

  void stop() throws Exception;

  void acceptRequests();
}
