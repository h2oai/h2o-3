package hex.segments;

import water.Iced;

class SegmentModelsStats extends Iced<SegmentModelsStats> {
  int _succeeded;
  int _failed;

  void reduce(SegmentModelsStats other) {
    _succeeded += other._succeeded;
    _failed += other._failed;
  }

  @Override
  public String toString() {
    return "succeeded=" + _succeeded + ", failed=" + _failed;
  }

}
