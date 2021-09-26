package cn.gzsendi.modules.framework.utils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

//https://www.cnblogs.com/christopherchan/p/11071098.html
public class JsonUtil {
	
	private final static Logger logger = LoggerFactory.getLogger(JsonUtil.class);
	
	//日期格式化
	private static final String STANDARD_FORMAT = "yyyy-MM-dd HH:mm:ss";
	
	private static ObjectMapper objectMapper;
	
	static{
		
		/** 
         * ObjectobjectMapper是JSON操作的核心，Jackson的所有JSON操作都是在ObjectobjectMapper中实现。 
         * ObjectobjectMapper有多个JSON序列化的方法，可以把JSON字符串保存File、OutputStream等不同的介质中。 
         * writeValue(File arg0, Object arg1)把arg1转成json序列，并保存到arg0文件中。 
         * writeValue(OutputStream arg0, Object arg1)把arg1转成json序列，并保存到arg0输出流中。 
         * writeValueAsBytes(Object arg0)把arg0转成json序列，并把结果输出成字节数组。 
         * writeValueAsString(Object arg0)把arg0转成json序列，并把结果输出成字符串。 
         */
        objectMapper = new ObjectMapper();
        
        //对象的所有字段全部列入
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        
        //取消默认转换timestamps形式
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,false);
        
        //忽略空Bean转json的错误
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS,false);
        
        //所有的日期格式都统一为以下的样式，即yyyy-MM-dd HH:mm:ss
        objectMapper.setDateFormat(new SimpleDateFormat(STANDARD_FORMAT));
        
        //忽略 在json字符串中存在，但是在java对象中不存在对应属性的情况。防止错误
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        
        objectMapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.ANY);
        
		
	}
	
	/**
     * 对象转Json格式字符串
     * @param obj 对象
     * @return Json格式字符串
     */
	public static String toJSONString(Object o) {
		
		if (o == null) {
            return null;
        }
		
		if (o instanceof String)
			return (String) o;
		
        String jsonValue = null;
		try {
			jsonValue = objectMapper.writeValueAsString(o);
		} catch (JsonProcessingException e) {
			logger.error("Parse Object to String error",e);
		}
		
		return jsonValue;
		
	}
	
	@SuppressWarnings("unchecked")
	public static Map<String,Object> castToObject(String str){
        if(str == null || "".equals(str) ){
            return null;
        }
        
        try {
            return objectMapper.readValue(str, Map.class);
        } catch (Exception e) {
        	logger.error("Parse String to Object error:", e);
            return null;
        }
        
    }
	
	/**
     * 字符串转换为自定义对象
     * @param str 要转换的字符串
     * @param clazz 自定义对象的class对象
     * @return 自定义对象
     */
	@SuppressWarnings("unchecked")
	public static <T> T castToObject(String str, Class<T> clazz){
        if(str == null || "".equals(str) || clazz == null){
            return null;
        }
        
        try {
            return clazz.equals(String.class) ? (T) str : objectMapper.readValue(str, clazz);
        } catch (Exception e) {
        	logger.error("Parse String to Object error:", e);
            return null;
        }
        
    }
	
	@SuppressWarnings("unchecked")
	public static <T> T castToObject(String str, TypeReference<T> typeReference) {
        if (str == null || "".equals(str) || typeReference == null) {
            return null;
        }
        try {
            return (T) (typeReference.getType().equals(String.class) ? str : objectMapper.readValue(str, typeReference));
        } catch (IOException e) {
        	logger.error("Parse String to Object error:", e);
            return null;
        }
    }
	
	public static <T> T castToObject(String str, Class<?> collectionClazz, Class<?>... elementClazzes) {
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(collectionClazz, elementClazzes);
        try {
            return objectMapper.readValue(str, javaType);
        } catch (IOException e) {
        	logger.error("Parse String to Object error : ", e.getMessage());
            return null;
        }
    }
	
	/*public static void main(String[] args) throws IOException, InterruptedException {
		User user1 = new User();
		user1.setUsername("liujinghua");
		user1.setAge(38);
		user1.setBirthydate(new Date());
		
		User user2 = new User();
		user2.setUsername("liujinghua2");
		user2.setAge(33);
		user2.setBirthydate(new Date());
		
		System.out.println(JsonUtil.castToObject("{\"username\":\"liujinghua\",\"age\":38,\"birthydate\":\"2021-01-18 15:45:21\",\"testkey\":\"testvalue\"}", User.class));
        
		List<User> userList = new ArrayList<>();
        userList.add(user1);
        userList.add(user2);
		
        System.out.println(JsonUtil.toJSONString(userList));
        List<User> userListBean = JsonUtil.castToObject(JsonUtil.toJSONString(userList), new TypeReference<List<User>>() {});
		System.out.println(userListBean);
		
		userListBean = JsonUtil.castToObject(JsonUtil.toJSONString(userList), List.class, User.class);
		System.out.println(userListBean);
		
		String valueStr = "{\"traceId\":\"20011111014001202101130630576926\",\"duration\":0,\"binaryAnnotations\":[{\"value\":\"132.121.80.208\",\"key\":\"bc.url\"},{\"value\":\"200170001087067500\",\"key\":\"bc.id\"},{\"value\":\"c\",\"key\":\"bc.type\"},{\"value\":\"13042053354\",\"key\":\"bz.servNo\"},{\"value\":\"11111014001\",\"key\":\"bz.scenarioCode\"},{\"value\":\"170001087067500\",\"key\":\"bz.orderNo\"},{\"value\":\"0\",\"key\":\"bz.resultCode\"}],\"name\":\"流量提醒\",\"annotations\":[{\"endpoint\":{\"port\":\"21\",\"ip\":\"132.121.148.140\",\"serviceName\":\"1005.OFCS\"},\"value\":\"cr\",\"timestamp\":1610270564000000},{\"endpoint\":{\"port\":\"21\",\"ip\":\"132.121.148.140\",\"serviceName\":\"1005.OFCS\"},\"value\":\"cs\",\"timestamp\":1610270564000000}],\"id\":\"20011111014001202101130630576927\",\"parentId\":\"\",\"timestamp\":1610270564000000}";
		
		Map<String,Object> map = JsonUtil.castToObject(valueStr);
		System.out.println(JsonUtil.toJSONString(map));
		
	}*/
	
}
