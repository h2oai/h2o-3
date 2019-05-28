package hex.genmodel;

public abstract class GenTransformer extends GenProducer {

  @Override
  public double[] produce(double[] row, double[] dataToProduce) {
    return transform(row, dataToProduce);
  }

  public abstract double[] transform(double[] row, double[] transformedData);
}
