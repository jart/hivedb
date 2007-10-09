package org.hivedb;

import static org.testng.AssertJUnit.assertTrue;

import java.util.Arrays;
import java.util.Collection;

import javax.sql.DataSource;

import org.hivedb.management.KeyAuthority;
import org.hivedb.management.MySqlKeyAuthority;
import org.hivedb.util.database.HiveMySqlTestCase;
import org.testng.annotations.Test;

public class TestMysqlKeyAuthority extends HiveMySqlTestCase{
	DataSource ds = null;

	@Test(groups={"mysql"})
	@SuppressWarnings("unchecked")
	public void testAssign() throws Exception {
		KeyAuthority<Integer> authority = new MySqlKeyAuthority<Integer>(
				getDataSource("test"), this.getClass(), Integer.class);
		int firstKey = authority.nextAvailableKey().intValue();
		int secondKey = authority.nextAvailableKey().intValue();
		assertTrue(secondKey > firstKey);
	}
	
	@Override
	public Collection<String> getDatabaseNames() {
		return Arrays.asList(new String[]{"test"});
	}

	@Override
	public String getHiveDatabaseName() {
		return "storage";
	}
}