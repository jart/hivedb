package org.hivedb.management;

import javax.sql.DataSource;

import org.hivedb.HiveRuntimeException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;

/**
 * Persistent numeric sequence generator. JdbcKeyAuthority is a thin wrapper
 * around DataFieldMaxValueIncrementer. JdbcKeyAuthority also takes
 * responsibility for issuing CREATE statements to initialize the sequence.
 * 
 * @author Justin McCarthy (jmccarthy@cafepress.com)
 */
public class JdbcKeyAuthority<T extends Number> implements KeyAuthority {
	DataFieldMaxValueIncrementer incrementer = null;

	private Class returnType = null;

	private String keySpace = null;

	// Only used to issue CREATE TABLE
	private DataSource dataSource = null;

	/**
	 * @param keySpace
	 *            A class that distinguishes this counter (the class name will
	 *            be mapped to a database table)
	 * @param returnType
	 *            Type of the numeric keys this KeyAuthority generates (Integer
	 *            or Float)
	 */
	public JdbcKeyAuthority(Class keySpace, Class<T> returnType) {
		this(tableNameForClass(keySpace), returnType);
		this.returnType = returnType;
	}

	/**
	 * @param keySpace
	 *            A String that distinguishes this counter (the class name will
	 *            be mapped to a database table)
	 * @param returnType
	 *            Type of the numeric keys this KeyAuthority generates (Integer
	 *            or Float)
	 */
	public JdbcKeyAuthority(String keySpace, Class<T> returnType) {
		this.returnType = returnType;
		this.keySpace = keySpace;
	}

	public void setIncrementer(DataFieldMaxValueIncrementer incrementer) {
		this.incrementer = incrementer;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public T nextAvailableKey() {
		T result = null;
		try {
			result = nextKey();
		} catch (Exception ex) {
			createSchema();
			result = nextKey();
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private T nextKey() {
		if (Integer.class.equals(returnType))
			return (T) new Integer(incrementer.nextIntValue());
		if (Long.class.equals(returnType))
			return (T) new Long(incrementer.nextLongValue());
		throw new HiveRuntimeException("Unable to generate key for type "
				+ returnType.toString());
	}

	private void createSchema() {
		JdbcTemplate template = new JdbcTemplate(dataSource);
		template.execute("CREATE TABLE " + keySpace + " (" + COLUMN_NAME
				+ " int);");
		template.execute("INSERT INTO  " + keySpace + " VALUES (1)");
	}

	public static String COLUMN_NAME = "current_max_id";

	public static String tableNameForClass(Class aClass) {
		return "key_authority_"
				+ aClass.getName().toLowerCase().replace('.', '_').replace('$',
						'_');
	}
}
