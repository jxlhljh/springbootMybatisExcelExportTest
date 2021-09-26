 package cn.gzsendi.config.mybatis.wrapper;

import java.util.Map;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.wrapper.MapWrapper;

import com.google.common.base.CaseFormat;

//https://blog.csdn.net/u014717572/article/details/84451041
//SpringBoot+Mybatis,返回Map的时候,将Map内的Key转换为驼峰的命名表达式
public class CustomWrapper extends MapWrapper {

	public CustomWrapper(MetaObject metaObject, Map<String, Object> map) {
		super(metaObject, map);
	}

	@Override
	public String findProperty(String name, boolean useCamelCaseMapping) {
		if (useCamelCaseMapping) {
			// CaseFormat是引用的 guava库,里面有转换驼峰的,免得自己重复造轮子,pom添加
			/**
			 ** <dependency> <groupId>com.google.guava</groupId>
			 * <artifactId>guava</artifactId> <version>24.1-jre</version>
			 * </dependency>
			 **/
			return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name);
		}
		return name;
	}

}
