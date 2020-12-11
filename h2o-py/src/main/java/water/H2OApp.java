package water;

public class H2OApp {

  public static void main(String[] args) {
    throw new IllegalStateException("Client version of the library cannot be used to start a local H2O instance. Use h2o.connect(ip=\"hostname\", port=number) instead.");
  }

}
