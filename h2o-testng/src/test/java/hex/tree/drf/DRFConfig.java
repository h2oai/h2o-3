package hex.tree.drf;

import h2o.testng.utils.OptionsGroupParam;
import h2o.testng.utils.Param;
import h2o.testng.utils.CommonHeaders;
import hex.Distribution.Family;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class DRFConfig {
	
	public final static int indexRowHeader = 5;
	public final static String positiveTestcaseFilePath = "h2o-testng/src/test/resources/drfCases.csv";
	public final static String negativeTestcaseFilePath = "h2o-testng/src/test/resources/drfNegCases.csv";
	
	public static Param[] params = new Param[] {
		
		new Param("_distribution", "Family", false, false),

		// autoset items
		new Param("_nfolds", "int"),
		new Param("_fold_column", "String"),
		new Param("_ignore_const_cols", "boolean"),
		new Param("_offset_column", "String"),
		new Param("_weights_column", "String"),
		new Param("_ntrees", "int"),
		new Param("_max_depth", "int"),
		new Param("_min_rows", "double"),
		new Param("_nbins", "int"),
		new Param("_nbins_cats", "int"),
		new Param("_score_each_iteration", "boolean"),
		new Param("_balance_classes", "boolean"),
		new Param("_max_confusion_matrix_size", "int"),
		new Param("_max_hit_ratio_k", "int"),
		new Param("_r2_stopping", "double"),
		new Param("_build_tree_one_node", "boolean"),
		new Param("_class_sampling_factors", "float[]"),
	}; 
	
	public static List<String> listHeaders = new ArrayList<String>(
			Arrays.asList(
					"auto",
					"gaussian",
					"binomial",
					"multinomial",
					"poisson",
					"gamma",
					"tweedie",
					
					"_nfolds",
					"_fold_column",
					"_ignore_const_cols",
					"_offset_column",
					"_weights_column",
					"_ntrees",
					"_max_depth",
					"_min_rows",
					"_nbins",
					"_nbins_cats",
					"_score_each_iteration",
					"_balance_classes",
					"_max_confusion_matrix_size",
					"_max_hit_ratio_k",
					"_r2_stopping",
					"_build_tree_one_node",
					"_class_sampling_factors"
					)
			);
	
	static {
	    listHeaders.addAll(CommonHeaders.commonHeaders);
	}
	
	//TODO: missing binomial attribute in hex.Distributions.Family class
	public final static OptionsGroupParam familyParams = new OptionsGroupParam(
				new String[] {"auto","gaussian", "multinomial", "poisson", "gamma", "tweedie"},
				new Object[] {Family.AUTO,Family.gaussian, Family.multinomial, Family.poisson, Family.gamma, Family.tweedie});
	
	
}
