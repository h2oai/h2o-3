package hex.genmodel.easy;

import java.util.HashMap;

/**
 * Column name to column value mapping for a new row (aka data point, observation, sample) to predict.
 *
 * The purpose in life for objects of type RowData is to be passed to a predict method.
 *
 * RowData contains the input values for one new row.
 * In this context, "row" means a new data point (aka row, observation, sample) to make a prediction for.
 * Column names are mandatory (the column name is the key in the HashMap).
 *
 * <p></p>
 * Columns of different types are handled as follows:
 * <ul>
 * <li>
 *   For numerical columns, the value Object may either be a Double or a String.  If a String is passed, then
 *   Double.parseDouble() will be called on the String.
 * </li>
 * <li>
 *   For categorical (aka factor, enum) columns, the value Object must be a String with the same names as seen
 *   in the training data.
 *   It is not allowed to use new categorical (aka factor, enum) levels unseen during training (this will result
 *   in a {@link hex.genmodel.easy.exception.PredictUnknownCategoricalLevelException} when one of the predict methods
 *   is called).
 * </li>
 * </ul>
 *
 * <p></p>
 * Incorrect use of data types will result in a {@link hex.genmodel.easy.exception.PredictUnknownTypeException}
 * when one of the predict methods is called.
 *
 * <p></p>
 * For missing columns that are in the model, NA will be used by the predict methods.
 *
 * <p></p>
 * Extra columns that are not in the model are ignored by the predict methods.
 *
 * <p></p>
 * See the top-of-tree master version of this file <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-genmodel/src/main/java/hex/genmodel/easy/RowData.java" target="_blank">here on github</a>.
*/
public class RowData extends HashMap<String, Object> {
}
