package org.hivedb.util.database.test;

import org.hibernate.shards.strategy.access.SequentialShardAccessStrategy;
import org.hivedb.Hive;
import org.hivedb.HiveLockableException;
import org.hivedb.HiveRuntimeException;
import org.hivedb.Schema;
import org.hivedb.annotations.AnnotationHelper;
import org.hivedb.annotations.GeneratorIgnore;
import org.hivedb.annotations.IndexParam;
import org.hivedb.annotations.Validate;
import org.hivedb.configuration.EntityConfig;
import org.hivedb.configuration.EntityHiveConfig;
import org.hivedb.configuration.EntityIndexConfig;
import org.hivedb.configuration.HiveConfigurationSchema;
import org.hivedb.hibernate.BaseDataAccessObject;
import org.hivedb.hibernate.ConfigurationReader;
import org.hivedb.hibernate.HiveSessionFactory;
import org.hivedb.hibernate.HiveSessionFactoryBuilderImpl;
import org.hivedb.management.HiveInstaller;
import org.hivedb.meta.Node;
import org.hivedb.meta.persistence.CachingDataSourceProvider;
import org.hivedb.services.BaseClassDaoService;
import org.hivedb.services.ClassDaoService;
import org.hivedb.services.ServiceResponse;
import org.hivedb.util.Lists;
import org.hivedb.util.classgen.GenerateInstance;
import org.hivedb.util.classgen.GeneratePrimitiveValue;
import org.hivedb.util.classgen.GeneratedInstanceInterceptor;
import org.hivedb.util.classgen.ReflectionTools;
import org.hivedb.util.database.HiveDbDialect;
import org.hivedb.util.database.Schemas;
import org.hivedb.util.functional.*;
import org.hivedb.util.functional.Filter.BinaryPredicate;
import org.hivedb.util.validators.NoValidator;
import org.testng.Assert;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * This test base class uses ClassDaoService as a thin wrapper around a DataAccessObject. Subclasses may specify what entity classes to test.
 * @author andylikuski
 *
 */
public class ClassDaoServiceTest extends H2TestCase implements SchemaInitializer {
	public static int INSTANCE_COUNT = 5;
	protected EntityHiveConfig config;
	protected Hive hive;
	protected static Collection<LazyInitializer> services = Lists.newArrayList();
	protected static Collection<Class> entityClasses = Lists.newArrayList();
	protected HiveSessionFactory factory;
	
	public Collection<Schema> getSchemas() {
		return Arrays.asList(new Schema[] {
				new HiveConfigurationSchema(getConnectString(getHiveDatabaseName()))});
	}

	protected void superClassBeforeMethod() {
		super.beforeMethod();
	}
	
	@AfterMethod
	@Override
	public void afterMethod() {
		deleteDatabase("hive");
	}
	
	public void initialize(Collection<Schema> schemaList) {
		superClassBeforeMethod();
		new HiveInstaller(getConnectString(getHiveDatabaseName())).run();		
		ConfigurationReader reader = new ConfigurationReader(getEntityClasses());
		reader.install(getConnectString(getHiveDatabaseName()));
		hive = getHive();
		for(String nodeName : getDataNodeNames())
			try {
				hive.addNode(new Node(nodeName, nodeName, "" , HiveDbDialect.H2));
				for (Schema schema : schemaList) {
					Schemas.install(schema, getConnectString(nodeName));
				}
			} catch (HiveLockableException e) {
				throw new HiveRuntimeException("Hive was read-only", e);
			}
		config = reader.getHiveConfiguration();
		factory = getSessionFactory();
	}
	
	public void addEntity(final Class clazz, Schema schema) {
		addEntity(clazz, Lists.newList(schema));
	}
	public void addEntity(final Class clazz, Collection<Schema> schemaList) {
		addEntity(clazz, new LazyInitializer(schemaList,
				new Delay<ClassDaoService>(){
					public ClassDaoService f() {
						return new BaseClassDaoService(
								ConfigurationReader.readConfiguration(clazz),
								new BaseDataAccessObject(
										config.getEntityConfig(clazz),
										hive,
										factory));
					}}));
	}
	
