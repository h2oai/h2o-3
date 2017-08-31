package hex.genmodel.algos.deeplearning;

import java.io.Serializable;

public class ActivationUtils {
  // drived from GLMMojoModel
  public interface ActivationFunctions extends Serializable {
    double[] eval(double[] x, double drop_out_ratio, int maxOutk);  // for MaxoutDropout
  }

  public static class LinearOut implements ActivationFunctions {
    public double[] eval(double[] input, double drop_out_ratio, int maxOutk) {  // do nothing
      return input;
    }
  }

  public static class SoftmaxOut implements ActivationFunctions {
    public double[] eval(double[] input, double drop_out_ratio, int maxOutk) {
      int nodeSize = input.length;
      double[] output = new double[nodeSize];
      double scaling = 0;
      double max = maxArray(input);

      for (int index = 0; index < nodeSize; index++) {
        output[index] =  Math.exp(input[index]-max);
        scaling += output[index];
      }

      for (int index = 0; index < nodeSize; index++)
        output[index] /= scaling;

      return output;
    }
  }

  public static double maxArray(double[] input) {
    assert ((input != null) && (input.length > 0)) : "Your array is empty.";

    double temp = input[0];
    for (int index = 0; index < input.length; index++)
      temp = temp<input[index]?input[index]:temp;

    return temp;
  }
  public static class ExpRectifierDropoutOut extends ExpRectifierOut {
    public double[] eval(double[] input, double drop_out_ratio, int maxOutk) {
      double[] output = super.eval(input, drop_out_ratio, maxOutk);
      applyDropout(output, drop_out_ratio, input.length);
      return output;
    }
  }

  public static double[] applyDropout(double[] input, double drop_out_ratio, int nodeSize) {
    if (drop_out_ratio > 0) {
      double multFact = 1 - drop_out_ratio;
      for (int index = 0; index < nodeSize; index++)
        input[index] *= multFact;
    }

    return input;
  }

  public static class ExpRectifierOut implements ActivationFunctions {
    public double[] eval(double[] input, double drop_out_ratio, int maxOutk) {
      int nodeSize = input.length;
      double[] output = new double[nodeSize];

      for (int index = 0; index < nodeSize; index++) {
        output[index] = input[index] >= 0 ? input[index] : Math.exp(input[index]) - 1;
      }
      return output;
    }
  }

  public static class RectifierOut implements ActivationFunctions {
    public double[] eval(double[] input, double drop_out_ratio, int maxOutk) {
      int nodeSize = input.length;
      double[] output = new double[nodeSize];

      for (int index = 0; index < nodeSize; index++)
        output[index] = 0.5f * (input[index] + Math.abs(input[index])); // clever.  Copied from Neurons.java

      return output;
    }
  }

  public static class RectifierDropoutOut extends RectifierOut {
    public double[] eval(double[] input, double drop_out_ratio, int maxOutk) {
      double[] output = super.eval(input, drop_out_ratio, maxOutk);
      applyDropout(output, drop_out_ratio, input.length);
      return output;
    }
  }

  public static class MaxoutDropoutOut extends MaxoutOut {
    public double[] eval(double[] input, double drop_out_ratio, int maxOutk) {
      double[] output = super.eval(input, drop_out_ratio, maxOutk);
      applyDropout(output, drop_out_ratio, output.length);
      return output;
    }
  }

  public static class MaxoutOut implements ActivationFunctions {
    public double[] eval(double[] input, double drop_out_ratio, int maxOutk) {
      int nodeSize = input.length/maxOutk;  // weight matrix is twice the size of other act functions
      double[] output = new double[nodeSize];

      for (int index=0; index < nodeSize; index++) {
        int countInd = index*maxOutk;
        double temp = input[countInd];
        for (int k = 0; k < maxOutk; k++)  {
          countInd += k;
          temp = temp > input[countInd]?temp:input[countInd];
        }
        output[index] = temp;
      }
      return output;
    }
  }

  public static class TanhDropoutOut extends TanhOut {
    public double[] eval(double[] input, double drop_out_ratio, int maxOutk) {
      int nodeSize = input.length;
      double[] output = super.eval(input, drop_out_ratio, maxOutk);
      applyDropout(output, drop_out_ratio, input.length);
      return output;
    }
  }

  public static class TanhOut implements ActivationFunctions {
    public double[] eval(double[] input, double drop_out_ratio, int maxOutk) {
      int nodeSize = input.length;
      double[] output = new double[nodeSize];

      for (int index=0; index < nodeSize; index++)
        output[index] = 1-2/(1+Math.exp(2*input[index]));

      return output;
    }
  }

}
