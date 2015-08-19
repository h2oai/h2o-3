package hex.tree.gbm;

import h2o.testng.utils.FunctionUtils;
import h2o.testng.utils.OptionsGroupParam;
import h2o.testng.utils.Param;
import hex.Distribution.Family;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class GBMConfig {
	/**
	 * The first row of data is used to testing.
	 */
	public final static int firstRow = 4;
	public final static String positiveTestcaseFilePath = "h2o-testng/src/test/resources/gbmCases.csv";
	public final static String negativeTestcaseFilePath = "h2o-testng/src/test/resources/gbmNegCases.csv";
	
	public static Param[] params = new Param[] {
		
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
	
	public static List<String> tcHeaders = new ArrayList<String>(Arrays.asList(
			"0",
			"test_description",
			"testcase_id",

			// GBM Parameters
			FunctionUtils.test_description,
			FunctionUtils.testcase_id,
			
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
			FunctionUtils.train_dataset_id,
			FunctionUtils.validate_dataset_id,

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
	public final static OptionsGroupParam familyParams = new OptionsGroupParam(
			new String[] {"auto", "gaussian", "multinomial", "poisson", "gamma", "tweedie"},
			new Object[] {Family.AUTO, Family.gaussian, Family.multinomial, Family.poisson, Family.gamma, Family.tweedie});
}