	public static void addEntity(Class clazz) {
		entityClasses.add(clazz);
	}
	
	public static void addEntity(Class clazz, LazyInitializer initializer) {
		entityClasses.add(clazz);
		services.add(initializer);
	}
	
	public static void clearEntities() {
		entityClasses.clear();
		services.clear();
	}
	
	@SuppressWarnings("unchecked")
	@BeforeClass
	public void initializeDataProvider() {
	}
	
	@SuppressWarnings("unused")
	@DataProvider(name = "service")
	protected Iterator<?> getServices() {
		return new TestIterator(services.iterator(),this);
	}
	
	@SuppressWarnings("deprecation")
	protected Class<?>[] getEntityClasses() {
		return (Class<?>[]) entityClasses.toArray(new Class<?>[]{});
	}

	@Test(dataProvider = "service")
	public void saveAndRetrieveInstance(ClassDaoService service) throws Exception {
		Object original = getPersistentInstance(service);
		Object response = service.get(getId(original));
		validateRetrieval(original, response, Arrays.asList(new String[] {}));
	}
	
// We currently do allow null properties to save because the only validation is on retrieve
// TODO add validation to save
//	@Test(dataProvider = "service")
//	public void saveAndRetrieveInstanceWithNullProperties(final ClassDaoService service) throws Exception {
//		AssertUtils.assertThrows(new Toss() {
//			public void f() throws Exception {
//				Object original = getPersistentInstanceWithNullProperties(service);	
//			}
//		}, HiveRuntimeException.class);
//	}
	
//	@Test(dataProvider = "service")
//	public void saveAndUpdateToEmptyCollectionProperties(final ClassDaoService service) throws Exception {
//		Object original = getPersistentInstance(service);
//		final EntityConfig entityConfig = config.getEntityConfig(toClass(service.getPersistedClass()));
//		Object update = new GenerateInstance<Object>((Class<Object>) entityConfig.getRepresentedInterface()).generateAndCopyProperties(original);
//		for (Method getter : ReflectionTools.getCollectionGetters(entityConfig.getRepresentedInterface())) {
//			String property = ReflectionTools.getPropertyNameOfAccessor(getter);
//			GeneratedInstanceInterceptor.setProperty(update, property, Collections.emptyList());
//		}
//		save(service, update);
//		Assert.assertEquals(service.get(getId(original)), update);
//		
//	}

	protected Object save(final ClassDaoService service, Object update) {
		return service.save(update);
	}
	protected Collection saveAll(ClassDaoService service, Collection<? extends Object> instances) {
		return service.saveAll(instances);
	}
	
	@Test(dataProvider = "service")
	public void saveAndRetrieveInstanceWithAllowedNullProperties(final ClassDaoService service) throws Exception {
		final Class<Object> clazz = (Class<Object>) Class.forName(service.getPersistedClass());
		Object instance = getInstance(clazz);
		for (Method getter : ReflectionTools.getGetters(clazz))
		{
			final String property = ReflectionTools.getPropertyNameOfAccessor(getter);
			final Validate annotation = (Validate)AnnotationHelper.getAnnotationDeeply(clazz, property, Validate.class);
			if (annotation != null && Class.forName(annotation.value()).equals(NoValidator.class) && !getter.getReturnType().isAssignableFrom(Collection.class))
			{
				ReflectionTools.invokeSetter(instance, property, null);
			}
		}
		save(service, instance);
		Object actual = service.get(config.getEntityConfig(clazz).getId(instance));
		for (Method getter : ReflectionTools.getGetters(clazz))
		{
			if(!getter.getReturnType().isAssignableFrom(Collection.class) &&
				AnnotationHelper.getAnnotationDeeply(clazz, ReflectionTools.getPropertyNameOfAccessor(getter), GeneratorIgnore.class) == null)
				Assert.assertEquals(getter.invoke(instance).hashCode(), getter.invoke(actual).hashCode()); // hashCode for Date comparisons
		}
	}

