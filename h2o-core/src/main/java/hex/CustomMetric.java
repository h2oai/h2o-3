package hex;

import water.Iced;

public class CustomMetric extends Iced<CustomMetric> {

  public static final CustomMetric EMPTY = new CustomMetric(null, Double.NaN);

  public final String name;
  public final double value;

  public CustomMetric(String name, double value) {
    this.name = name;
    this.value = value;
  }

  public static CustomMetric from(String name, double value) {
    return new CustomMetric(name, value);
  }

  public boolean isValid() {
    return name != null;
  }
}
