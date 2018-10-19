package water.server;

public interface H2oServletContainer {

  String getScheme();

  void start(String ip, int port) throws Exception;

  void stop() throws Exception;

  void acceptRequests();
}
