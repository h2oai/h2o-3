package h2o.testng.db;

import h2o.testng.utils.CommonHeaders;

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
				+ "parameter_list		 VARCHAR(2048)," 
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

	public static boolean save(String mse_result, String auc_result, HashMap<String, String> rawInput) {

		if (!config.isUsedDB()) {
			System.out.println("Program is configured don't use database");
			return false;
		}

		final String testcaseId = rawInput.get(CommonHeaders.testcase_id);
		final String trainingFrameId = rawInput.get(CommonHeaders.train_dataset_id);
		final String validationFrameId = rawInput.get(CommonHeaders.validate_dataset_id);
		final String currentTime = "NOW()";
		final String interpreterVersion = "JVM";
		final String machineName = H2O.ABV.compiledBy();
		final String gitHashNumber = H2O.ABV.lastCommitHash();
		final String source = H2O.ABV.projectVersion();
		final String tuned_or_defaults = rawInput.get(MySQL.tuned_or_defaults);
		// TODO verify it
		int totalHosts = 0;
		int cpusPerHost = H2O.NUMCPUS;
		int totalNodes = H2O.CLOUD.size();

		if (mse_result == null || mse_result.toLowerCase().equals("na") || mse_result.toLowerCase().equals("nan")) {
			mse_result = "NULL";
		}

		if (auc_result == null || auc_result.toLowerCase().equals("na") || auc_result.toLowerCase().equals("nan")) {
			auc_result = "NULL";
		}

		MySQLConnection connection = new MySQLConnection();
		Statement statement = null;

		String sql = String.format(
				"insert into %s values('%s','%s','%s',%s,%s,%s,'%s','%s',%s,%s,%s,'%s','%s','%s','%s')",
				config.getTableName(), testcaseId, trainingFrameId, validationFrameId, mse_result, auc_result,
				currentTime, interpreterVersion, machineName, totalHosts, cpusPerHost, totalNodes, source,
				rawInput.toString(), gitHashNumber, tuned_or_defaults);

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
