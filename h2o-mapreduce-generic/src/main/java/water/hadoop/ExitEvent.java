package water.hadoop;

public interface ExitEvent {
  void send(int status);
}
