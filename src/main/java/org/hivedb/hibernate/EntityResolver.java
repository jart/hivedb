package org.hivedb.hibernate;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.hivedb.annotations.AnnotationHelper;
import org.hivedb.annotations.HiveForeignKey;
import org.hivedb.configuration.EntityConfig;
import org.hivedb.configuration.EntityHiveConfig;
import org.hivedb.util.ReflectionTools;
import org.hivedb.util.functional.Filter;
import org.hivedb.util.functional.Pair;
import org.hivedb.util.functional.Predicate;
import org.hivedb.util.functional.Transform;
import org.hivedb.util.functional.Unary;

public class EntityResolver {
	
	private EntityHiveConfig entityHiveConfig;
	public EntityResolver(EntityHiveConfig entityHiveConfig) {
		this.entityHiveConfig = entityHiveConfig; 
	}
	public Class<?> resolveToEntityOrRelatedEntity(Class<?> clazz) {
		Class<?> entityInterface = resolveEntityInterface(clazz);
		if (entityInterface != null) 
			return  entityInterface;
		return getHiveForeignKeyAnnotatedMethod(clazz).getAnnotation(HiveForeignKey.class).value();
	}
	@SuppressWarnings("unchecked")
	public Map.Entry<Class<?>, Object>  resolveToEntityOrRelatedEntiyInterfaceAndId(Object entity) {
		Class clazz = entity.getClass();
		Class entityInterface = resolveEntityInterface(clazz);
		if (entityInterface != null) 
			return  new Pair<Class<?>, Object>(entityInterface,entityHiveConfig.getEntityConfig(entityInterface).getId(entity));
		Method method = getHiveForeignKeyAnnotatedMethod(clazz);
		try {
			return  new Pair<Class<?>, Object>(
					method.getAnnotation(HiveForeignKey.class).value(),
					method.invoke(entity, new Object[]{}));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public Class resolveEntityInterface(Class clazz) {
		return ReflectionTools.whichIsImplemented(
				clazz, 
				Transform.map(new Unary<EntityConfig, Class>() {
					public Class f(EntityConfig entityConfig) {
						return entityConfig.getRepresentedInterface();
					}},
					entityHiveConfig.getEntityConfigs()));
	}
	private Method getHiveForeignKeyAnnotatedMethod(Class clazz) {
		Method annotatedMethod = Filter.grepSingleOrNull(new Predicate<Method>() {
			public boolean f(Method method) {
				return method.getAnnotation(HiveForeignKey.class) != null;
		}}, Transform.flatMap(new Unary<Class, Collection<Method>>() {
			public Collection<Method> f(Class interfase) {
				return Arrays.asList(interfase.getMethods());
			}}, Arrays.asList(clazz.getInterfaces())));
		if (annotatedMethod == null)
			throw new RuntimeException(
				String.format("Class %s cannot be resolved to a Hive enity and does not reference a Hive entity", clazz.getCanonicalName()));
		return annotatedMethod;
	}

}