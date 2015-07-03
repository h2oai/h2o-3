package hex.glm;

import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;

import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import water.DKV;
import water.Key;
import water.Scope;
import water.TestNGUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;

public class GLMBasic extends TestNGUtil {

	@DataProvider(name = "glmCases")
	public static Object[][] glmCases() {

		/**
		 * The first column of data is used to testing.
		 */
		final int firstColumn = 6;
		/**
		 * The number column of data is used to testing.
		 */
		final int totalColumn = 45;
		/**
		 * The first row of data is used to testing.
		 */
		final int firstRow = 4;
		final String filePath = "h2o-testng/src/test/resources/" + "glmCases.csv";

		Object[][] data = null;

		try {
			// read data from file
			List<String> lines = Files.readAllLines(find_test_file_static(filePath).toPath(), Charset.defaultCharset());

			// remove header
			lines.removeAll(lines.subList(0, firstRow));

			data = new Object[lines.size()][totalColumn];
			int r = 0;
			for (String line : lines) {
				String[] variables = line.trim().split(",");
				for (int c = 0; c < totalColumn; c++) {
					if (c + firstColumn < variables.length) {
						data[r][c] = variables[c + firstColumn];
					}
					else {
						data[r][c] = "";
					}
				}
				r++;
			}
		}
		catch (Exception ignore) {
			System.out.println("Cannot open file: " + filePath);
			ignore.printStackTrace();
		}

		return data;
	}

	@Test(dataProvider = "glmCases")
	public void basic(String testcase_id, String regression, String classification, String gaussian, String binomial,
			String poisson, String gamma, String auto, String irlsm, String lbfgs, String ignore_const_cols,
			String offset_column, String weights_column, String alpha, String lambda, String lambdaSearch,
			String standardize, String non_negative, String betaConstraints, String lowerBound, String upperBound,
			String beta_given, String intercept, String prior, String maxActivePredictors, String distribution,
			String regression_balanced_unbalanced, String rows, String columns, String train_rows_after_split,
			String validation_rows_after_split, String parse_types, String categorical, String sparse, String dense,
			String collinear_cols, String train_dataset_id, String train_dataset_filename, String validate_dataset_id,
			String validate_dataset_filename, String target, String target_type, String ignored_columns, String r,
			String scikit) {

		final String pathFile = "smalldata/testng/";

		// Set GLM parameters
		Family f = null;
		if (gaussian.equals("x")) {
			f = Family.gaussian;
		}
		else if (binomial.equals("x")) {
			f = Family.binomial;
		}
		else if (poisson.equals("x")) {
			f = Family.poisson;
		}
		else if (gamma.equals("x")) {
			f = Family.gamma;
		}

		GLMParameters params = null != f ? new GLMParameters(f) : new GLMParameters();
		if (irlsm.equals("x")) {
			params._solver = Solver.IRLSM;
		}
		else if (lbfgs.equals("x")) {
			params._solver = Solver.L_BFGS;
		}
		params._lambda = lambda.equals("") ? null : new double[] { Double.parseDouble(lambda) };
		params._alpha = alpha.equals("") ? null : new double[] { Double.parseDouble(alpha) };
		params._standardize = standardize.equals("x");
		params._lambda_search = lambdaSearch.equals("Y");

		params._ignore_const_cols = ignore_const_cols.equals("x");
		if (!"".equals(offset_column)) {
			params._offset_column = offset_column;
		}
		if (!"".equals(weights_column)) {
			params._weights_column = weights_column;
		}
		params._non_negative = non_negative.equals("x");
		params._intercept = intercept.equals("x");
		if (!"".equals(prior)) {
			params._prior = Double.parseDouble(prior);
		}
		if (!"".equals(maxActivePredictors)) {
			params._max_active_predictors = Integer.parseInt(maxActivePredictors);
		}
		if (!"".equals(ignored_columns)) {
			params._ignored_columns = new String[] { ignored_columns };
		}

		// params._beta_constraints
		// params._score_each_iteration = false;

		Frame train = null;
		Frame validate = null;
		if (!"".equals(train_dataset_filename) && !"".equals(validate_dataset_filename)) {
			// train
			File train_dataset = find_test_file_static(pathFile + train_dataset_filename);
			assert train_dataset.exists();
			NFSFileVec nfs_train_dataset = NFSFileVec.make(train_dataset);
			Key key_train_dataset = Key.make(train_dataset_id + ".hex");
			train = ParseDataset.parse(key_train_dataset, nfs_train_dataset._key);
			params._train = train._key;

			// validate
			File validate_dataset = find_test_file_static(pathFile + validate_dataset_filename);
			assert validate_dataset.exists();
			NFSFileVec nfs_validate_dataset = NFSFileVec.make(validate_dataset);
			Key key_validate_dataset = Key.make(train_dataset_id + ".hex");
			validate = ParseDataset.parse(key_validate_dataset, nfs_validate_dataset._key);
			params._valid = validate._key;

			params._response_column = target;
		}

		// Build the appropriate glm, given the above parameters
		Key modelKey = Key.make("model");
		GLM job = null;
		GLMModel model = null;
		Frame score = null;
		HashMap<String, Double> coef = null;

		Scope.enter();

		if ("".equals(train_dataset_filename) || "".equals(validate_dataset_filename)
				|| "newsgroup_train1".equals(train_dataset_id)) {
			// ignore those test case
			throw new SkipException("Skipping this exception");
		}
		else {
			job = new GLM(modelKey, "basic glm test", params);
			model = job.trainModel().get();

			model = DKV.get(modelKey).get();

			coef = model.coefficients();
			score = model.score(validate);
			// Assert.assertTrue(model.testJavaScoring(score, train, 1e-15));
		}

//		if (train != null) {
//			train.delete();
//		}
		if (validate != null) {
			validate.delete();
		}
		if (model != null)
			model.delete();
		if (job != null)
			job.remove();
		Scope.exit();

	}
}
