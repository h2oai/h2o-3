package hex.glm;

import h2o.testng.utils.Dataset;
import h2o.testng.utils.FunctionUtils;
import h2o.testng.utils.OptionsGroupParam;
import h2o.testng.utils.Param;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import water.Key;
import water.Scope;
import water.TestNGUtil;
import water.fvec.FVecTest;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;

public class GLMBasic extends TestNGUtil {

	private static HashMap<String, Dataset> dataSetCharacteristic;
	
	// below are single param which can set automatically to a GLM Object
	private static Param[] params = new Param[] {
		
			new Param("_family", "Family", false, false),
			new Param("_solver", "Solver", false, false),

			// autoset items
			new Param("_alpha", "double[]"),
			new Param("_lambda", "double[]"),
			new Param("_standardize", "boolean"),
			new Param("_lambda_search", "boolean"),
			new Param("_nfolds", "int"),
			new Param("_ignore_const_cols", "boolean"),
			new Param("_offset_column", "String"),
			new Param("_weights_column", "String"),
			new Param("_non_negative", "boolean"),
			new Param("_intercept", "boolean"),
			new Param("_prior", "double"),
			new Param("_max_active_predictors", "int"),
			new Param("_ignored_columns", "String[]"),
			new Param("_response_column", "String"),
	};
	
	private static OptionsGroupParam familyOptionsParams = new OptionsGroupParam(
			new String[] {"gaussian", "binomial", "poisson", "gamma", "tweedie"},
			new Object[] {Family.gaussian, Family.binomial, Family.poisson, Family.gamma, Family.tweedie}
	); 
	
	private static OptionsGroupParam solverOptionsParams = new OptionsGroupParam(
			new String[] {"auto","irlsm", "lbfgs"},
			new Object[] {Solver.AUTO, Solver.IRLSM, Solver.L_BFGS}
	); 

	private static List<String> tcHeaders = new ArrayList<String>(Arrays.asList(
			"0",
			"1",
			"test_description",
			"testcase_id",

			// GLM Parameters
			"regression",
			"classification",
			"gaussian",
			"binomial",
			"poisson",
			"gamma",
			"tweedie",

			"auto",
			"irlsm",
			"lbfgs",
			
			"_nfolds",
			"fold_column",

			"_ignore_const_cols",
			"_offset_column",
			"_weights_column",
			"_alpha",
			"_lambda",
			"_lambda_search",
			"_standardize",
			"_non_negative",
			"betaConstraints",
			"lowerBound",
			"upperBound",
			"beta_given",
			"_intercept",
			"_prior",
			"_max_active_predictors",
			"distribution",
			"regression_balanced_unbalanced",
			"rows",
			"columns",
			"train_rows_after_split",
			"validation_rows_after_split",
			"parse_types",
			"categorical",
			"sparse",
			"dense",
			"high-dimensional data",
			"correlated",
			"collinear_cols",

			// dataset files & ids
			"dataset_directory",
			"train_dataset_id",
			"train_dataset_filename",
			"validate_dataset_id",
			"validate_dataset_filename",

			"_response_column",
			"response_column_type",
			"_ignored_columns",
			"r",
			"scikit"
	));

	@BeforeClass
	public void beforeClass() {

		dataSetCharacteristic = FunctionUtils.readDataSetCharacteristic();
	}
	
