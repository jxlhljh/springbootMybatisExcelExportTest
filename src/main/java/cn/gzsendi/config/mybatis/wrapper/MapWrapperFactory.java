package cn.gzsendi.config.mybatis.wrapper;

import java.util.Map;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

public class MapWrapperFactory implements ObjectWrapperFactory {
	
	@Override
	public boolean hasWrapperFor(Object object) {
		return object != null && object instanceof Map;
	}
	
	@Override
	public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
		return new CustomWrapper(metaObject,(Map)object);
	}
}