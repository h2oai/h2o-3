package hex.tree.gbm;

import h2o.testng.utils.Dataset;
import h2o.testng.utils.FunctionUtils;
import h2o.testng.utils.OptionsGroupParam;
import h2o.testng.utils.Param;
import hex.Distributions.Family;
import hex.tree.gbm.GBMModel.GBMParameters;

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

import water.Scope;
import water.TestNGUtil;
import water.fvec.Frame;

public class GBMBasic extends TestNGUtil {

	@BeforeClass
	public void beforeClass() {

		dataSetCharacteristic = FunctionUtils.readDataSetCharacteristic();
	}

	@DataProvider(name = "gbmCases")
	public static Object[][] gbmCases() {

		/**
		 * The first row of data is used to testing.
		 */
		final int firstRow = 4;
		final String testcaseFilePath = "h2o-testng/src/test/resources/gbmCases.csv";
		final String negTestcaseFilePath = "h2o-testng/src/test/resources/gbmNegCases.csv";

		Object[][] data = null;
		List<String> lines = null;
		List<String> negLines = null;
		List<String> allLines = new ArrayList<String>();

		try {
			// read data from file
			//lines = Files.readAllLines(find_test_file_static(testcaseFilePath).toPath(), Charset.defaultCharset());
		}
		catch (Exception ignore) {
			System.out.println("Cannot open file: " + testcaseFilePath);
			ignore.printStackTrace();
		}

		try {
			// read data from negative file
			negLines = Files
					.readAllLines(find_test_file_static(negTestcaseFilePath).toPath(), Charset.defaultCharset());
		}
		catch (Exception ignore) {
			System.out.println("Cannot open file: " + negTestcaseFilePath);
			ignore.printStackTrace();
		}

		// remove headers and compile all lines
		if (lines != null) {
			allLines.addAll(lines.subList(firstRow, lines.size()));
		}
		if (negLines != null) {
			allLines.addAll(negLines.subList(firstRow, negLines.size()));
		}

		data = new Object[allLines.size()][7];
		int r = 0;
		for (String line : allLines) {
			String[] variables = line.trim().split(",", -1);

			data[r][0] = variables[tcHeaders.indexOf("testcase_id")];
			data[r][1] = variables[tcHeaders.indexOf("test_description")];			
			data[r][2] = variables[tcHeaders.indexOf("train_dataset_id")];
			data[r][3] = variables[tcHeaders.indexOf("validate_dataset_id")];
			data[r][4] = dataSetCharacteristic.get(variables[tcHeaders.indexOf("train_dataset_id")]);
			data[r][5] = dataSetCharacteristic.get(variables[tcHeaders.indexOf("validate_dataset_id")]);
			data[r][6] = variables;

			r++;
		}

		return data;
	}

