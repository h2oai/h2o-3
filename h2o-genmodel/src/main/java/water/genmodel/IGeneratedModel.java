package water.genmodel;

import java.util.Map;

import hex.genmodel.annotations.CG;

/**
 * A generic interface to access generated models.
 */
public interface IGeneratedModel {
    /** Returns model's unique identifier. */
    @CG(delegate="#getUUID")
    String getUUID();

    /** Returns number of columns used as input for training (i.e., exclude response column). */
    int getNumCols();

    /** The names of columns used in the model. It contains names of input columns and a name of response column. */
    String[] getNames();

    /** The name of the response column. */
    @Deprecated
    String getResponseName();

    /** Returns an index of the response column inside getDomains(). */
    int getResponseIdx();

    /** Get number of classes in in given column.
     * Return number greater than zero if the column is categorical
     * or -1 if the column is numeric. */
    int getNumClasses(int i);

    /** Return a number of classes in response column.
     *
     * @return number of response classes
     * @throws java.lang.UnsupportedOperationException in case that it is call on non-classifier model.
     */
    int getNumResponseClasses();

    /** @return true if this model represents a classifier, else it is used for regression. */
    boolean isClassifier();

    /** @return true if this model represents an AutoEncoder. */
    boolean isAutoEncoder();

    /** Predict the given row and return prediction.
     *
     * @param data row holding the data. Ordering should follow ordering of columns returned by getNames()
     * @param preds allocated array to hold a prediction
     * @return returned preds parameter filled by prediction
     * @deprecated use method IGenModel#score0
     */
    @Deprecated
    float[] predict(double[] data, float[] preds);

    /** Predict the given row and return prediction using given number of iterations (e.g., number of trees from forest).
    *
    * @param data row holding the data. Ordering should follow ordering of columns returned by getNames()
    * @param preds allocated array to hold a prediction
    * @param maxIters maximum number of iterations to use during predicting process
    * @return returned preds parameter filled by prediction
    */
    float[] predict(double[] data, float[] preds, int maxIters);

    /** Gets domain of given column.
     * @param name column name
     * @return return domain for given column or null if column is numeric.
     */
    String[] getDomainValues(String name);
    /**
     * Returns domain values for i-th column.
     * @param i index of column
     * @return domain for given categorical column or null if columns contains numeric value
     */
    String[] getDomainValues(int i);

    /** Returns domain values for all columns including response column. */
    String[][] getDomainValues();

    /** Returns index of column with give name or -1 if column is not found. */
    int getColIdx(String name);

    /** Maps given column's categorical to integer used by this model.
     * Returns -1 if mapping is not found. */
    int mapEnum(int colIdx, String categoricalValue);

    /**
     * Returns the expected size of preds array which is passed to {@link #predict(double[], float[])} function.
     * @return expected size of preds array
     */
    int getPredsSize();
}
