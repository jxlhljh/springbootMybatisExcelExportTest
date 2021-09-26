package cn.gzsendi.config;

import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;

import com.alibaba.druid.support.http.StatViewServlet;

//注意不要忘记在 SpringBootSampleApplication.java 上添加 @ServletComponentScan 注解，不然就是404了。
@WebServlet(urlPatterns="/druid/*",  
    initParams={  
         @WebInitParam(name="allow",value=""),// IP白名单(没有配置或者为空，则允许所有访问)  
         @WebInitParam(name="deny",value=""),// IP黑名单 (存在共同时，deny优先于allow)  
         @WebInitParam(name="loginUsername",value="admin"),// 用户名  
         @WebInitParam(name="loginPassword",value="123456"),// 密码
         @WebInitParam(name="resetEnable",value="true")// 启用HTML页面上的“Reset All”功能  
})  
public class DruidStatViewServlet extends StatViewServlet {  
    private static final long serialVersionUID = -2688872071445249539L;  
  
}  
