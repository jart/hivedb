package org.hivedb.hibernate;

import java.io.Serializable;
import java.util.Collection;

import org.hibernate.Criteria;
import org.hibernate.EmptyInterceptor;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.exception.JDBCConnectionException;
import org.hivedb.Hive;
import org.hivedb.HiveKeyNotFoundException;
import org.hivedb.annotations.IndexType;
import org.hivedb.configuration.EntityConfig;
import org.hivedb.configuration.EntityIndexConfig;
import org.hivedb.configuration.EntityIndexConfigDelegator;
import org.hivedb.util.Lists;
import org.hivedb.util.ReflectionTools;

public class BaseDataAccessObject implements DataAccessObject<Object, Serializable>{
	protected HiveSessionFactory factory;
	protected EntityConfig config;
	protected Class<?> clazz;
	protected Interceptor defaultInterceptor = EmptyInterceptor.INSTANCE;
	protected Hive hive;

	public Hive getHive() {
		return hive;
	}

	public void setHive(Hive hive) {
		this.hive = hive;
	}

	public BaseDataAccessObject(EntityConfig config, Hive hive, HiveSessionFactory factory) {
		this.clazz = config.getRepresentedInterface();
		this.config = config;
		this.factory = factory;
		this.hive = hive;
		
	}
	
	public BaseDataAccessObject(EntityConfig config, Hive hive, HiveSessionFactory factory, Interceptor interceptor) {
		this(config,hive,factory);
		this.defaultInterceptor = interceptor;
	}
	
	public Serializable delete(final Serializable id) {
		SessionCallback callback = new SessionCallback() {
			public void execute(Session session) {
				Object deleted = get(id, session);
				session.delete(deleted);
			}};
		doInTransaction(callback, getSession());
		return id;
	}

	public Boolean exists(Serializable id) {
		EntityConfig entityConfig = config;
		return hive.directory().doesResourceIdExist(entityConfig.getResourceName(), id);
	}

	public Object get(final Serializable id) {
		Object entity = null;
		Session session = null;
		try {
			session = getSession();
			return get(id, session);
		} finally {
			if(session != null)
				session.close();
		}
	}
	
	private Object get(Serializable id, Session session) {
		Object fetched = session.get(getRespresentedClass(), id);
		return fetched;
	}
	
	@SuppressWarnings("unchecked")
	public Collection<Object> findByProperty(String propertyName, Object propertyValue) {
		EntityIndexConfig indexConfig = config.getEntityIndexConfig(propertyName);
		Session session = createSessionForIndex(config, indexConfig, propertyValue);
		Criteria criteria = session.createCriteria(config.getRepresentedInterface());
		
		if (ReflectionTools.isCollectionProperty(config.getRepresentedInterface(), propertyName))
			if (ReflectionTools.isComplexCollectionItemProperty(config.getRepresentedInterface(), propertyName)) {
				criteria.createAlias(propertyName, "x")
					.add( Restrictions.eq("x." + indexConfig.getInnerClassPropertyName(), propertyValue));
			}
			else
				criteria.add(Restrictions.eq(propertyName, propertyValue));
		else
			criteria.add( Restrictions.eq(propertyName, propertyValue)); 
		return findByProperty(session, criteria);
	}
	
	public Collection<Object> findByProperty(String propertyName, Object propertyValue, Integer firstResult, Integer maxResults) {
		EntityConfig entityConfig = config;
		EntityIndexConfig indexConfig = entityConfig.getEntityIndexConfig(propertyName);
		Session session = createSessionForIndex(entityConfig, indexConfig, propertyValue);
		Criteria criteria = session.createCriteria(entityConfig.getRepresentedInterface());
		criteria.add( Restrictions.eq(indexConfig.getPropertyName(), propertyValue));
		criteria.setFirstResult(firstResult);
		criteria.setMaxResults(maxResults);
		criteria.addOrder(Order.asc(propertyName));
		return findByProperty(session, criteria);
	}
	
	private Session createSessionForIndex(EntityConfig entityConfig, EntityIndexConfig indexConfig, Object propertyValue) {
		if (indexConfig.getIndexType().equals(IndexType.Delegates))
			return factory.openSession(
					((EntityIndexConfigDelegator)indexConfig).getDelegateEntityConfig().getResourceName(),
					propertyValue);
		else if (indexConfig.getIndexType().equals(IndexType.Hive))
			return factory.openSession(
					entityConfig.getResourceName(), 
					indexConfig.getIndexName(), 
					propertyValue);
		else if (indexConfig.getIndexType().equals(IndexType.Data))
			return factory.openAllShardsSession();
		throw new RuntimeException(String.format("Unknown IndexType: %s", indexConfig.getIndexType()));
	}
	
	public Collection<Object> findByPropertyRange(String propertyName, java.lang.Object minValue, java.lang.Object maxValue) {
		// Use an AllShardsresolutionStrategy + Criteria
		EntityIndexConfig indexConfig = config.getEntityIndexConfig(propertyName);
		Collection<Object> entities = Lists.newArrayList();
		Session session = factory.openAllShardsSession();
		Criteria criteria = session.createCriteria(config.getRepresentedInterface());
		criteria.add( Restrictions.between(propertyName, minValue, maxValue));
		return findByProperty(session, criteria);
	}
	
	public Collection<Object> findByPropertyRange(String propertyName, Object minValue, Object maxValue, Integer firstResult, Integer maxResults) {
		// Use an AllShardsresolutionStrategy + Criteria
		Session session = factory.openAllShardsSession();
		Criteria criteria = session.createCriteria(config.getRepresentedInterface());
		criteria.add( Restrictions.between(propertyName, minValue, maxValue));
		criteria.setFirstResult(firstResult);
		criteria.setMaxResults(maxResults);
		criteria.addOrder(Order.asc(propertyName));
		return findByProperty(session, criteria);
	}
	
	private Collection<Object> findByProperty(Session session, Criteria criteria) {
		try {
			return criteria.list();
		} finally {
			if(session != null)
				session.close();
		}
	}

	public Object save(final Object entity) {
		SessionCallback callback = new SessionCallback(){
			public void execute(Session session) {
				session.saveOrUpdate(getRespresentedClass().getName(),entity);
			}};
		doInTransaction(callback, getSession());
		return entity;
	}

	public Collection<Object> saveAll(final Collection<Object> collection) {
		SessionCallback callback = new SessionCallback(){
			public void execute(Session session) {
				for(Object entity : collection) 
					session.saveOrUpdate(getRespresentedClass().getName(), entity);
			}};
		doInTransaction(callback, getSession());
		return collection;
	}
	
	protected Session getSession() {
		return factory.openSession(factory.getDefaultInterceptor());
	}

	@SuppressWarnings("unchecked")
	public Class<Object> getRespresentedClass() {
		return (Class<Object>) EntityResolver.getPersistedImplementation(clazz);
	}
	
	public Interceptor getInterceptor() {return this.defaultInterceptor;}
	
	public void setInterceptor(Interceptor i) {this.defaultInterceptor = i;}
	
	public static void doInTransaction(SessionCallback callback, Session session) {
		Transaction tx = null;
		try {
			tx = session.beginTransaction();
			callback.execute(session);
			tx.commit();
		} catch (JDBCConnectionException ce) {
			throw new RuntimeException(ce);
		} catch( RuntimeException e ) {
			if(tx != null)
				try {
					tx.rollback();
				}
				catch (Exception ex){
					throw e;
				}
			throw e;
		} finally {
			session.close();
		}
	}
}
