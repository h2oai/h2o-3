/**
 * <em>START HERE</em> for information about using H2O-generated POJO models.
 *
 * <h1>About generated models</h1>
 * <p></p>
 * H2O-generated POJO models are intended to be easily embedded in any Java environment.
 * The only compile and runtime dependency for a generated model is the h2o-genmodel.jar
 * file produced as the build output of this package.
 *
 * <h1>Extracting generated models from H2O</h1>
 * <p></p>
 * Generated models can be extracted from H2O in the following ways:
 * <ul>
 *   <li>From the H2O Flow Web UI: </li>
 *   When viewing a model, press the "Download POJO" button at the top of the model cell.
 *
 *   You can also Preview the POJO inside Flow, but it will only show the first thousand
 *   lines or so in the web browser, cutting off the bottom of large models.
 *
 *   <p></p>
 *   <li>From R:</li>
 *   The following code snippet shows an example of building a model and downloading its
 *   corresponding POJO from an R script.
 * <pre>
 * {@code
 * library(h2o)
 * h2o.init()
 * path = system.file("extdata", "prostate.csv", package = "h2o")
 * h2o_df = h2o.importFile(path)
 * h2o_df$CAPSULE = as.factor(h2o_df$CAPSULE)
 * model = h2o.glm(y = "CAPSULE",
 *                 x = c("AGE", "RACE", "PSA", "GLEASON"),
 *                 training_frame = h2o_df,
 *                 family = "binomial")
 * h2o.download_pojo(model)
 * }</pre>
 *
 *   <li>From Python:</li>
 *   The following code snippet shows an example of building a model and downloading its
 *   corresponding POJO from a Python script.
 * <pre>
 * {@code
 * import h2o
 * h2o.init()
 * path = h2o.system_file("prostate.csv")
 * h2o_df = h2o.import_file(path)
 * h2o_df['CAPSULE'] = h2o_df['CAPSULE'].asfactor()
 * model = h2o.glm(y = "CAPSULE",
 *                 x = ["AGE", "RACE", "PSA", "GLEASON"],
 *                 training_frame = h2o_df,
 *                 family = "binomial")
 * h2o.download_pojo(model)
 * }</pre>
 *
 *   <p></p>
 *   <li>From Java:</li>
 *   TODO: provide pointer of doing this directly from Java
 *
 *   <p></p>
 *   <li>From Sparkling Water:</li>
 *   TODO: provide pointer of doing this from Sparkling Water
 * </ul>
 *
 * <h1>Use cases</h1>
 * <p></p>
 * The following use cases are demonstrated with code examples:
 * <ul>
 *   <li>Reading new data from a CSV file and predicting on it</li>
 *   The {@link hex.genmodel.PredictCsv} class is used by the H2O test harness to make
 *   predictions on new data points.
 *
 *   <p></p>
 *   <li>Getting a new observation from a JSON request and returning a prediction</li>
 *   TODO: write an example for this
 *
 *   <p></p>
 *   <li>A user-defined-function called directly from hive</li>
 *   See <a href="https://github.com/h2oai/h2o-3-training" target="_blank">H2O-3 training github repository</a>
 * </ul>
 */
package hex.genmodel;