	@Test(dataProvider = "service")
	public void retrieveInstancesUsingAllIndexedProperties(final ClassDaoService service) throws Exception {
		final Object original = getPersistentInstance(service);
		if (original != null) {
			final EntityConfig entityConfig = config.getEntityConfig(toClass(service.getPersistedClass()));			
			for(EntityIndexConfig entityIndexConfig : entityConfig.getEntityIndexConfigs()) {
				final String indexPropertyName = entityIndexConfig.getPropertyName();
				// test retrieval with the single value or each item of the list of values
				for (Object value : entityIndexConfig.getIndexValues(original)) {		
					validateEnityIndexValue(service, original, indexPropertyName, value);
				}
			}
		}
	}

	private void validateEnityIndexValue(final ClassDaoService service,
			final Object original, final String indexPropertyName, Object value)
			throws IllegalAccessException, InvocationTargetException {
		Collection response = service.getByReference(indexPropertyName, value);
		if (response.size() == 0)
			System.err.println(String.format("getByReference returned no results for proeprty %s, value %s: here are the entities in the database: %s", indexPropertyName, value, service.getAll()));
		validateRetrieval(Collections.singletonList(original), response, Arrays.asList(new String[] {indexPropertyName}));
	}
	
	@Test(dataProvider = "service")
	public void retrieveInstancesUsingSubclassFinders(ClassDaoService service) throws Exception {
		if (service.getClass().equals(BaseClassDaoService.class))
			return;
		final Object original = getPersistentInstance(service);
		// Any method returning a ServiceResponse will be tested
		Collection<Method> finders =
			Transform.map(new Unary<Method, Method>() {
				public Method f(Method method) {
					return ReflectionTools.getMethodOfOwner(method);
				}},
				Filter.grep(new Predicate<Method>() {
					public boolean f(Method method) {
						return method.getReturnType().equals(ServiceResponse.class);
					}},
					ReflectionTools.getOwnedMethods(service.getClass())));
		final Object storedInstance = original;
		final EntityConfig entityConfig = config.getEntityConfig(storedInstance.getClass());
		for(final Method finder : finders) {
			Collection<Object> argumentValues = Transform.map(new Unary<Annotation[], Object>() {
				public Object f(Annotation[] annotations) {
					final Annotation annotation = Filter.grepSingleOrNull(
						new Predicate<Annotation>() {
							public boolean f(Annotation annotation) {
								return annotation.annotationType().equals(IndexParam.class);
							}
						},
						Arrays.asList(annotations));
					if (annotation == null) {
						throw new RuntimeException(
							String.format("Cannot resolve the index of parameter(s) of method %s, add an IndexParam annotation to each parameter", finder.getName()));
					}
					EntityIndexConfig entityIndexConfig = Atom.getFirstOrNull(Filter.grep(new Predicate<EntityIndexConfig>() {
						public boolean f(EntityIndexConfig entityIndexConfig) {
							return entityIndexConfig.getIndexName().equals(((IndexParam)annotation).value());
						}}, entityConfig.getEntityIndexConfigs()));
					// For EntityIndexConfig indexes
					if (entityIndexConfig != null)
						return Atom.getFirstOrThrow(entityIndexConfig.getIndexValues(storedInstance));
					// For DataIndex indexes
					else
						return ReflectionTools.invokeGetter(storedInstance, ((IndexParam)annotation).value());
				}},
				(Iterable<? extends Annotation[]>)Arrays.asList(finder.getParameterAnnotations()));
			ServiceResponse response = (ServiceResponse) finder.invoke(service, argumentValues.toArray());
			validateRetrieval(original, response, Arrays.asList(new Method[] {finder}));
		}
	}
	