	@DataProvider(name = "glmCases")
	public static Object[][] glmCases() {

		/**
		 * The first row of data is used to testing.
		 */
		final int firstRow = 4;
		final String testcaseFilePath = "h2o-testng/src/test/resources/glmCases.csv";

		Object[][] data = null;
		List<String> lines = null;

		try {
			// read data from file
			lines = Files.readAllLines(find_test_file_static(testcaseFilePath).toPath(), Charset.defaultCharset());

		}
		catch (IOException ignore) {
			System.out.println("Cannot open file: " + testcaseFilePath);
			ignore.printStackTrace();
			return null;
		}

		// remove headers
		lines.removeAll(lines.subList(0, firstRow));

		data = new Object[lines.size()][8];
		int r = 0;

		for (String line : lines) {
			String[] variables = line.trim().split(",", -1);

			data[r][0] = variables[tcHeaders.indexOf("testcase_id")];
			data[r][1] = variables[tcHeaders.indexOf("test_description")];
			data[r][2] = variables[tcHeaders.indexOf("dataset_directory")];
			data[r][3] = variables[tcHeaders.indexOf("train_dataset_id")];
			data[r][4] = variables[tcHeaders.indexOf("validate_dataset_id")];
			data[r][5] = dataSetCharacteristic.get(variables[tcHeaders.indexOf("train_dataset_id")]);
			data[r][6] = dataSetCharacteristic.get(variables[tcHeaders.indexOf("validate_dataset_id")]);
			data[r][7] = variables;

			r++;
		}

		return data;
	}

	@Test(dataProvider = "glmCases")
	public void basic(String testcaseId, String testDescription, String datasetDirectory, String trainDatasetId,
			String validateDatasetId, Dataset trainDataset, Dataset validateDataset, String[] rawInput) {

		GLMParameters glmParams = null;

		redirectStandardStreams();

		System.out.println("");
		System.out.println(String.format(
				"Datasets: \n" +
				"Train Dataset ID:      %s\n" +
				"Validate Dataset ID:   %s\n", 
				trainDatasetId,	validateDatasetId));
		System.out.println("");

		try {
			String validateMessage = validate(trainDatasetId, trainDataset, rawInput);

			if (validateMessage != null) {
				System.out.println(validateMessage);
				Assert.fail(String.format(validateMessage));
			}
			else {
				glmParams = toGLMParameters(trainDatasetId, validateDatasetId, trainDataset, validateDataset, rawInput);

				_basic(testcaseId, glmParams, rawInput);
			}
		}
		finally {

			// wait 100 mili-sec for output/error to be stored
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

			resetStandardStreams();
		}
	}
	
	@AfterClass
	public void afterClass() {

		FunctionUtils.closeAllFrameInDatasetCharacteristic(dataSetCharacteristic);
	}

	private void _basic(String testcaseId, GLMParameters glmParams, String[] rawInput) {

		System.out.println(String.format("Testcase: %s", testcaseId));
		// System.out.println(String.format("Description: %s", testDescription));
		System.out.println("GLM Params:");
		for (Param p : params) {
			p.print(glmParams);
		}

		Frame trainFrame = null;
		Frame betaConstraints = null;

		// Build the appropriate glm, given the above parameters
		Key modelKey = Key.make("model");
		GLM job = null;
		GLMModel model = null;
		Frame score = null;
		HashMap<String, Double> coef = null;

		trainFrame = glmParams._train.get();
		if (glmParams._beta_constraints != null) {
			betaConstraints = glmParams._beta_constraints.get();
		}

		try {
			Scope.enter();

			System.out.println("Build model");
			job = new GLM(modelKey, "basic glm test", glmParams);

			System.out.println("Train model");
			model = job.trainModel().get();

			coef = model.coefficients();

			System.out.println("Predict testcase " + testcaseId);
			score = model.score(trainFrame);

			System.out.println("Predict success.");
			System.out.println("Testcase is passed.");
		}
		catch (IllegalArgumentException ex) {
			// can't predict testcase
			ex.printStackTrace();
			Assert.fail("Test is failed. It can't predict", ex);
		}
		finally {
			if (betaConstraints != null) {
				betaConstraints.delete();
			}
			if (model != null)
				model.delete();
			if (job != null)
				job.remove();
			Scope.exit();
		}
	}

	private static void printRawInput(String[] rawInput) {

		System.out.println("RAW INPUTS:");
		for (int i = 0; i < tcHeaders.size(); i++) {
			System.out.println(String.format("  %s: %s", tcHeaders.get(i), rawInput[i]));
		}
	}

