package org.hivedb.util.database.test;


import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;

import org.hivedb.annotations.EntityId;
import org.hivedb.annotations.GeneratedClass;
import org.hivedb.annotations.Index;
import org.hivedb.annotations.IndexType;
import org.hivedb.annotations.PartitionIndex;
import org.hivedb.annotations.Resource;

@GeneratedClass("WeatherReportGenerated")
@Resource("WeatherReport")
public interface WeatherReport {
	
	public static final String REPORT_ID = "reportId";
	public static final String TEMPERATURE = "temperature";
	public static final String CONTINENT = "continent";
    
    @PartitionIndex
	String getContinent();
    @Index(type=IndexType.Data)
	BigDecimal getLatitude();
	BigDecimal getLongitude();
	
	@EntityId
	Integer getReportId();
	Date getReportTime();

	@Index
	int getTemperature();
	
	@Index
	Collection<Integer> getWeeklyTemperatures();
	
	@Index(type=IndexType.Data)
	Collection<WeatherEvent> getWeatherEvents();
	
	void setContinent(String value);
	void setLatitude(BigDecimal value);
	void setLongitude(BigDecimal value);
	void setReportId(Integer value);
	void setReportTime(Date value);
	void setTemperature(int value);
	void setWeeklyTemperatures(Collection<Integer> values);
	void setWeatherEvents(Collection<WeatherEvent> weatherEvents);
}
