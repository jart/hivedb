package org.hivedb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.hivedb.util.database.MysqlTestCase;
import org.hivedb.util.scenarioBuilder.HiveScenarioMarauderConfig;
import org.testng.annotations.Test;

public class TestHiveScenarioWithMySql extends MysqlTestCase {

	/**
	 *  Fills a hive with metadata and indexes to validate CRUD operations
	 *  This tests works but is commented out due to its slowness
	 */
	@Test(groups={"mysql"})
	public void testPirateDomain() throws Exception {
		new HiveScenarioTest(new HiveScenarioMarauderConfig(getConnectString("hive"), getDataUris())).performTest(100,0);
	}
	private Collection<String> getDataUris() {
		Collection<String> uris = new ArrayList<String>();
		for(String name : getDataNodeNames())
			uris.add(getConnectString(name));
		return uris;
	}
	
	private Collection<String> getDataNodeNames() {
		return Arrays.asList(new String[]{"data1","data2","data3"});
	}

	@Override
	public Collection<String> getDatabaseNames() {
		return Arrays.asList(new String[]{"hive","data1","data2","data3"});
	}
}