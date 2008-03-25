package org.hivedb.meta.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hivedb.util.HiveUtils;

public class HiveBasicDataSource extends BasicDataSource implements Cloneable {
	private Log log = LogFactory.getLog(HiveBasicDataSource.class);
	public static final String CONNECTION_POOL_SIZE = "HiveDataSourceConnectionPoolSize";
	public static final int DEFAULT_POOL_SIZE = 32;
	private Long connectionTimeout = 500l;
	private Long socketTimeout  = 500l;
	
	public HiveBasicDataSource() {}
	
	public HiveBasicDataSource(String hiveUri, int poolSize){
		super();
		setUrl(hiveUri);	
		setMaxActive(poolSize);
		setValidationQuery("select count(1);");
		log.debug(String.format(
				"HiveBasicDataSource created for %s.  Max pool size: %s  Min idle connections: %s  Idle timeout: %s  TestOnBorrow: %s", 
				this.getUrl(), this.getMaxActive(), this.getMinIdle(), this.getMinEvictableIdleTimeMillis(), this.testOnBorrow));
	}
	
	public HiveBasicDataSource(String hiveUri)
	{
		this(hiveUri, getDefaultPoolSize());	
	}
	
	private static Integer getDefaultPoolSize() {
		try {
			String poolSize = System.getProperty(CONNECTION_POOL_SIZE);
			return Integer.parseInt(poolSize);
		} catch (Exception e) {
			return DEFAULT_POOL_SIZE;
		}
	}
	public String getUrl() { //publicize for testing
		return super.getUrl();
	}
	
	@Override
	public HiveBasicDataSource clone() throws CloneNotSupportedException {
		HiveBasicDataSource clone = new HiveBasicDataSource();
		clone.setConnectionTimeout(this.getConnectionTimeout());
		clone.setSocketTimeout(this.getSocketTimeout());
		clone.setInitialSize(this.getInitialSize());
		clone.setMinIdle(this.getMinIdle());
		clone.setMaxIdle(this.getMaxIdle());
		clone.setMaxActive(this.getMaxActive());
		clone.setMaxWait(this.getMaxWait());
		clone.setMaxOpenPreparedStatements(this.getMaxOpenPreparedStatements());
		clone.setDefaultTransactionIsolation(this.getDefaultTransactionIsolation());
		clone.setDefaultAutoCommit(this.getDefaultAutoCommit());
		clone.setPoolPreparedStatements(this.isPoolPreparedStatements());
		clone.setMinEvictableIdleTimeMillis(this.getMinEvictableIdleTimeMillis());
		clone.setNumTestsPerEvictionRun(this.getNumTestsPerEvictionRun());
		clone.setPassword(this.getPassword());
		clone.setTestOnBorrow(this.getTestOnBorrow());
		clone.setTestOnReturn(this.getTestOnReturn());
		clone.setTestWhileIdle(this.getTestWhileIdle());
		clone.setTimeBetweenEvictionRunsMillis(this.getTimeBetweenEvictionRunsMillis());
		clone.setUrl(this.getUrl());
		clone.setUsername(this.getUsername());
		clone.setValidationQuery(this.getValidationQuery());
		return clone;
	}

	@Override
	public int hashCode() {
		return HiveUtils.makeHashCode(
				this.getConnectionTimeout(),
				this.getSocketTimeout(),
				this.getInitialSize(),
				this.getMinIdle(),
				this.getMaxIdle(),
				this.getMaxActive(),
				this.getMaxWait(),
				this.getMaxOpenPreparedStatements(),
				this.getDefaultTransactionIsolation(),
				this.getDefaultAutoCommit(),
				this.isPoolPreparedStatements(),
				this.getMinEvictableIdleTimeMillis(),
				this.getNumTestsPerEvictionRun(),
				this.getPassword(),
				this.getTestOnBorrow(),
				this.getTestOnReturn(),
				this.getTestWhileIdle(),
				this.getTimeBetweenEvictionRunsMillis(),
				this.getUrl(),
				this.getUsername(),
				this.getValidationQuery()
		);
	}

	public Long getConnectionTimeout() {
		return connectionTimeout;
	}

	public void setConnectionTimeout(Long connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
		this.addConnectionProperty("connectTimeout", connectionTimeout.toString());
	}

	public Long getSocketTimeout() {
		return socketTimeout;
	}

	public void setSocketTimeout(Long socketTimeout) {
		this.socketTimeout = socketTimeout;
		this.addConnectionProperty("socketTimeout", socketTimeout.toString());
	}

	@Override
	public Connection getConnection() throws SQLException {
		Connection connection = super.getConnection();
		log.debug("Loaned connection, current active connections: " + this.getNumActive());
		return connection;
	}

	@Override
	public Connection getConnection(String username, String password)
			throws SQLException {
		Connection connection = super.getConnection(username,password);		
		log.debug("Loaned connection, current active connections: " + this.getNumActive());
		return connection;
	}
}
