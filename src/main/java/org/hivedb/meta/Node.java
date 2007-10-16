/**
 * HiveDB is an Open Source (LGPL) system for creating large, high-transaction-volume
 * data storage systems.
 */
package org.hivedb.meta;

import org.hivedb.Hive;
import org.hivedb.Lockable;
import org.hivedb.util.HiveUtils;
import org.hivedb.util.database.HiveDbDialect;

/**
 * Node models a database instance suitable for storage of partitioned Data.
 * 
 * @author Kevin Kelm (kkelm@fortress-consulting.com)
 * @author Andy Likuski (alikuski@cafepress.com)
 */
public class Node implements Comparable<Node>, Cloneable, IdAndNameIdentifiable<Integer>, Lockable {
	private int id,partitionDimensionId,port;
	private String uri,name, host,databaseName, username, password, options;
	private boolean readOnly = false;
	private double capacity;
	private HiveDbDialect dialect;

	public Node(int id, String name, String databaseName, String host, int partitionDimensionId, HiveDbDialect dialect) {
		this.id = id;
		this.name = name;
		this.databaseName = databaseName;
		this.host = host;
		this.partitionDimensionId = partitionDimensionId;
		this.dialect = dialect;
	}
	
	public Node(int id, String name, String uri, boolean readOnly, int partitionDimensionId) {
		this(id, name, "", "", partitionDimensionId, HiveDbDialect.MySql);
		this.uri = uri;
		this.readOnly = readOnly;
	}
	
//	public Node(String name, String uri) {
//		this(name, uri, false);
//	}
//	
//	public Node(String name, String uri, boolean readOnly) {
//		this(Hive.NEW_OBJECT_ID, name, uri, readOnly,0);
//	}
	
//	reinstate me
//	public Node() {}
	
	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getOptions() {
		return options;
	}

	public void setOptions(String options) {
		this.options = options;
	}

	public HiveDbDialect getDialect() {
		return dialect;
	}

	public void setDialect(HiveDbDialect dialect) {
		this.dialect = dialect;
	}

	public void setId(int id) {
		this.id = id;
	}
	
	public Integer getId() {
		return id;
	}
	public boolean isReadOnly() {
		return readOnly;
	}
	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	
	public double getCapacity() {
		return capacity;
	}
	public void setCapacity(double capacity) {
		this.capacity = capacity;
	}
	
	public void updateId(int id) {
		this.id = id;
	}	

	public String getName() {
		return name;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getPartitionDimensionId() {
		return partitionDimensionId;
	}

	public void setPartitionDimensionId(int partitionDimensionId) {
		this.partitionDimensionId = partitionDimensionId;
	}
	
	public boolean equals(Object obj)
	{
		return obj.hashCode() == hashCode();
	}
	public int hashCode() {
		return HiveUtils.makeHashCode(new Object[] {
				id,partitionDimensionId,port,uri,name, host,databaseName, username, password, options,readOnly,capacity,dialect
		});
	}
	public String toString()
	{
		return HiveUtils.toDeepFormatedString(this, 
										"Id", 		getId(), 
										"Name", 	getName(), 
										"Uri", 		getUri(), 
										"ReadOnly",	isReadOnly());									
	}

	public int compareTo(Node o) {
		return getUri().compareTo(o.getUri());
	}
}
