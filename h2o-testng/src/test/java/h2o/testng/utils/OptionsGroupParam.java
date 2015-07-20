package h2o.testng.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OptionsGroupParam {

	public List<String> optionsGroup = null;
	public Object[] values = null;

	public OptionsGroupParam(String[] optionsGroup, Object[] values) {

		this.optionsGroup = new ArrayList<String>(Arrays.asList(optionsGroup));
		this.values = values;
	}

	public Object getValue(String[] value, List<String> tcHeaders) {

		for (String option : optionsGroup) {
			if (Param.parseBoolean(value[tcHeaders.indexOf(option)])) {
				return values[optionsGroup.indexOf(option)];
			}
		}
		return null;
	}
}