	@Test(dataProvider = "service")
	public void testGetByCount(final ClassDaoService service) throws Exception {
		final Object original = getPersistentInstance(service);
		if (original != null) {
			final EntityConfig entityConfig = config.getEntityConfig(toClass(service.getPersistedClass()));			
			for(EntityIndexConfig entityIndexConfig : entityConfig.getEntityIndexConfigs()) {
				final String indexPropertyName = entityIndexConfig.getPropertyName();
				// test retrieval with the single value or each item of the list of values
				for (Object value : entityIndexConfig.getIndexValues(original)) {	
					Assert.assertEquals((Integer)1, service.getCountByReference(indexPropertyName, value));
				}
				
			}
		}
	}
	
	@Test(dataProvider = "service")
	public void notDetectTheExistenceOfNonPersistentEntities(ClassDaoService service) throws Exception {
		assertFalse(service.exists(getId(getInstance(toClass(service.getPersistedClass())))));
	}
	
	@Test(dataProvider = "service")
	public void detectTheExistenceOfPersistentEntities(ClassDaoService service) throws Exception {
		assertTrue(service.exists(getId(getPersistentInstance(service))));
	}
	
	@Test(dataProvider = "service")
	public void saveMultipleInstances(ClassDaoService service) throws Exception {
		List<Object> instances = Lists.newArrayList();
		for(int i=0; i<INSTANCE_COUNT; i++)
			instances.add(getInstance(toClass(service.getPersistedClass())));
		saveAll(service, instances);
		for(Object instance : instances)
			validateRetrieval(
					instance, 
					service.get(getId(instance)),
					Collections.emptyList());
	}

	
	
	@Test(dataProvider = "service")
	public void testUpdateComplexCollectionItems(ClassDaoService service) throws Exception {
		Object original = getPersistentInstance(service);
		final EntityConfig entityConfig = config.getEntityConfig(toClass(service.getPersistedClass()));
		Object updated = service.get(entityConfig.getId(original));
		for (final EntityIndexConfig entityIndexConfig : entityConfig.getEntityIndexConfigs()) {
			final String propertyName = entityIndexConfig.getPropertyName();
			if (ReflectionTools.isComplexCollectionItemProperty(entityConfig.getRepresentedInterface(), propertyName)) {
				// Test collection item updates
				final List<Object> updatedItems = new ArrayList<Object>((Collection<Object>)ReflectionTools.invokeGetter(updated, propertyName));
				
				// We don't delete orphans for many-to-* relationships so we currently can't delete
				// Delete the first
				//updatedItems.remove(0);
					
				// Update the second
				final Object updateItem = updatedItems.get(0);
				final Collection<String> simpleProperties = ReflectionTools.getPropertiesOfPrimitiveGetters(updateItem.getClass());
				
				Object generatedPrimitiveValue=null;
				if (simpleProperties.size() > 0) {
					final String firstProperty = Atom.getFirst(
							Filter.grepFalseAgainstList(Collections.singleton(entityIndexConfig.getInnerClassPropertyName()), simpleProperties));
					generatedPrimitiveValue = new GeneratePrimitiveValue<Object>(
							(Class<Object>) ReflectionTools.getPropertyType(updateItem.getClass(), firstProperty)).generate();
					ReflectionTools.invokeSetter(updateItem, firstProperty, generatedPrimitiveValue);
				}
				// Add a third
				final Object generated = new GenerateInstance<Object>((Class<Object>) updateItem.getClass()).generate();
				updatedItems.add(generated);
				
				GeneratedInstanceInterceptor.setProperty(updated, propertyName, updatedItems);
				save(service, updated);
				final Object persisted = service.get(entityConfig.getId(updated));
				// Check the updated collection
					// size should be equal
				final Collection<Object> persistedItems = new ArrayList<Object>((Collection<Object>)ReflectionTools.invokeGetter(persisted, propertyName));
				Assert.assertEquals(persistedItems.size(), entityIndexConfig.getIndexValues(updated).size());
					// first item should be removed
	//			final Collection<Object> originalItems = new ArrayList<Object>((Collection<Object>)ReflectionTools.invokeGetter(original, propertyName));
	//			assertFalse(Filter.grepItemAgainstList(Atom.getFirst(originalItems), persistedItems));
					// should be an updated item 
				if (simpleProperties.size() > 0) {
					final String firstProperty = Atom.getFirst(
							Filter.grepFalseAgainstList(Collections.singleton(entityIndexConfig.getInnerClassPropertyName()), simpleProperties));
					assertTrue(Filter.grepItemAgainstList(generatedPrimitiveValue,
						Transform.map(new Unary<Object,Object>() {
							public Object f(Object item) {
								return ReflectionTools.invokeGetter(item, firstProperty);
						}}, persistedItems)));
				}
					// new item should exist, compared the first property to check
					// we can't do an equality check because our property class may not have an entityId attribute, which we use for comparison
				assertTrue(Filter.grepItemAgainstList(Atom.getLast(updatedItems), persistedItems, new BinaryPredicate<Object, Object>() {
					final String firstProperty = Atom.getFirst(
							Filter.grepFalseAgainstList(Collections.singleton(entityIndexConfig.getInnerClassPropertyName()), simpleProperties));
					@Override
					public boolean f(Object item1, Object item2) {
						return ReflectionTools.invokeGetter(item1, firstProperty).equals(ReflectionTools.invokeGetter(item2, firstProperty));
					}
				}));
			}
		}
	
	}
	
