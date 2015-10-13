package h2o.testng.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class OptionsGroupParam {

	public List<String> optionsGroup = null;
	public Object[] values = null;

	public OptionsGroupParam(String[] optionsGroup, Object[] values) {

		this.optionsGroup = new ArrayList<String>(Arrays.asList(optionsGroup));
		this.values = values;
	}

	public Object getValue(HashMap<String, String> rawInput) {

		for (String option : optionsGroup) {
			if (Param.parseBoolean(rawInput.get(option))) {
				return values[optionsGroup.indexOf(option)];
			}
		}
		return null;
	}

	public Object getValueKey(HashMap<String, String> rawInput, String key) {

		if(!"".equals(rawInput.get(key).trim())){
			try {
				return values[optionsGroup.indexOf(rawInput.get(key).trim())];
			}catch (Exception ex){
				return null;
			}

		}
		return null;
	}
}
