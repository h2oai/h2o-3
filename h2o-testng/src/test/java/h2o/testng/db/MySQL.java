package h2o.testng.db;

import h2o.testng.utils.CommonHeaders;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Statement;
import java.util.HashMap;

import water.H2O;

public class MySQL {

	private static MySQLConfig config = MySQLConfig.initConfig();

	public final static String defaults = "defaults";
	public final static String tuned = "tuned";
	public final static String tuned_or_defaults = "tuned_or_defaults";

	public static boolean createTable() {

		if (!config.isUsedDB()) {
			System.out.println("Program is configured don't use database");
			return false;
		}

		final String sql = "create table if not exists " + config.getTableName() + "("
				+ "test_case_id			 VARCHAR(125)," 
				+ "training_frame_id	 VARCHAR(125),"
				+ "validation_frame_id	 VARCHAR(125)," 
				+ "mse_result			 DOUBLE," 
				+ "auc_result			 DOUBLE,"
				+ "date					 datetime," 
				+ "interpreter_version	 VARCHAR(125)," 
				+ "machine_name			 VARCHAR(125),"
				+ "total_hosts			 INT," 
				+ "cpus_per_hosts		 INT," 
				+ "total_nodes			 INT," 
				+ "source				 VARCHAR(125),"
				+ "parameter_list		 TEXT," 
				+ "git_hash_number		 VARCHAR(125),"
				+ "tuned_or_defaults	 VARCHAR(125)" 
				+ ")";

		MySQLConnection connection = new MySQLConnection();
		Statement statement = null;

		try {
			statement = connection.createStatement();
			statement.executeUpdate(sql);
		}
		catch (Exception ex) {
			System.out.println("Can't create table: " + config.getTableName());
			ex.printStackTrace();
			return false;
		}
		finally {
			connection.closeConnection();
		}

		System.out.println("Create successfully table");
		return true;
	}

