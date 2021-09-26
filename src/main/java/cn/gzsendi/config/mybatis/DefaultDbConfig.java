package cn.gzsendi.config.mybatis;

import cn.gzsendi.config.mybatis.wrapper.MapWrapperFactory;
import com.alibaba.druid.filter.logging.Slf4jLogFilter;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.WallFilter;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.SpringBootVFS;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;

//https://blog.csdn.net/u011943534/article/details/82260311
//springboot 添加druid监控，开启慢日志，配置spring监控
@Configuration  
@MapperScan(basePackages = {"cn.gzsendi.modules.**.mapper"},annotationClass = UsingDefaultDB.class, sqlSessionFactoryRef = "defaultSqlSessionFactory")
public class DefaultDbConfig {
	
	private String aliasesPackage = "cn.gzsendi.modules.**.model";
	
	@Value("${default.url}")
	private String url;
	
	@Value("${default.username}")
	private String username;
	
	@Value("${default.password}")
	private String password;
	
	@Bean(name = "defaultDataSource")
	@Primary
	public DataSource dataSource(){
		
		String driverClassName=  "com.mysql.cj.jdbc.Driver";//"com.mysql.jdbc.Driver";
		String validationQuery=  "select 1";
		
		DruidDataSource ds = new DruidDataSource();
		ds.setDriverClassName(driverClassName);
		ds.setUrl(url);
		ds.setUsername(username);
		ds.setPassword(password);
		ds.setInitialSize(1);//初始化数量
		ds.setMaxActive(20);//最大活跃数
		ds.setMinIdle(1);//
		ds.setMaxWait(60000);//最大等待超时时间
		ds.setValidationQuery(validationQuery);
		ds.setTestOnBorrow(false);
		ds.setTestOnReturn(false);
		ds.setTestWhileIdle(true);
		ds.setTimeBetweenEvictionRunsMillis(60000);//
		ds.setMinEvictableIdleTimeMillis(300000);//
		ds.setRemoveAbandoned(true);
		ds.setRemoveAbandonedTimeout(1800);
		ds.setLogAbandoned(true);
		
		//打开PSCache，并且指定每个连接PSCache的大小
		ds.setPoolPreparedStatements(false);
		ds.setMaxPoolPreparedStatementPerConnectionSize(20);
		
		try {
			ds.setFilters("stat");
		} catch (SQLException e) {
			e.printStackTrace();
		}
		ds.setProxyFilters(Arrays.asList(statFilter(),logFilter(),wallFilter()));

		return ds;
	}
	
	//@Bean
    //@Primary
    public StatFilter statFilter(){
        StatFilter statFilter = new StatFilter();
        statFilter.setSlowSqlMillis(5000);//大于5秒认为是慢查询Sql
        statFilter.setLogSlowSql(true);
        statFilter.setMergeSql(true);
        return statFilter;
    }
	
	//@Bean
    public Slf4jLogFilter logFilter(){
        Slf4jLogFilter filter = new Slf4jLogFilter();
//        filter.setResultSetLogEnabled(false);
//        filter.setConnectionLogEnabled(false);
//        filter.setStatementParameterClearLogEnable(false);
//        filter.setStatementCreateAfterLogEnabled(false);
//        filter.setStatementCloseAfterLogEnabled(false);
//        filter.setStatementParameterSetLogEnabled(false);
//        filter.setStatementPrepareAfterLogEnabled(false);
        return  filter;
    }
	
	@Bean(name = "defaultSqlSessionFactory")
    @Primary 
    public SqlSessionFactory sqlSessionFactory(@Qualifier("defaultDataSource") DataSource dataSource) throws Exception {
		
		//DefaultVFS在获取jar上存在问题，使用springboot只能修改
        VFS.addImplClass(SpringBootVFS.class);
		
		PackagesSqlSessionFactoryBean sqlSessionFactoryBean = new PackagesSqlSessionFactoryBean();  
        sqlSessionFactoryBean.setDataSource(dataSource);  
        sqlSessionFactoryBean.setTypeAliasesPackage(this.aliasesPackage);
        sqlSessionFactoryBean.setObjectWrapperFactory(new MapWrapperFactory());
        
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();  
        sqlSessionFactoryBean.setMapperLocations(resolver.getResources("classpath*:cn/gzsendi/modules/**/mapper/xml/*.xml"));  
        sqlSessionFactoryBean.getObject().getConfiguration().setMapUnderscoreToCamelCase(true);//开启驼峰支持
        
        return sqlSessionFactoryBean.getObject();  
    }  
	
	@Bean(name = "defaultTransactionManager")
    @Primary
    public DataSourceTransactionManager transactionManager(@Qualifier("defaultDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }



	private WallFilter wallFilter() {
		WallFilter wallFilter = new WallFilter();
		wallFilter.setConfig(wallConfig());
		return wallFilter;
	}


	private WallConfig wallConfig() {
		WallConfig config = new WallConfig();
		config.setMultiStatementAllow(true);//允许一次执行多条语句
		config.setNoneBaseStatementAllow(true);//允许非基本语句的其他语句
		return config;
	}


}
