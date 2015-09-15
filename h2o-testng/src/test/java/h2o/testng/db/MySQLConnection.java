package h2o.testng.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class MySQLConnection {

	private static MySQLConfig config = MySQLConfig.initConfig();
	private static Connection connection = null;

	public void initConnection() throws SQLException {

		String url = String.format("jdbc:mysql://%s:%s/%s", config.getHost(), config.getPort(),
				config.getDatabasename());

		if (connection == null || connection.isClosed()) {
			connection = DriverManager.getConnection(url, config.getUser(), config.getPassword());
		}
	}

	public Statement createStatement() throws SQLException {

		Statement statement = null;

		initConnection();

		statement = connection.createStatement();
		statement.setQueryTimeout(config.getQueryTimeout());

		return statement;
	}

	public boolean closeConnection() {

		if (connection != null) {
			try {
				connection.close();
			}
			catch (SQLException ex) {
				System.out.println("Failed to close connection!");
				ex.printStackTrace();
				return false;
			}
		}

		return true;
	}
}