	public static boolean save(HashMap<String,Double> trainingResults, HashMap<String,Double> testingResults,
														 HashMap<String, String> rawInput) {

		if (!config.isUsedDB()) {
			System.out.println("Program is configured don't use database");
			return false;
		}

		String testcaseId = "1";
		String ipAddr = "NULL";
		try { ipAddr = InetAddress.getLocalHost().getCanonicalHostName();
		} catch (UnknownHostException e) { e.printStackTrace(); };
		int ncpu = Runtime.getRuntime().availableProcessors();

		MySQLConnection connection = new MySQLConnection();
		Statement statement = null;

		String sql = String.format(
			"insert into %s values(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, " +
				"%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, '%s', '%s', '%s', %s, '%s', " +
				"%s)",
			config.getTableName(),
			testcaseId,
			trainingResults.get("R2") == null || Double.isNaN(trainingResults.get("R2")) ? "NULL" : Double.toString(trainingResults.get("R2")),
			trainingResults.get("Logloss") == null || Double.isNaN(trainingResults.get("Logloss")) ? "NULL" : Double.toString(trainingResults.get("Logloss")),
			trainingResults.get("MeanResidualDeviance") == null || Double.isNaN(trainingResults.get("MeanResidualDeviance")) ? "NULL" : Double.toString(trainingResults.get("MeanResidualDeviance")),
			trainingResults.get("AUC") == null || Double.isNaN(trainingResults.get("AUC")) ? "NULL" : Double.toString(trainingResults.get("AUC")),
			trainingResults.get("AIC") == null || Double.isNaN(trainingResults.get("AIC")) ? "NULL" : Double.toString(trainingResults.get("AIC")),
			trainingResults.get("Gini") == null || Double.isNaN(trainingResults.get("Gini")) ? "NULL" : Double.toString(trainingResults.get("Gini")),
			trainingResults.get("MSE") == null || Double.isNaN(trainingResults.get("MSE")) ? "NULL" : Double.toString(trainingResults.get("MSE")),
			trainingResults.get("ResidualDeviance") == null || Double.isNaN(trainingResults.get("ResidualDeviance")) ? "NULL" : Double.toString(trainingResults.get("ResidualDeviance")),
			trainingResults.get("ResidualDegreesOfFreedom") == null || Double.isNaN(trainingResults.get("ResidualDegreesOfFreedom")) ? "NULL" : Double.toString(trainingResults.get("ResidualDegreesOfFreedom")),
			trainingResults.get("NullDeviance") == null || Double.isNaN(trainingResults.get("NullDeviance")) ? "NULL" : Double.toString(trainingResults.get("NullDeviance")),
			trainingResults.get("NullDegreesOfFreedom") == null || Double.isNaN(trainingResults.get("NullDegreesOfFreedom")) ? "NULL" : Double.toString(trainingResults.get("R2")),
			trainingResults.get("F1") == null || Double.isNaN(trainingResults.get("F1")) ? "NULL" : Double.toString(trainingResults.get("F1")),
			trainingResults.get("F2") == null || Double.isNaN(trainingResults.get("F2")) ? "NULL" : Double.toString(trainingResults.get("F2")),
			trainingResults.get("F0point5") == null || Double.isNaN(trainingResults.get("F0point5")) ? "NULL" : Double.toString(trainingResults.get("F0point5")),
			trainingResults.get("Accuracy") == null || Double.isNaN(trainingResults.get("Accuracy")) ? "NULL" : Double.toString(trainingResults.get("Accuracy")),
			trainingResults.get("Error") == null || Double.isNaN(trainingResults.get("Error")) ? "NULL" : Double.toString(trainingResults.get("Error")),
			trainingResults.get("Precision") == null || Double.isNaN(trainingResults.get("Precision")) ? "NULL" : Double.toString(trainingResults.get("Precision")),
			trainingResults.get("Recall") == null || Double.isNaN(trainingResults.get("Recall")) ? "NULL" : Double.toString(trainingResults.get("Recall")),
			trainingResults.get("MCC") == null || Double.isNaN(trainingResults.get("MCC")) ? "NULL" : Double.toString(trainingResults.get("MCC")),
			trainingResults.get("MaxPerClassError") == null || Double.isNaN(trainingResults.get("MaxPerClassError")) ? "NULL" : Double.toString(trainingResults.get("MaxPerClassError")),
			testingResults.get("R2") == null || Double.isNaN(testingResults.get("R2")) ? "NULL" : Double.toString(testingResults.get("R2")),
			testingResults.get("Logloss") == null || Double.isNaN(testingResults.get("Logloss")) ? "NULL" : Double.toString(testingResults.get("Logloss")),
			testingResults.get("MeanResidualDeviance") == null || Double.isNaN(testingResults.get("MeanResidualDeviance")) ? "NULL" : Double.toString(testingResults.get("MeanResidualDeviance")),
			testingResults.get("AUC") == null || Double.isNaN(testingResults.get("AUC")) ? "NULL" : Double.toString(testingResults.get("AUC")),
			testingResults.get("AIC") == null || Double.isNaN(testingResults.get("AIC")) ? "NULL" : Double.toString(testingResults.get("AIC")),
			testingResults.get("Gini") == null || Double.isNaN(testingResults.get("Gini")) ? "NULL" : Double.toString(testingResults.get("Gini")),
			testingResults.get("MSE") == null || Double.isNaN(testingResults.get("MSE")) ? "NULL" : Double.toString(testingResults.get("MSE")),
			testingResults.get("ResidualDeviance") == null || Double.isNaN(testingResults.get("ResidualDeviance")) ? "NULL" : Double.toString(testingResults.get("ResidualDeviance")),
			testingResults.get("ResidualDegreesOfFreedom") == null || Double.isNaN(testingResults.get("ResidualDegreesOfFreedom")) ? "NULL" : Double.toString(testingResults.get("ResidualDegreesOfFreedom")),
			testingResults.get("NullDeviance") == null || Double.isNaN(testingResults.get("NullDeviance")) ? "NULL" : Double.toString(testingResults.get("NullDeviance")),
			testingResults.get("NullDegreesOfFreedom") == null || Double.isNaN(testingResults.get("NullDegreesOfFreedom")) ? "NULL" : Double.toString(testingResults.get("R2")),
			testingResults.get("F1") == null || Double.isNaN(testingResults.get("F1")) ? "NULL" : Double.toString(testingResults.get("F1")),
			testingResults.get("F2") == null || Double.isNaN(testingResults.get("F2")) ? "NULL" : Double.toString(testingResults.get("F2")),
			testingResults.get("F0point5") == null || Double.isNaN(testingResults.get("F0point5")) ? "NULL" : Double.toString(testingResults.get("F0point5")),
			testingResults.get("Accuracy") == null || Double.isNaN(testingResults.get("Accuracy")) ? "NULL" : Double.toString(testingResults.get("Accuracy")),
			testingResults.get("Error") == null || Double.isNaN(testingResults.get("Error")) ? "NULL" : Double.toString(testingResults.get("Error")),
			testingResults.get("Precision") == null || Double.isNaN(testingResults.get("Precision")) ? "NULL" : Double.toString(testingResults.get("Precision")),
			testingResults.get("Recall") == null || Double.isNaN(testingResults.get("Recall")) ? "NULL" : Double.toString(testingResults.get("Recall")),
			testingResults.get("MCC") == null || Double.isNaN(testingResults.get("MCC")) ? "NULL" : Double.toString(testingResults.get("MCC")),
			testingResults.get("MaxPerClassError") == null || Double.isNaN(testingResults.get("MaxPerClassError")) ? "NULL" : Double.toString(testingResults.get("MaxPerClassError")),
			"NOW()",
			"H2O",
			H2O.ABV.projectVersion(),
			ipAddr,
			ncpu,
			H2O.ABV.lastCommitHash(),
			trainingResults.get("ModelBuildTime") == null || Double.isNaN(trainingResults.get("R2")) ? "NULL" : Double.toString(trainingResults.get("ModelBuildTime")));

		System.out.println("saved script SQL:");
		System.out.println(sql);

		try {
			statement = connection.createStatement();
			statement.executeUpdate(sql);
		}
		catch (Exception ex) {
			System.out.println("Can't insert into table: " + config.getTableName());
			ex.printStackTrace();
			return false;
		}
		finally {
			connection.closeConnection();
		}

		System.out.println("The result is saved successfully in database");
		return true;
	}
}
