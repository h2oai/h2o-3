package h2o.testng.utils;

import hex.Model;

import java.lang.reflect.Field;
import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

import water.Key;

public class Param {

	public String name = null;
	public String type = null;
	public boolean isRequired = false;
	public boolean isAutoSet = true;

	public Param(String name, String type) {

		this(name, type, false, true);
	}

	public Param(String name, String type, boolean isRequired, boolean isAutoSet) {

		this.name = name;
		this.type = type;
		this.isRequired = isRequired;
		this.isAutoSet = isAutoSet;
	}

	/**
	 * Parse from string value to boolean value
	 * 
	 * @param value
	 * @return boolean value
	 */
	public static boolean parseBoolean(String value) {

		if (value == null) {
			return false;
		}

		// can either be "x", "Y", "Yes" for true and "N", "No" or blank for false
		String bValue = value.trim().toLowerCase();
		if ("x".equals(bValue) || "y".equals(bValue) || "yes".equals(bValue)) {
			return true;
		}

		return false;
	}

	public static String validateAutoSetParams(Param[] params, HashMap<String, String> rawInput) {

		String result = null;

		for (Param p : params) {
			if (p.isAutoSet) {
				result = p.validate(rawInput.get(p.name));
				if (result != null) {
					break;
				}
			}
		}

		return result;
	}

	public static boolean setAutoSetParams(Model.Parameters modelParameter, Param[] params,
			HashMap<String, String> rawInput) {

		System.out.println("set auto_set params");
		boolean isSetValue = false;

		// set AutoSet params
		for (Param p : params) {
			if (p.isAutoSet) {
				isSetValue |= p.parseAndSet(modelParameter, rawInput.get(p.name));
			}
		}

		return isSetValue;
	}

	/**
	 * show all of attributes of params in console
	 * 
	 * @param params
	 */
	public void print(Object params) {

		Class<?> clazz = params.getClass();

		while (clazz != null) {
			try {
				Field field = clazz.getDeclaredField(name);
				System.out.println(String.format("  %s = %s", name, field.get(params)));
				return;

			}
			catch (NoSuchFieldException e) {
				// not in this clazz, ok, fine... how about its Super one?
				clazz = clazz.getSuperclass();
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/**
	 * Validate one value
	 * 
	 * @param value
	 * @return null if value is valid otherwise return invalid message.
	 */
	private String validate(String value) {

		value = value.trim();

		if (StringUtils.isEmpty(value)) {
			if (isRequired) {
				return name + "is empty";
			}
			else {
				return null;
			}
		}

		String result = null;
		switch (type) {
			case "int":
				if (!NumberUtils.isDigits(value)) {
					result = name + " is not digits";
				}
				break;

			case "float":
			case "double":
				if (!NumberUtils.isNumber(value)) {
					result = name + " is not numberic";
				}
				break;

			case "double[]":
				// TODO: implement it
				break;

			default:
				break;
		}

		return result;
	}

	private boolean parseAndSet(Object params, String value) {

		value = value.trim();
		Object v = null;

		// is this a non-blank value? if it's NOT, no need to set: use Default value one!
		// TODO: if this is a required value then this input doesn't make sense!!!
		if ("".equals(value)) {
			// System.out.println("Value is empty, so ignore this");
			return false;
		}

		switch (type) {
			case "boolean":
				v = parseBoolean(value);
				break;

			case "String":
				v = value;
				break;

			case "String[]":
				// TODO: may be we need to parse this one too!!!
				v = new String[] { value };
				break;

			case "double":
				v = Double.parseDouble(value);
				break;

			case "double[]":
				v = new double[] { Double.parseDouble(value) };
				break;

			case "int":
				v = Integer.parseInt(value);
				break;

			case "int[]":
				String[] splitValues=value.split(";");
				int[] values= new int[]{splitValues.length};
				for(int iter=0;iter<splitValues.length;iter++){
					if(!splitValues[iter].trim().equals(""))
						values[iter]=Integer.parseInt(splitValues[iter]);
				}
				v = values;
				break;

			case "float":
				v = Float.parseFloat(value);
				break;

			case "float[]":
				String[] splitValuesfloat=value.split(";");
				float[] valuesfloat= new float[splitValuesfloat.length];
				for(int iter=0;iter<splitValuesfloat.length;iter++){
					if(!splitValuesfloat[iter].trim().equals(""))
						valuesfloat[iter]=Float.parseFloat(splitValuesfloat[iter]);
				}
				v = valuesfloat;
				break;

			case "Key":
				v = Key.make(value);
				break;

			default:
				System.out.println("Unrecognized type: " + type);
				break;
		}

		Class<?> clazz = params.getClass();
		while (clazz != null) {
			try {
				Field field = clazz.getDeclaredField(name);
				// field.setAccessible(true); // is this needed?!?

				// Check old value is different with new value
				Object oldValue = field.get(params);

				if (!v.equals(oldValue)) {
					System.out.println("Set " + name + ": " + v);
					field.set(params, v);
				}
				return true;

			}
			catch (NoSuchFieldException e) {
				// not in this clazz, ok, fine... how about its Super one?
				clazz = clazz.getSuperclass();
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}
		return false;
	}
}