	@Test(dataProvider = "gbmCases")
	public void basic(String testcase_id, String test_description, String train_dataset_id,
			String validate_dataset_id, Dataset train_dataset, Dataset validate_dataset, String[] rawInput) {

		GBMParameters gbmParams = null;

		redirectStandardStreams();

		try {
			String invalidMessage = validate(train_dataset_id, train_dataset, rawInput);

			if (invalidMessage != null) {
				System.out.println(invalidMessage);
				Assert.fail(String.format(invalidMessage));
			}
			else {
				gbmParams = toGBMParameters(train_dataset_id, validate_dataset_id, train_dataset, validate_dataset,
						rawInput);
				_basic(testcase_id, test_description, gbmParams, rawInput);
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

	private void _basic(String testcase_id, String test_description, GBMParameters parameter, String[] rawInput) {

		System.out.println(String.format("Testcase: %s", testcase_id));
		System.out.println(String.format("Description: %s", test_description));
		System.out.println("GBM Params:");
		for (Param p : params) {
			p.print(parameter);
		}

		Frame trainFrame = null;
		GBM job = null;
		GBMModel gbmModel = null;
		Frame score = null;

		trainFrame = parameter._train.get();

		try {
			Scope.enter();

			System.out.println("Build model ");
			job = new GBM(parameter);
			System.out.println("Train model");
			gbmModel = job.trainModel().get();

			System.out.println("Predict testcase " + testcase_id);
			score = gbmModel.score(trainFrame);

			System.out.println("Validate testcase " + testcase_id);
			// Assert.assertTrue(gbmModel.testJavaScoring(score, trainFrame, 1e-15));

			if (FunctionUtils.isNegativeTestcase(tcHeaders, rawInput)) {
				Assert.fail("It is negative testcase");
			}
			else {
				System.out.println("Testcase is passed.");
			}
		}
		catch (Exception ex) {
			System.out.println("Testcase is failed.");
			ex.printStackTrace();

			if (!FunctionUtils.isNegativeTestcase(tcHeaders, rawInput)) {
				Assert.fail("Testcase is failed", ex);
			}
		}
		finally {
			if (score != null) {
				score.remove();
				score.delete();
			}
			if (job != null) {
				job.remove();
			}
			if (gbmModel != null) {
				gbmModel.delete();
			}
			Scope.exit();
		}
	}

	private static String validate(String train_dataset_id, Dataset train_dataset, String[] input) {

		System.out.println("Validate Parameters object with testcase: " + input[tcHeaders.indexOf("testcase_id")]);
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
			result = Param.validateAutoSetParams(params, input, tcHeaders);
		}

		if (result != null) {
			result = "[INVALID] " + result;
		}

		return result;
	}

	private static GBMParameters toGBMParameters(String train_dataset_id, String validate_dataset_id,
			Dataset train_dataset, Dataset validate_dataset, String[] input) {

		System.out.println("Create Parameters object with testcase: " + input[tcHeaders.indexOf("testcase_id")]);

		GBMParameters gbmParams = new GBMParameters();

		// set AutoSet params
		for (Param p : params) {
			if (p.isAutoSet) {
				p.parseAndSet(gbmParams, input[tcHeaders.indexOf(p.name)]);
			}
		}

		// set distribution param
		Family f = (Family) familyParams.getValue(input, tcHeaders);

		if (f != null) {
			System.out.println("Set _distribution: " + f);
			gbmParams._distribution = f;
		}
		
		// set response column params
		gbmParams._response_column = train_dataset.getResponseColumn();

		// set train/validate params
		Frame trainFrame = null;
		Frame validateFrame = null;

		try {

			System.out.println("Create train frame: " + train_dataset_id);
			trainFrame = train_dataset.getFrame();

			if (StringUtils.isNotEmpty(validate_dataset_id) && validate_dataset != null
					&& validate_dataset.isAvailabel()) {
				System.out.println("Create validate frame: " + validate_dataset_id);
				validateFrame = validate_dataset.getFrame();
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
		gbmParams._train = trainFrame._key;

		if (validateFrame != null) {
			System.out.println("Set validate frame");
			gbmParams._valid = validateFrame._key;
		}

		System.out.println("Create success GBMParameters object.");
		return gbmParams;

	}
	
	private static Param[] params = new Param[] {
		
		new Param("_distribution", "Family", false, false),

		// autoset items
		new Param("_nfolds", "int"),
		new Param("_ignore_const_cols", "boolean"),
		new Param("_offset_column", "String"),
		new Param("_weights_column", "String"),
		new Param("_ntrees", "int"),
		new Param("_max_depth", "int"),
		new Param("_min_rows", "double"),
		new Param("_nbins", "int"),
		new Param("_nbins_cats", "int"),
		new Param("_score_each_iteration", "boolean"),
		new Param("_learn_rate", "float"),
		new Param("_balance_classes", "boolean"),
		new Param("_max_confusion_matrix_size", "int"),
		new Param("_max_hit_ratio_k", "int"),
		new Param("_r2_stopping", "double"),
		new Param("_build_tree_one_node", "boolean"),
		new Param("_class_sampling_factors", "float[]"),
	}; 
	
	private static List<String> tcHeaders = new ArrayList<String>(Arrays.asList(
			"0",
			"test_description",
			"testcase_id",
			FunctionUtils.testcase_type,

			// GBM Parameters
			"regression",
			"classification",
			
			"auto",
			"gaussian",
			"binomial",
			"multinomial",
			"poisson",
			"gamma",
			"tweedie",
			
			"_nfolds",
			"fold_column",
			"_ignore_const_cols",
			"_offset_column",
			"_weights_column",
			"_ntrees",
			"_max_depth",
			"_min_rows",
			"_nbins",
			"_nbins_cats",
			"_learn_rate",
			"_score_each_iteration",
			"_balance_classes",
			"_max_confusion_matrix_size",
			"_max_hit_ratio_k",
			"_r2_stopping",
			"_build_tree_one_node",
			"_class_sampling_factors",
			
			// testcase description
			"distribution",
			"regression_balanced_unbalanced",
			"rows",
			"columns",
			"train_rows_after_split",
			"validation_rows_after_split",
			"categorical",
			"sparse",
			"dense",
			"high-dimensional data",
			"correlated",
			"collinear_cols",

			// dataset files & ids
			"train_dataset_id",
			"validate_dataset_id",

			"ignored_columns",
			"R",
			"Scikit",
			"R_AUC",
			"R_MSE",
			"R_Loss",
			"Scikit_AUC",
			"Scikit_MSE",
			"Scikit_Loss"
	));
	
	//TODO: Family have no binomial attribute
	private final static OptionsGroupParam familyParams = new OptionsGroupParam(
			new String[] {"auto", "gaussian", "multinomial", "poisson", "gamma", "tweedie"},
			new Object[] {Family.AUTO, Family.gaussian, Family.multinomial, Family.poisson, Family.gamma, Family.tweedie});
	
	private static HashMap<String, Dataset> dataSetCharacteristic;
}
