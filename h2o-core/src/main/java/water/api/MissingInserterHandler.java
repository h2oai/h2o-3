package water.api;

public class MissingInserterHandler extends Handler {

  public MissingInserterV2 run(int version, MissingInserterV2 mis) {
    mis.createAndFillImpl().execImpl();
    return mis;
  }
}