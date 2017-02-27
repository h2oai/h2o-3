package water.userapi;

import water.fvec.Frame;

import java.util.Objects;

/**
 * Created by vpatryshev on 2/27/17.
 */
class TrainAndValid {
  final Frame train;
  final Frame valid;

  public TrainAndValid(Frame train, Frame valid) {
    this.train = train;
    this.valid = valid;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TrainAndValid)) return false;

    TrainAndValid that = (TrainAndValid) o;

    return Objects.equals(train, that.train) &&
        Objects.equals(valid, that.valid);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hashCode(train) + Objects.hashCode(valid);
  }

  @Override
  public String toString() {
    return "TrainAndValid{" +
        "train=" + train +
        ", valid=" + valid +
        '}';
  }
}
