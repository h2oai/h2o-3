package h2o.testng.db;

import h2o.testng.utils.CommonHeaders;

import java.sql.ResultSet;
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

		final String sql = "create table if not exists " + config.getTableName() 
				+ "(" 
				+ "test_case_id			 VARCHAR(125),"
				+ "training_frame_id	 VARCHAR(125),"
				+ "validation_frame_id	 VARCHAR(125),"
				+ "mse_result			 DOUBLE,"
				+ "auc_result			 DOUBLE,"
//				+ "metric_type			 VARCHAR(125),"
//				+ "result			 	 VARCHAR(125),"
				+ "date					 datetime,"
				+ "interpreter_version	 VARCHAR(125),"
				+ "machine_name			 VARCHAR(125),"
				+ "total_hosts			 INT,"
				+ "cpus_per_hosts		 INT,"
				+ "total_nodes			 INT,"
				+ "source				 VARCHAR(125),"
				+ "parameter_list		 VARCHAR(1024),"
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
		int cpusPerHost = 0;
		int totalNodes = 0;

		MySQLConnection connection = new MySQLConnection();
		Statement statement = null;

		String sql = String.format(
				"insert into %s values('%s','%s','%s','%s','%s',%s,'%s','%s',%s,%s,%s,'%s','%s','%s','%s')",
				config.getTableName(), testcaseId, trainingFrameId, validationFrameId, mse_result, auc_result, currentTime,
				interpreterVersion, machineName, totalHosts, cpusPerHost, totalNodes, source, rawInput.toString(),
				gitHashNumber, tuned_or_defaults);

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

//	public static void showAll() {
//
//		MySQLConnection connection = new MySQLConnection();
//		Statement statement = null;
//		ResultSet rs = null;
//
//		try {
//
//			connection.initConnection();
//			statement = connection.createStatement();
//
//			rs = statement.executeQuery("select * from " + config.getTableName());
//			while (rs.next()) {
//				// read the result set
//				System.out.println();
//				System.out.println("testcase id = " + rs.getString("test_case_id"));
//				System.out.println("training frame id = " + rs.getString("training_frame_id"));
//				System.out.println("validation frame id = " + rs.getString("validation_frame_id"));
//				System.out.println("metric_type = " + rs.getString("metric_type"));
//				System.out.println("result = " + rs.getString("result"));
//				System.out.println("date = " + rs.getString("date"));
//				System.out.println("interpreter version = " + rs.getString("interpreter_version"));
//				System.out.println("machine_name = " + rs.getString("machine_name"));
//				System.out.println("total_hosts = " + rs.getString("total_hosts"));
//				System.out.println("cpus_per_hosts = " + rs.getString("cpus_per_hosts"));
//				System.out.println("total_nodes = " + rs.getString("total_nodes"));
//				System.out.println("source = " + rs.getString("source"));
//				System.out.println("parameter list = " + rs.getString("parameter_list"));
//				System.out.println("git_hash_number = " + rs.getString("git_hash_number"));
//				System.out.println("tuned_or_defaults = " + rs.getString("defaults"));
//			}
//		}
//		catch (Exception ex) {
//			System.out.println("Cannot list all from table: " + config.getTableName());
//			ex.printStackTrace();
//		}
//		finally {
//			connection.closeConnection();
//		}
//	}
}
