package h2o.testng.utils;

import java.util.List;


public class FunctionUtils {
	public static final String testcase_type = "testcase_type";
	
	public static boolean isNegativeTestcase(List<String> tcHeaders, String[] input) {

		final String negative = "negative";

		if (negative.equals(input[tcHeaders.indexOf(testcase_type)].trim())) {
			return true;
		}

		return false;
	}
}
