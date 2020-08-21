package hex.genmodel.algos.tree;

public class ConvertTreeOptions {
  static final ConvertTreeOptions DEFAULT = new ConvertTreeOptions();

  final boolean _checkTreeConsistency;

  public ConvertTreeOptions() {
    this(false);
  }
  
  private ConvertTreeOptions(boolean checkTreeConsistency) {
    _checkTreeConsistency = checkTreeConsistency;
  }

  /**
   * Performs a self-check on each converted tree. Inconsistencies are reported in the log.
   * @return a new instance of the options object with consistency-check flag enabled 
   */
  public ConvertTreeOptions withTreeConsistencyCheckEnabled() {
    return new ConvertTreeOptions(true);
  }

  @Override
  public String toString() {
    return "ConvertTreeOptions{" +
            "_checkTreeConsistency=" + _checkTreeConsistency +
            '}';
  }
}