	@Test(dataProvider = "service")
	public void deleteAnInstance(ClassDaoService service) throws Exception {
		Object deleted = getPersistentInstance(service);
		service.delete(getId(deleted));
		assertFalse(service.exists(getId(deleted)));
	}
	
	protected Object getInstance(Class<Object> clazz) throws Exception {
		return new GenerateInstance<Object>(clazz).generate();
	}
	protected Object getPersistentInstance(ClassDaoService service) throws Exception {
		return save(service, getInstance(toClass(service.getPersistedClass())));
	}
	
	protected Object getInstanceWithNullProperties(Class<Object> clazz) throws Exception {
		Object instance = new GenerateInstance<Object>(clazz).generate();
		for (Method getter : ReflectionTools.getComplexGetters(clazz))
			GeneratedInstanceInterceptor.setProperty(
					instance,
					ReflectionTools.getPropertyNameOfAccessor(getter),
					null);
		return instance;
	}
	protected Object getPersistentInstanceWithNullProperties(ClassDaoService service) throws Exception {
		return save(service, getInstanceWithNullProperties(toClass(service.getPersistedClass())));
	}
	
	
	protected Serializable getId(Object instance) {
		return config.getEntityConfig(instance.getClass()).getId(instance);
	}
	
	protected void validateRetrieval(Object original, Object response, Collection arguments) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		if (original instanceof Collection)
			Assert.assertEquals(new HashSet((Collection)response).hashCode(), new HashSet((Collection)original).hashCode(), String.format("Validation failed for arguments %s", arguments));
		else
			Assert.assertEquals(response.hashCode(), original.hashCode(), String.format("Validation failed for arguments %s", arguments));
	}
	
	@Override
	public Collection<String> getDatabaseNames() {
		Collection<String> dbs = Lists.newArrayList();
		dbs.addAll(getDataNodeNames());
		if(!dbs.contains(getHiveDatabaseName()))
			dbs.add(getHiveDatabaseName());
		return dbs;
	}

	private Collection<String> getDataNodeNames() {
		return Collections.singletonList(getHiveDatabaseName());
	}

	public String getHiveDatabaseName() {
		return "hive";
	}

	private Hive getHive() {
		return Hive.load(getConnectString(getHiveDatabaseName()), CachingDataSourceProvider.getInstance());
	}

	private HiveSessionFactory getSessionFactory() {
		return new HiveSessionFactoryBuilderImpl(
				getConnectString(getHiveDatabaseName()), 
				Arrays.asList(getEntityClasses()), 
				new SequentialShardAccessStrategy());
	}
	
	protected Class<Object> toClass(String className) {
		try {
			return (Class<Object>) Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
