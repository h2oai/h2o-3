package hex;

/**
 * interface grouping the parameters needed to support Cross-validation.
 */
public interface CVSupport {

  enum FoldAssignmentScheme {
    AUTO, Random, Modulo, Stratified
  }
  
  /**
   * @return true iff the current object is a CV model.
   */
  boolean isCVModel();

  /**
   * @return the total amount of folds is using "arithmetic" folding, or -1 if no folding or for column-based folding.
   * @see {@link #getFoldColumn()}
   */
  int getNFolds();

  /**
   * @return the fold assignment scheme if any, or null if none. 
   */
  FoldAssignmentScheme getFoldAssignment();

  /**
   * @return the name of the fold column if any, or null if none.
   */
  String getFoldColumn();
  
  /**
   * @return the current holdout fold (out of {@link #getTotalFolds()}), or -1 if none.
   */
  int getHoldoutFold();

  /**
   * @return the total amount of folds, or -1 if none.
   */
  int getTotalFolds();
  
}
