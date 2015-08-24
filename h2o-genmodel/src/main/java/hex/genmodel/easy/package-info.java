/**
 * The easy prediction API for generated POJO models.
 *
 * Use as follows:
 * <ol>
 *   <li>Instantiate an EasyPredictModelWrapper</li>
 *   <li>Create a new row of data</li>
 *   <li>Call one of the predict methods</li>
 * </ol>
 *
 * <p></p>
 * Here is an example:
 *
 * <pre>
 *   {@code
 *   // Step 1.
 *   modelClassName = "your_pojo_model_downloaded_from_h2o";
 *   GenModel rawModel;
 *   rawModel = (GenModel) Class.forName(modelClassName).newInstance();
 *   EasyPredictModelWrapper model = new EasyPredictModelWrapper(rawModel);
 *
 *   // Step 2.
 *   RowData row = new RowData();
 *   row.put(new String("CategoricalColumnName"), new String("LevelName"));
 *   row.put(new String("NumericColumnName1"), new String("42.0"));
 *   row.put(new String("NumericColumnName2"), new Double(42.0));
 *
 *   // Step 3.
 *   BinomialModelPrediction p = model.predictBinomial(row);
 *   }
 * </pre>
 */
package hex.genmodel.easy;