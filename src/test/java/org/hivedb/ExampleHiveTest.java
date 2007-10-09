package org.hivedb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;

import org.hivedb.management.HiveInstaller;
import org.hivedb.meta.AccessType;
import org.hivedb.meta.Node;
import org.hivedb.meta.PartitionDimension;
import org.hivedb.meta.Resource;
import org.hivedb.meta.SecondaryIndex;
import org.hivedb.util.database.H2TestCase;
import org.hivedb.util.functional.Atom;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcDaoSupport;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * This test provides a narrative example of how to create and use a hive.
 * It goes through:
 * -Installing the hive schema
 * -Adding a data node
 * -Installing the data schema
 * -Adding a secondary index
 * -Inserting a record into the data node
 * -Retrieving the record by Primary Key
 * -Retrieving the record by Secondary Index
 * 
 * @author Britt Crawford (bcrawford@cafepress.com)
 *
 */
public class ExampleHiveTest extends H2TestCase {
	private static final String dataTableCreateSql = "CREATE TABLE products (id integer PRIMARY KEY, name varchar(255), type varchar(255))";
	private static final String productInsertSql = "INSERT INTO products VALUES (?,?,?)";
	private static final String selectProductById = "SELECT * FROM products WHERE id = ?";
	private static final String selectProductByName = "SELECT * FROM products WHERE name = ?";
	
	@Test
	public void createAndUseTheHive() throws Exception {
		// Install The Hive Metadata Schema
		new HiveInstaller(getConnectString(H2TestCase.TEST_DB)).run();
		
		//Load a Hive
		Hive hive = Hive.load(getConnectString(H2TestCase.TEST_DB));
		
		//Create a Partition Dimension
		//We are going to partition our Product domain using the product type string.
		String dimensionName = "ProductType";
		
		PartitionDimension partitionDimension = 
			new PartitionDimension(dimensionName, Types.VARCHAR, new ArrayList<Node>(), getConnectString(H2TestCase.TEST_DB), new ArrayList<Resource>());
		
		//Add it to the Hive	
		partitionDimension = hive.addPartitionDimension(partitionDimension);
		
		//Create a Data Node
		Node dataNode = new Node("aNode",getConnectString(H2TestCase.TEST_DB));
		
		//Add it to the partition dimension
		hive.addNode(partitionDimension, dataNode);
		
		//Make sure everything we just added actually got put into the hive meta data.
		Assert.assertTrue(hive.getPartitionDimensions().size() > 0);
		Assert.assertNotNull(hive.getPartitionDimension(dimensionName).getNodes());
		Assert.assertTrue(hive.getPartitionDimension(dimensionName).getNodes().size() > 0);

		//Add a key, just to test.
		String key = "knife";
		hive.insertPrimaryIndexKey(dimensionName, key);
		//Just cleaning up the random key.
		hive.deletePrimaryIndexKey(dimensionName, key);
		
		//At this point there is no real data in the Hive just a directory of Primary key to node mappings.
		//First we need to load our data schema on to each data node.
		for(Node node : hive.getPartitionDimension(dimensionName).getNodes()){
			/*
			 *  
			 * Ordinarily to get a connection to node from the hive we would have to provide a key
			 * and the permissions (READ or READWRITE) with which we want to acquire the connection.
			 * However the getUnsafe method can be used [AND SHOULD ONLY BE USED] for cases like this
			 * when there is no data yet loadedon a node and thus no key to dereference. 
			 *
			 * NOTE: You can obtain vanilla JDBC connections from the hive or use the cached JdbcDaoSupport
			 * objects.  
			 *
			 */
			JdbcDaoSupport daoSupport = hive.getJdbcDaoSupportCache(dimensionName).getUnsafe(node);
			daoSupport.getJdbcTemplate().update(dataTableCreateSql);
		}
		
		//Set up a secondary index on products so that we can query them by name
		
		// First create a Resource.  All Secondary Indexes will be associated with this Resource.
		String resourceName = "Product";
		Resource product = new Resource(resourceName, Types.INTEGER, false, new ArrayList<SecondaryIndex>());
		
		// Add it to the Hive
		product = hive.addResource(partitionDimension.getName(), product);
		
		//Now create a SecondaryIndex
		SecondaryIndex nameIndex = new SecondaryIndex("name", Types.VARCHAR);
		//Add it to the Hive
		nameIndex = hive.addSecondaryIndex(product, nameIndex);
		//Note: SecondaryIndexes are identified by ResourceName.IndexColumnName
		
		//Now lets add a product to the hive.
		Product spork = new Product(23, "Spork", "Cutlery");
		//First we have to add a primary index entry in order to get allocated to a data node.
		//While it is possible to write a record to multiple locations within the Hive, the default implementation
		//inserts a single copy.
		hive.insertPrimaryIndexKey(dimensionName, spork.getType());
		//Next we insert the record into the assigned data node
		Collection<SimpleJdbcDaoSupport> sporkDaos = hive.getJdbcDaoSupportCache(dimensionName).get(spork.getType(), AccessType.ReadWrite);
		PreparedStatementCreatorFactory stmtFactory = 
			new PreparedStatementCreatorFactory(productInsertSql, new int[] {Types.INTEGER, Types.VARCHAR, Types.VARCHAR});
		Object[] parameters = new Object[] {spork.getId(), spork.getName(), spork.getType()};
		for(JdbcDaoSupport dao : sporkDaos)
			dao.getJdbcTemplate().update(stmtFactory.newPreparedStatementCreator(parameters));

		//Update the resource id so that the hive can locate it
		hive.insertResourceId(dimensionName, resourceName, spork.getId(), spork.getType());
		//Finally we update the SecondaryIndex
		hive.insertSecondaryIndexKey("name",resourceName,dimensionName, spork.getName(), spork.getId());
		
		//Retrieve spork by Primary Key
		sporkDaos = hive.getJdbcDaoSupportCache(dimensionName).get(spork.getType(), AccessType.ReadWrite);
		parameters = new Object[] {spork.getId()};
		
		//Here I am taking advantage of the fact that I know there is only one copy.
		Product productA = (Product) Atom.getFirst(sporkDaos).getJdbcTemplate().queryForObject(selectProductById, parameters, new ProductRowMapper());
		//Make sure its a spork
		Assert.assertEquals(spork.getName(), productA.getName());
		
		//Retrieve the spork by Name
		sporkDaos = (Collection<SimpleJdbcDaoSupport>) hive.getJdbcDaoSupportCache(dimensionName).get(nameIndex, spork.getName(), AccessType.Read);
		parameters = new Object[] {spork.getName()};
		Product productB = (Product) Atom.getFirst(sporkDaos).getJdbcTemplate().queryForObject(selectProductByName, parameters, new ProductRowMapper());
		//Make sure its a spork
		Assert.assertEquals(spork.getId(), productB.getId());
		
		//productA and productB are the same spork
		Assert.assertEquals(productA.getId(), productB.getId());
		Assert.assertEquals(productA.getName(), productB.getName());
	}

	class Product {
		private Integer id;
		private String name;
		private String type;
		
		public Product(Integer id, String name, String type) {
			this.id = id;
			this.name = name;
			this.type = type;
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
		
		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
	}
	
	class ProductRowMapper implements RowMapper {
		public Object mapRow(ResultSet rs, int index) throws SQLException {
			return new Product(rs.getInt("id"), rs.getString("name"), rs.getString("type"));
		}
	}
}