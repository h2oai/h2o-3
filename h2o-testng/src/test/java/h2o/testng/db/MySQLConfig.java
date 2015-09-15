package h2o.testng.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class MySQLConfig {

	private static MySQLConfig appConfig;

	private Properties properties;
	private String dbConfigFilePath = null;

	private MySQLConfig() {

	}

	public static MySQLConfig initConfig() {

		if (appConfig == null) {
			appConfig = new MySQLConfig();
		}

		return appConfig;
	}

	private void loadConfig() {

		File configFile = new File(dbConfigFilePath);
		if (!configFile.exists()) {
			System.out.println("Database config file not found");
			System.exit(-1);
		}

		properties = new Properties();

		try {
			properties.load(new BufferedReader(new FileReader(configFile)));
		}
		catch (FileNotFoundException ex) {
			System.out.println("Database config file not found");
			ex.printStackTrace();
			System.exit(-1);
		}
		catch (IOException ex) {
			System.out.println("Can not read config file");
			ex.printStackTrace();
			System.exit(-1);
		}

		System.out.println("Loaded database config file successfully");
	}

	public void setConfigFilePath(String configFilePath) {

		dbConfigFilePath = configFilePath;
	}

	public boolean isUsedDB() {

		return dbConfigFilePath != null;
	}

	public String getHost() {

		if (properties == null) {
			loadConfig();
		}

		return properties.getProperty(Constant.dbHost);
	}

	public String getPort() {

		if (properties == null) {
			loadConfig();
		}

		return properties.getProperty(Constant.dbPort);
	}

	public String getUser() {

		if (properties == null) {
			loadConfig();
		}

		return properties.getProperty(Constant.dbUser);
	}

	public String getPassword() {

		if (properties == null) {
			loadConfig();
		}

		return properties.getProperty(Constant.dbPassword);
	}

	public String getDatabasename() {

		if (properties == null) {
			loadConfig();
		}

		return properties.getProperty(Constant.dbDatabaseName);
	}

	public String getTableName() {

		if (properties == null) {
			loadConfig();
		}

		return properties.getProperty(Constant.dbTableName);
	}

	public int getQueryTimeout() {

		if (properties == null) {
			loadConfig();
		}

		return Integer.parseInt(properties.getProperty(Constant.dbQueryTimeout));
	}
}
