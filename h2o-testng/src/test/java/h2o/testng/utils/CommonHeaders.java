package h2o.testng.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommonHeaders {

	public final static String testcase_id = "testcase_id";
	public final static String test_description = "test_description";

	public final static String regression = "regression";
	public final static String classification = "classification";

	public final static String train_dataset_id = "train_dataset_id";
	public final static String validate_dataset_id = "validate_dataset_id";

	public final static String family_auto = "auto";
	public final static String family_gaussian = "gaussian";
	public final static String family_binomial = "binomial";
	public final static String family_multinomial = "multinomial";
	public final static String family_poisson = "poisson";
	public final static String family_gamma = "gamma";
	public final static String family_tweedie = "tweedie";

	public static List<String> commonHeaders = new ArrayList<String>(Arrays.asList(
			test_description,
			testcase_id,
			
			regression,
			classification,
			
			// dataset files & ids
			train_dataset_id,
			validate_dataset_id
			));
}
