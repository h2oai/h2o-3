package hex.glm;

import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;

import java.lang.reflect.Field;
import java.lang.Class;

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
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import water.DKV;
import water.Key;
import water.Scope;
import water.TestNGUtil;
import water.fvec.FVecTest;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;
import water.serial.ObjectTreeBinarySerializer;

public class GLMBasic extends TestNGUtil {

	private static class Param {
		public String name = null;
		public String type = null;
		public boolean isRequired = false;
		public boolean isAutoSet = true;

		public Param(String name, String type) {
			this(name, type, false, true);
		}

		public Param(String name, String type, boolean isRequired, boolean isAutoSet) {
			this.name = name;
			this.type = type;
			this.isRequired = isRequired;
			this.isAutoSet = isAutoSet;
		}

		public static boolean parseBoolean(String value) {
			// can either be "x", "Y" for true and blank or "N" for false
			String bValue = value.trim().toLowerCase();
			if ("x".equals(bValue) || "y".equals(bValue)) {
				return true;
			}

			return false;
		}

		public boolean parseAndSet(GLMParameters glmParams, String value) {
			value = value.trim();
			Object v = null;

			// Only boolean has a special case: "" can be used as false.
			// So it is parsed here before other datatypes will be parsed.
			if ("boolean".equals(type)) {
				v = parseBoolean(value);
			} else {
				// is this a non-blank value? if it's NOT, no need to set: use Default value one!
				// TODO: if this is a required value then this input doesn't make sense!!!
				if ("".equals(value)) {
					//System.out.println("Value is empty, so ignore this");
					return false;
				}

				switch (type) {
					// case "boolean": this case has already been checked previously

					case "String":
						v = value;
						break;

					case "String[]":
						// TODO: may be we need to parse this one too!!!
						v = new String[] { value };
						break;

					case "double":
						v = Double.parseDouble(value);
						break;

					case "double[]":
						v = new double[] { Double.parseDouble(value) };
						break;

					case "int":
						v = Integer.parseInt(value);
						break;

					default:
						System.out.println("Unrecognized type: " + type);
						break;
				}
			}

			Class<?> clazz = glmParams.getClass();
			while (clazz != null) {
				try {
					Field field = clazz.getDeclaredField(name);
					//field.setAccessible(true); // is this needed?!?
					field.set(glmParams, v);
					return true;

				} catch (NoSuchFieldException e) {
					// not in this clazz, ok, fine... how about its Super one?
					clazz = clazz.getSuperclass();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
			return false;
		}

		public void print(GLMParameters glmParams) {
			Class<?> clazz = glmParams.getClass();

			while (clazz != null) {
				try {
					Field field = clazz.getDeclaredField(name);
					System.out.println(String.format("  %s: %s", name, field.get(glmParams)));
					return;

				} catch (NoSuchFieldException e) {
					// not in this clazz, ok, fine... how about its Super one?
					clazz = clazz.getSuperclass();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}

	private static class OptionsGroupParam {
		public List<String> optionsGroup = null;
		public Object[] values = null;

		public OptionsGroupParam(String[] optionsGroup, Object[] values) {
			this.optionsGroup = new ArrayList<String>(Arrays.asList(optionsGroup));
			this.values = values;
		}

		public Object getValue(String[] input) {
			for (String option: optionsGroup) {
				if (Param.parseBoolean(input[tcHeaders.indexOf(option)])) {
					return values[optionsGroup.indexOf(option)];
				}
			}
			return null;
		}
	}

	// below are single param which can set automatically to a GLM Object
	private static Param[] params = new Param[] {
			/*
			// TODO implement the use of these - or using OptionsGroupParam
			new Param("gaussian", "boolean", false, false),
			new Param("binomial", "boolean", false, false),
			new Param("poisson", "boolean", false, false),
			new Param("gamma", "boolean", false, false),

			new Param("irlsm", "boolean", false, false),
			new Param("lbfgs", "boolean", false, false),
			*/

			// autoset items
			new Param("_alpha", "double[]"),
			new Param("_lambda", "double[]"),
			new Param("_standardize", "boolean"),
			new Param("_lambda_search", "boolean"),
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

	private static OptionsGroupParam[] optionParams = new OptionsGroupParam[] {
			new OptionsGroupParam(
					new String[] {"gaussian", "binomial", "poisson", "gamma"},
					new Object[] {Family.gaussian, Family.binomial, Family.poisson, Family.gamma}),
			new OptionsGroupParam(
					new String[] {"irlsm", "lbfgs"},
					new Object[] {Solver.IRLSM, Solver.L_BFGS}),
	};

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

			"_ignore_const_cols",
			"_offset_column",
			"_weights_column",
			"_alpha",
			"_lambda",
			"_lambda_search",
			"_standardize",
			"_non_negative",
			"betaConstraints", // TODO: check this out
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
			"collinear_cols",

			// dataset files & ids
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

	@DataProvider(name = "glmCases")
	public static Object[][] glmCases() {

		/**
		 * The first row of data is used to testing.
		 */
		final int firstRow = 4;
		final String testcaseFilePath = "h2o-testng/src/test/resources/glmCases.csv";

		Object[][] data = null;

		try {
			// read data from file
			List<String> lines = Files.readAllLines(find_test_file_static(testcaseFilePath).toPath(),
					Charset.defaultCharset());

			// remove headers
			lines.removeAll(lines.subList(0, firstRow));

			data = new Object[lines.size()][8];
			int r = 0;

			for (String line : lines) {
				String[] variables = line.trim().split(",", -1);

				data[r][0] = variables[tcHeaders.indexOf("testcase_id")];
				data[r][1] = variables[tcHeaders.indexOf("test_description")];
				data[r][2] = toGLMParameters(variables);
				data[r][3] = variables[tcHeaders.indexOf("train_dataset_id")];
				data[r][4] = variables[tcHeaders.indexOf("train_dataset_filename")];
				data[r][5] = variables[tcHeaders.indexOf("validate_dataset_id")];
				data[r][6] = variables[tcHeaders.indexOf("validate_dataset_filename")];
				data[r][7] = variables; // raw input stored here for debugging

				r++;
			}
		}
		catch (IOException ignore) {
			System.out.println("Cannot open file: " + testcaseFilePath);
			ignore.printStackTrace();
		}

		return data;
	}

	private void _basic(GLMParameters glmParams, String trainDatasetId, String trainDatasetFilename,
						String validateDatasetId, String validateDatasetFilename, String[] rawInput) {
		final String pathFile = "smalldata/testng/";

		Frame trainFrame = null;
		Frame validateFrame = null;
		Frame betaConstraints = null;

		boolean isBetaConstraints = Param.parseBoolean(rawInput[tcHeaders.indexOf("betaConstraints")]);
		String responseColumnType = rawInput[tcHeaders.indexOf("response_column_type")];
		String responseColumn = rawInput[tcHeaders.indexOf("_response_column")];
		String lowerBound = rawInput[tcHeaders.indexOf("lowerBound")];
		String upperBound = rawInput[tcHeaders.indexOf("upperBound")];
		String betaGiven = rawInput[tcHeaders.indexOf("beta_given")];

		// create train dataset
		File train_dataset = find_test_file_static(pathFile + trainDatasetFilename);
		System.out.println("Is train dataset exist? If no, abort the test.\n");
		assert train_dataset.exists();
		NFSFileVec nfs_train_dataset = NFSFileVec.make(train_dataset);
		Key key_train_dataset = Key.make(trainDatasetId + ".hex");
		trainFrame = ParseDataset.parse(key_train_dataset, nfs_train_dataset._key);
		glmParams._train = trainFrame._key;

		// create validate dataset
		File validate_dataset = find_test_file_static(pathFile + validateDatasetFilename);
		assert validate_dataset.exists();
		NFSFileVec nfs_validate_dataset = NFSFileVec.make(validate_dataset);
		Key key_validate_dataset = Key.make(validateDatasetId + ".hex");
		validateFrame = ParseDataset.parse(key_validate_dataset, nfs_validate_dataset._key);
		glmParams._valid = validateFrame._key;

		// the beta constraints are represented in a frame with columns: "names", "lower_bounds", "upper_bounds",
		// and "beta_given" (optional). Each row corresponds to a predictor in the GLM. "names" contains the predictor
		// names, "lower"/"upper_bounds", are the lower and upper bounds of beta, and "beta_given" is some supplied
		// starting values for the betas.

		// You need to construct the beta constraints frame. In order to do so, you need to know the names of the
		// predictor columns, the upper and lower bounds (these are the same for each predictor), and (optionally)
		// the beta given (we don't provide this in the test case spreadsheet yet).

		// In this example, "AGE", "RACE", and "GLEASON" are the predictor names. -.5 is the lower bound and .5 is the
		// upper bound. Clearly, this information could change for each test case, so you'll have to generalize this
		// method.
		//Key betaConsKey = Key.make("beta_constraints");
		//FVecTest.makeByteVec(betaConsKey, "names, lower_bounds, upper_bounds\n"+
		//		"AGE, -.5, .5\n"+
		//		"RACE, -.5, .5\n"+
		//		"GLEASON, -.5, .5");
		//betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);
		//glmParams._beta_constraints = betaConstraints._key;

		if (isBetaConstraints) {
			// Here's an example of how to make the beta constraints frame.
			// First, represent the beta constraints frame as a string, for example:

			//"names, lower_bounds, upper_bounds\n"+
			//"AGE, -.5, .5\n"+
			//"RACE, -.5, .5\n"+
			//"GLEASON, -.5, .5"
			String betaConstraintsString = "names, lower_bounds, upper_bounds\n";
			List<String> predictorNames = Arrays.asList(trainFrame._names);
			//predictorNames.remove(glmParams._response_column); // remove the response column name. we only want predictors
			for(String name : predictorNames){
				if (!name.equals(glmParams._response_column)) {
					System.out.println(name + ", " + lowerBound + ", " + upperBound + "\n");
					betaConstraintsString += name + ", " + lowerBound + ", " + upperBound + "\n";
				}
			}
			Key betaConsKey = Key.make("beta_constraints");
			FVecTest.makeByteVec(betaConsKey, betaConstraintsString);
			betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);
			glmParams._beta_constraints = betaConstraints._key;
		}

		// Build the appropriate glm, given the above parameters
		Key modelKey = Key.make("model");
		GLM job = null;
		GLMModel model = null;
		Frame score = null;
		HashMap<String, Double> coef = null;

		Scope.enter();

		job = new GLM(modelKey, "basic glm test", glmParams);
		model = job.trainModel().get();

		//model = DKV.get(modelKey).get();

		coef = model.coefficients();

		try {
			score = model.score(validateFrame);
			// Assert.assertTrue(model.testJavaScoring(score, trainFrame, 1e-15));
			System.out.println("Test is passed.");
		}
		catch (IllegalArgumentException ex) {
			// can't predict testcase
			Assert.fail("Test is failed. It can't predict");
			ex.printStackTrace();
		}
		finally {
			if (trainFrame != null) {
				trainFrame.delete();
			}
			if (validateFrame != null) {
				validateFrame.delete();
			}
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

	private void printRawInput(String[] rawInput) {
		System.out.println("RAW INPUTS:");
		for (int i = 0; i < tcHeaders.size(); i++) {
			System.out.println(String.format("  %s: %s", tcHeaders.get(i), rawInput[i]));
		}
	}

	private boolean _validate(GLMParameters glmParams, String trainDatasetId, String trainDatasetFilename,
							  String validateDatasetId, String validateDatasetFilename, String[] rawInput) {
		if ("".equals(trainDatasetFilename.trim()) || "".equals(validateDatasetFilename.trim())) {
			System.out.println("[INVALID] Filename is blank!");
			printRawInput(rawInput);
			return false;
		}
		return true;
	}

	@Test(dataProvider = "glmCases")
	public void basic(String testcaseId, String testDescription, GLMParameters glmParams,
					  String trainDatasetId, String trainDatasetFilename, String validateDatasetId, String validateDatasetFilename,
					  String[] rawInput) {
		redirectStandardStreams();

		System.out.println(String.format("Testcase: %s", testcaseId));
		System.out.println(String.format("Description: %s", testDescription));
		System.out.println("GLM Params:");
		for (Param p: params) {
			if (p.isAutoSet) {
				p.print(glmParams);
			}
		}

		System.out.println("");
		System.out.println(String.format("Datasets: \n" +
				"Train Dataset ID:      %s\n" +
				"Train Dataset File:    %s\n" +
				"Validate Dataset ID:   %s\n" +
				"Validate Dataset File: %s\n",
				trainDatasetId, trainDatasetFilename, validateDatasetId, validateDatasetFilename));
		System.out.println("");

		try {
			if (_validate(glmParams, trainDatasetId, trainDatasetFilename, validateDatasetId, validateDatasetFilename, rawInput)) {
				_basic(glmParams, trainDatasetId, trainDatasetFilename, validateDatasetId, validateDatasetFilename, rawInput);
			} else {
				Assert.fail(String.format("INVALID INPUT - this test is skipped."));
			}
		} finally {

			//wait 100 mili-sec for output/error to be stored
			try {
				Thread.sleep(100);
			} catch(InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

			resetStandardStreams();
		}
	}


	private static GLMParameters toGLMParameters(String[] input) {
		// TODO: Start Ugly code ---
		String gaussian = input[tcHeaders.indexOf("gaussian")];
		String binomial = input[tcHeaders.indexOf("binomial")];
		String poisson = input[tcHeaders.indexOf("poisson")];
		String gamma = input[tcHeaders.indexOf("gamma")];
		String tweedie = input[tcHeaders.indexOf("tweedie")];


		String irlsm = input[tcHeaders.indexOf("irlsm")];
		String lbfgs = input[tcHeaders.indexOf("lbfgs")];

		Family f = null;
		if ("x".equals(gaussian)) {
			f = Family.gaussian;
		}
		else if ("x".equals(binomial)) {
			f = Family.binomial;
		}
		else if ("x".equals(poisson)) {
			f = Family.poisson;
		}
		else if ("x".equals(gamma)) {
			f = Family.gamma;
		}
		else if ("x".equals(tweedie)) {
			f = Family.tweedie;
		}

		GLMParameters glmParams = null != f ? new GLMParameters(f) : new GLMParameters();

		if ("x".equals(irlsm)) {
			glmParams._solver = Solver.IRLSM;
		}
		else if ("x".equals(lbfgs)) {
			glmParams._solver = Solver.L_BFGS;
		}
		// End Ugly code ---

		for (Param p: params) {
			if (p.isAutoSet) {
				p.parseAndSet(glmParams, input[tcHeaders.indexOf(p.name)]);
			}
		}

		return glmParams;
	}
}
