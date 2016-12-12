package hex.deeplearning;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * Deep learning data column. Will be replaces with Column once it's merged
 * Created by vpatryshev on 12/6/16.
 */
public class DlColumn<T> implements Serializable {
  final public String name;
  final public List<T> data;
  public long size() { return data.size(); }

  public DlColumn(String name, List<T> data) {
    this.name = name;
    this.data = data;
  }

  public DlColumn(String name, T[] data) {
    this.name = name;
    this.data = Arrays.asList(data);
  }
}
