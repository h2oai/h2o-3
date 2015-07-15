package h2o.testng.utils;

import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;

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
	
	public static Object getValue(int paramType, String[] value, List<String> tcHeaders) {

		return OptionsGroupParam.optionParams[paramType].getValue(value,tcHeaders);
	}

	
	public final static OptionsGroupParam[] optionParams = new OptionsGroupParam[] {
		new OptionsGroupParam(
				new String[] {"gaussian", "binomial", "poisson", "gamma"},
				new Object[] {Family.gaussian, Family.binomial, Family.poisson, Family.gamma}),
		new OptionsGroupParam(
				new String[] {"auto", "irlsm", "lbfgs"},
				new Object[] {Solver.AUTO, Solver.IRLSM, Solver.L_BFGS}),
	};

	public enum ParamType {
		FAMILY(0), SOLVER(1);
		
		private int value;
		
		private ParamType(int value) {
			
			this.value = value;
		}
		
		public int getValue() {
			
			return value;
		}
	}
}
