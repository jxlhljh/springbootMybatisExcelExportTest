package cn.gzsendi.config;

import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;

import com.alibaba.druid.support.http.WebStatFilter;

//注意不要忘记在 SpringBootSampleApplication.java 上添加 @ServletComponentScan 注解，不然就是404了。
@WebFilter(
		filterName = "druidWebStatFilter", urlPatterns = "/*", 
		initParams = { @WebInitParam(name = "exclusions", value = "weburi.json,.html,.js,.gif,.jpg,.png,.css,.ico,/druid/*") // 忽略资源
})
public class DruidStatFilter extends WebStatFilter {
}