	private static String validate(String train_dataset_id, Dataset train_dataset, String[] rawInput) {

		System.out.println("Validate Parameters object with testcase: " + rawInput[tcHeaders.indexOf("testcase_id")]);
		printRawInput(rawInput);

		String result = null;

		if (StringUtils.isEmpty(train_dataset_id)) {
			result = "Dataset files is empty";
		}
		else if (train_dataset == null) {
			result = "Dataset characteristic file is empty";
		}
		else if (!train_dataset.isAvailabel()) {
			result = "Dataset characteristic is not available";
		}
		else {
			result = Param.validateAutoSetParams(params, rawInput, tcHeaders);
		}

		if (result != null) {
			result = "[INVALID] " + result;
		}

		return result;
	}

	private static GLMParameters toGLMParameters(String trainDatasetId, String validateDatasetId, Dataset trainDataset,
			Dataset validateDataset, String[] rawInput) {

		System.out.println("Create GLMParameter object");

		GLMParameters glmParams = new GLMParameters();

		Family f = (Family) familyOptionsParams.getValue(rawInput, tcHeaders);
		Solver s = (Solver) solverOptionsParams.getValue(rawInput, tcHeaders);

		if (f != null) {
			System.out.println("Set _family: " + f);
			glmParams._family = f;
		}
		if (s != null) {
			System.out.println("Set _solver: " + s);
			glmParams._solver = s;
		}

		for (Param p : params) {
			if (p.isAutoSet) {
				p.parseAndSet(glmParams, rawInput[tcHeaders.indexOf(p.name)]);
			}
		}

		// set train/validate params
		Frame trainFrame = null;
		Frame validateFrame = null;

		try {

			System.out.println("Create train frame: " + trainDatasetId);
			trainFrame = trainDataset.getFrame();

			if (StringUtils.isNotEmpty(validateDatasetId) && validateDataset != null && validateDataset.isAvailabel()) {
				System.out.println("Create validate frame: " + validateDatasetId);
				validateFrame = validateDataset.getFrame();
			}
		}
		catch (Exception e) {
			if (trainFrame != null) {
				trainFrame.remove();
			}
			if (validateFrame != null) {
				validateFrame.remove();
			}
			throw e;
		}

		System.out.println("Set train frame");
		glmParams._train = trainFrame._key;

		if (validateFrame != null) {
			System.out.println("Set validate frame");
			glmParams._valid = validateFrame._key;
		}

		Frame betaConstraints = null;
		boolean isBetaConstraints = Param.parseBoolean(rawInput[tcHeaders.indexOf("betaConstraints")]);
		String lowerBound = rawInput[tcHeaders.indexOf("lowerBound")];
		String upperBound = rawInput[tcHeaders.indexOf("upperBound")];

		if (isBetaConstraints) {
			// Here's an example of how to make the beta constraints frame.
			// First, represent the beta constraints frame as a string, for example:

			// "names, lower_bounds, upper_bounds\n"+
			// "AGE, -.5, .5\n"+
			// "RACE, -.5, .5\n"+
			// "GLEASON, -.5, .5"
			String betaConstraintsString = "names, lower_bounds, upper_bounds\n";
			List<String> predictorNames = Arrays.asList(trainFrame._names);
			// predictorNames.remove(glmParams._response_column); // remove the response column name. we only want
			// predictors
			for (String name : predictorNames) {
				if (!name.equals(glmParams._response_column)) {
					if (trainFrame.vec(name).isEnum()) { // need coefficient names for each level of a categorical
															// column
						for (String level : trainFrame.vec(name).domain()) {
							betaConstraintsString += String
									.format("%s.%s,%s,%s\n", name, level, lowerBound, upperBound);
						}
					}
					else { // numeric columns only need one coefficient name
						betaConstraintsString += String.format("%s,%s,%s\n", name, lowerBound, upperBound);
					}
				}
			}
			Key betaConsKey = Key.make("beta_constraints");
			FVecTest.makeByteVec(betaConsKey, betaConstraintsString);
			betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);
			glmParams._beta_constraints = betaConstraints._key;
		}

		return glmParams;
	}
}
