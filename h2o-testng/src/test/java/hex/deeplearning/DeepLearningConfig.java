package hex.deeplearning;

import h2o.testng.utils.CommonHeaders;
import h2o.testng.utils.OptionsGroupParam;
import h2o.testng.utils.Param;
import hex.Distribution;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DeepLearningConfig {

	public final static int indexRowHeader = 3;
	public final static String positiveTestcaseFilePath = "h2o-testng/src/test/resources/dlCases.csv";
	public final static String negativeTestcaseFilePath = "h2o-testng/src/test/resources/dlNegCases.csv";
	
	// below are single param which can set automatically to a GLM Object
	public static Param[] params = new Param[] {
			
			new Param("_distribution", "Distribution.Family", false, false),
			new Param("_activation", "Activation", false, false),
			new Param("_missing_values_handling","MissingValuesHandling", false, false),
			new Param("_initial_weight_distribution","InitialWeightDistribution", false, false),
			new Param("_loss","Loss", false, false),

			// autoset items
			new Param("_nfolds","int"),
			new Param("_hidden","int[] "),
			new Param("_epochs","double"),
			new Param("_variable_importances","boolean"),
			new Param("_fold_column","String"),
			new Param("_offset_column","String"),
			new Param("_weights_column","String"),
			new Param("_balance_classes","boolean"),
			new Param("_max_confusion_matrix_size","int"),
			new Param("_max_hit_ratio_k","int"),
			new Param("_use_all_factor_levels","boolean"),
			new Param("_train_samples_per_iteration","long"),
			new Param("_adaptive_rate","boolean"),
			new Param("_input_dropout_ratio","double"),
			new Param("_l1","double"),
			new Param("_l2","double"),
			new Param("_score_interval","double"),
			new Param("_score_training_samples","long"),
			new Param("_score_duty_cycle","double"),
			new Param("_replicate_training_data","boolean"),
			new Param("_autoencoder","boolean"),
			new Param("_class_sampling_factors","float[]"),
			new Param("_target_ratio_comm_to_comp","double"),
			new Param("_seed","long"),
			new Param("_rho","double"),
			new Param("_epsilon","double"),
			new Param("_max_w2","float"),
			new Param("_regression_stop","double"),
			new Param("_diagnostics","boolean"),
			new Param("_fast_mode","boolean"),
			new Param("_force_load_balance","boolean"),
			new Param("_single_node_mode","boolean"),
			new Param("_shuffle_training_data","boolean"),
			new Param("_quiet_mode","boolean"),
			new Param("_sparse","boolean"),
			new Param("_col_major","boolean"),
			new Param("_average_activation","double"),
			new Param("_sparsity_beta","double"),
			new Param("_max_categorical_features","int"),
			new Param("_reproducible","boolean"),
			new Param("_export_weights_and_biases","boolean")


	};
	
	public static OptionsGroupParam distributionOptionsParams = new OptionsGroupParam(
			new String[] {"auto",
					"bernoulli",
					"multinomial",
					"gaussian",
					"poissan",
					"gamma",
					"tweedie"},
			new Object[] {Distribution.Family.AUTO,
					Distribution.Family.bernoulli,
					Distribution.Family.multinomial,
					Distribution.Family.gaussian,
					Distribution.Family.poisson,
					Distribution.Family.gamma,
					Distribution.Family.tweedie}
	); 
	
	public static OptionsGroupParam activationOptionsParams = new OptionsGroupParam(
			new String[] {"tanh",
					"tanhwithdropout",
					"rectifier",
					"rectifierwithdropout",
					"maxout",
					"maxoutwithdropout"},
			new Object[] {DeepLearningParameters.Activation.Tanh,
					DeepLearningParameters.Activation.TanhWithDropout,
					DeepLearningParameters.Activation.Rectifier,
					DeepLearningParameters.Activation.RectifierWithDropout,
					DeepLearningParameters.Activation.Maxout,
					DeepLearningParameters.Activation.MaxoutWithDropout}
	);

	public static OptionsGroupParam initialWeightDistributionOptionsParams = new OptionsGroupParam(
			new String[] {"UniformAdaptive", "Uniform", "Normal"},
			new Object[] {
					DeepLearningParameters.InitialWeightDistribution.UniformAdaptive,
					DeepLearningParameters.InitialWeightDistribution.Uniform,
					DeepLearningParameters.InitialWeightDistribution.Normal
					}
	);

	public static OptionsGroupParam missingValuesHandlingOptionsParams = new OptionsGroupParam(
			new String[] {"Skip", "MeanImputation"},
			new Object[] {
					DeepLearningParameters.MissingValuesHandling.Skip,
					DeepLearningParameters.MissingValuesHandling.MeanImputation
			}
	);

	public static OptionsGroupParam lossOptionsParams = new OptionsGroupParam(
			new String[] {"automatic","crossentropy","huber","absolute"},
			new Object[] {
					DeepLearningParameters.Loss.Automatic,
					DeepLearningParameters.Loss.CrossEntropy,
					DeepLearningParameters.Loss.Huber,
					DeepLearningParameters.Loss.Absolute
			}
	);

	public static List<String> listHeaders = new ArrayList<String>(
			Arrays.asList(
					"auto",
					"bernoulli",
					"multinomial",
					"gaussian",
					"poissan",
					"gamma",
					"tweedie",

					"_nfolds",

					"tanh",
					"tanhwithdropout",
					"rectifier",
					"rectifierwithdropout",
					"maxout",
					"maxoutwithdropout",

					"_hidden",
					"_epochs",
					"_variable_importances",
					"_fold_column",
					"_offset_column",
					"_weights_column",
					"_balance_classes",
					"_max_confusion_matrix_size",
					"_max_hit_ratio_k",
					"check_point",
					"_use_all_factor_levels",
					"_train_samples_per_iteration",
					"_adaptive_rate",
					"_input_dropout_ratio",
					"_l1",
					"_l2",
					"automatic",
					"crossentropy",
					"meansquare",
					"huber",
					"absolute",
					"distribution",
					"_score_interval",
					"_score_training_samples",
					"_score_duty_cycle",
					"_replicate_training_data",
					"_autoencoder",
					"_class_sampling_factors",
					"_target_ratio_comm_to_comp",
					"_seed",
					"_rho",
					"_epsilon",
					"_max_w2",
					"_initial_weight_distribution",
					"_regression_stop",
					"_diagnostics",
					"_fast_mode",
					"_force_load_balance",
					"_single_node_mode",
					"_shuffle_training_data",
					"_missing_values_handling",
					"_quiet_mode",
					"_sparse",
					"_col_major",
					"_average_activation",
					"_sparsity_beta",
					"_max_categorical_features",
					"_reproducible",
					"_export_weights_and_biases"

					)
	);
	
	static {
	    listHeaders.addAll(CommonHeaders.commonHeaders);
	}
}
