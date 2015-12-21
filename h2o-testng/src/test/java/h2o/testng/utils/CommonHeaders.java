package h2o.testng.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommonHeaders {
	
	public final static String family_auto = "auto";
	public final static String family_gaussian = "gaussian";
	public final static String family_binomial = "binomial";
	public final static String family_multinomial = "multinomial";
	public final static String family_poisson = "poisson";
	public final static String family_gamma = "gamma";
	public final static String family_tweedie = "tweedie";

	public final static String test_case_id = "test_case_id";
	public final static String test_description = "test_description";

	public final static String regression = "regression";
	public final static String classification = "classification";

	public final static String training_dataset_id = "training_dataset_id";
	public final static String testing_dataset_id = "testing_dataset_id";

	public final static String error_message = "error_message";
	
	public static List<String> commonHeaders = new ArrayList<String>(Arrays.asList(
			test_description,
			test_case_id,
			
			regression,
			classification,
			
			// dataset files & ids
			training_dataset_id,
			testing_dataset_id
			
//			error_message
			));
}
