package hex.tree.drf;

import h2o.testng.utils.OptionsGroupParam;
import h2o.testng.utils.Param;
import hex.Distributions.Family;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import water.Key;
import water.Scope;
import water.TestNGUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;

public class DRFBasic extends TestNGUtil {

	@DataProvider(name = "drfCases")
	public static Object[][] drfCases() {

		/**
		 * The first row of data is used to testing.
		 */
		final int firstRow = 5;
		final String testcaseFilePath = "h2o-testng/src/test/resources/drfCases.csv";

		Object[][] data = null;
		List<String> lines = null;

		try {
			// read data from file
			lines = Files.readAllLines(find_test_file_static(testcaseFilePath).toPath(), Charset.defaultCharset());
		}
		catch (Exception ignore) {
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
			data[r][4] = variables[tcHeaders.indexOf("train_dataset_filename")];
			data[r][5] = variables[tcHeaders.indexOf("validate_dataset_id")];
			data[r][6] = variables[tcHeaders.indexOf("validate_dataset_filename")];
			data[r][7] = variables;

			r++;
		}

		return data;
	}

	@Test(dataProvider = "drfCases")
	public void basic(String testcase_id, String test_description, String dataset_directory, String train_dataset_id,
			String train_dataset_filename, String validate_dataset_id, String validate_dataset_filename,
			String[] rawInput) {

		redirectStandardStreams();

		try {
			String invalidMessage = validate(rawInput);
			String notImplMessage = checkImplemented(rawInput);

			if (invalidMessage != null) {
				System.out.println(invalidMessage);
				Assert.fail(String.format(invalidMessage));
			}
			else if (notImplMessage != null) {
				System.out.println(notImplMessage);
				Assert.fail(String.format(notImplMessage));
			}
			else {
				_basic(testcase_id, test_description, train_dataset_id, train_dataset_filename, validate_dataset_id,
						validate_dataset_filename, toDRFParameters(rawInput), rawInput);
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

	private void _basic(String testcase_id, String test_description, String train_dataset_id,
			String train_dataset_filename, String validate_dataset_id, String validate_dataset_filename,
			DRFModel.DRFParameters parameter, String[] rawInput) {

		System.out.println(String.format("Testcase: %s", testcase_id));
		System.out.println(String.format("Description: %s", test_description));
		System.out.println("DRF Params:");
		for (Param p : params) {
			p.print(parameter);
		}

		Frame trainFrame = null;
		Frame validateFrame = null;
		DRF job = null;
		DRFModel drfModel = null;
		Frame score = null;

		trainFrame = parameter._train.get();
		if (parameter._valid != null) {
			validateFrame = parameter._valid.get();
		}

		try {
			Scope.enter();

			// Build a first model; all remaining models should be equal
			job = new DRF(parameter);
			drfModel = job.trainModel().get();

			score = drfModel.score(trainFrame);
			System.out.println("Test is passed.");
		}
		catch (IllegalArgumentException ex) {
			// can't predict testcase
			Assert.fail("Test is failed. It can't predict");
			ex.printStackTrace();
		}
		finally {
			if (job != null) {
				job.remove();
			}
			if (drfModel != null) {
				drfModel.delete();
			}
			if (trainFrame != null) {
				trainFrame.remove();
				trainFrame.delete();
			}
			if (validateFrame != null) {
				validateFrame.remove();
				validateFrame.delete();
			}
			if (score != null) {
				score.remove();
				score.delete();
			}
			Scope.exit();
		}
	}

	private static String validate(String[] input) {

		System.out.println("Validate DRFParameters object with testcase: " + input[tcHeaders.indexOf("testcase_id")]);
		String result = null;

		for (Param p : params) {
			if (p.isAutoSet) {
				result = p.validate(input[tcHeaders.indexOf(p.name)]);
				if (result != null) {
					return result;
				}
			}
		}

		String dataset_directory = input[tcHeaders.indexOf("dataset_directory")].trim();
		String train_dataset_id = input[tcHeaders.indexOf("train_dataset_id")];
		String train_dataset_filename = input[tcHeaders.indexOf("train_dataset_filename")];
		String response_column = input[tcHeaders.indexOf("_response_column")];

		if (StringUtils.isEmpty(dataset_directory)) {
			result = "Dataset directory is empty";
		}
		else if (StringUtils.isEmpty(train_dataset_id) || StringUtils.isEmpty(train_dataset_filename)) {
			result = "Dataset files is empty";
		}
		else if (StringUtils.isEmpty(response_column)) {
			result = "_response_column is empty";
		}

		if (result != null) {
			result = "[INVALID] " + result;
		}

		return result;
	}

	private static String checkImplemented(String[] input) {

		System.out.println("check DRFParameters object with testcase: " + input[tcHeaders.indexOf("testcase_id")]);
		String result = null;

		if (StringUtils.isNotEmpty(input[tcHeaders.indexOf("_offset_column")].trim())) {
			result = "offset_column is not implemented";
		}
		else if (StringUtils.isNotEmpty(input[tcHeaders.indexOf("_weights_column")].trim())) {
			result = "weights_column is not implemented";
		}
		else if (StringUtils.isNotEmpty(input[tcHeaders.indexOf("_nfolds")].trim())) {
			result = "nfolds is not implemented";
		}
		else if (StringUtils.isNotEmpty(input[tcHeaders.indexOf("fold_column")].trim())) {
			result = "fold_column is not implemented";
		}
		else if (StringUtils.isNotEmpty(input[tcHeaders.indexOf("gaussian")].trim())
				|| StringUtils.isNotEmpty(input[tcHeaders.indexOf("binomial")].trim())
				|| StringUtils.isNotEmpty(input[tcHeaders.indexOf("multinomial")].trim())
				|| StringUtils.isNotEmpty(input[tcHeaders.indexOf("poisson")].trim())
				|| StringUtils.isNotEmpty(input[tcHeaders.indexOf("gamma")].trim())
				|| StringUtils.isNotEmpty(input[tcHeaders.indexOf("tweedie")].trim())) {
			result = "Only AUTO family is implemented";
		}

		if (result != null) {
			result = "[NOT IMPL] " + result;
		}

		return result;
	}

	private static DRFModel.DRFParameters toDRFParameters(String[] input) {

		System.out.println("Create DRFParameters object with testcase: " + input[tcHeaders.indexOf("testcase_id")]);

		DRFModel.DRFParameters drfParams = new DRFModel.DRFParameters();

		// set AutoSet params
		for (Param p : params) {
			if (p.isAutoSet) {
				p.parseAndSet(drfParams, input[tcHeaders.indexOf(p.name)]);
			}
		}

		// set distribution param
		Family f = (Family) familyParams.getValue(input, tcHeaders);

		if (f != null) {
			System.out.println("Set _distribution: " + f);
			drfParams._distribution = f;
		}

		String datasetDirectory = input[tcHeaders.indexOf("dataset_directory")].trim();
		String train_dataset_id = input[tcHeaders.indexOf("train_dataset_id")].trim();
		String train_dataset_filename = input[tcHeaders.indexOf("train_dataset_filename")].trim();
		String validate_dataset_id = input[tcHeaders.indexOf("validate_dataset_id")].trim();
		String validate_dataset_filename = input[tcHeaders.indexOf("validate_dataset_filename")].trim();

		if ("bigdata".equals(datasetDirectory)) {
			datasetDirectory = "bigdata/laptop/testng/";
		}
		else {
			datasetDirectory = "smalldata/testng/";
		}

		Frame trainFrame = null;
		Frame validateFrame = null;

		// create train dataset
		File train_dataset = find_test_file_static(datasetDirectory + train_dataset_filename);
		System.out.println("Is train dataset exist? If no, abort the test.\n");
		assert train_dataset.exists();
		NFSFileVec nfs_train_dataset = NFSFileVec.make(train_dataset);
		Key key_train_dataset = Key.make(train_dataset_id + ".hex");
		
		try {
			trainFrame = ParseDataset.parse(key_train_dataset, nfs_train_dataset._key);
		}
		catch (Exception e) {
			nfs_train_dataset.remove();
			key_train_dataset.remove();
			throw e;
		}
		drfParams._train = trainFrame._key;

		// create validate dataset
		if (StringUtils.isNotEmpty(validate_dataset_filename)) {
			File validate_dataset = find_test_file_static(datasetDirectory + validate_dataset_filename);
			assert validate_dataset.exists();
			NFSFileVec nfs_validate_dataset = NFSFileVec.make(validate_dataset);
			Key key_validate_dataset = Key.make(validate_dataset_id + ".hex");
			
			try {
				validateFrame = ParseDataset.parse(key_validate_dataset, nfs_validate_dataset._key);
			}
			catch (Exception e) {
				trainFrame.delete();
				nfs_validate_dataset.remove();
				key_validate_dataset.remove();
				throw e;
			}
			
			drfParams._valid = validateFrame._key;
		}

		return drfParams;
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
		// there properties is "default" accessor. Thus, we cannot set value
//		new Param("_mtries", "int"),
//		new Param("_sample_rate", "float"),
		new Param("_score_each_iteration", "boolean"),
		new Param("_balance_classes", "boolean"),
		new Param("_max_confusion_matrix_size", "int"),
		new Param("_max_hit_ratio_k", "int"),
		new Param("_r2_stopping", "double"),
		new Param("_build_tree_one_node", "boolean"),
//		new Param("_binomial_double_trees", "boolean"),
		new Param("_class_sampling_factors", "float[]"),
		
		new Param("_response_column", "String"),
	}; 
	
	private static List<String> tcHeaders = new ArrayList<String>(Arrays.asList(
			"0",
			"1",
			"test_description",
			"testcase_id",

			// DRF Parameters
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
//			"_mtries",
//			"_sample_rate",
			"_score_each_iteration",
			"_balance_classes",
			"_max_confusion_matrix_size",
			"_max_hit_ratio_k",
			"_r2_stopping",
			"_build_tree_one_node",
//			"_binomial_double_trees",
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
			"dataset_directory",
			"train_dataset_id",
			"train_dataset_filename",
			"validate_dataset_id",
			"validate_dataset_filename",

			"_response_column",
			"response_column_type",
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

	//TODO: missing binomial attribute in hex.Distributions.Family class
	private final static OptionsGroupParam familyParams = new OptionsGroupParam(
				new String[] {"auto","gaussian", "multinomial", "poisson", "gamma", "tweedie"},
				new Object[] {Family.AUTO,Family.gaussian, Family.multinomial, Family.poisson, Family.gamma, Family.tweedie});
}
