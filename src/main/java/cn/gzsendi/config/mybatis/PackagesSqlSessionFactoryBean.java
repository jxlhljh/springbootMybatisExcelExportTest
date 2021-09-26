package cn.gzsendi.config.mybatis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils; //注意:不一定非得是lang3包 lang包也可以  
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

/** 
* @ClassName: PackagesSqlSessionFactoryBean 
* @Description: mybatis自动扫描别名路径（新增通配符匹配功能） 
* @author wzf
* @date 2019年1月21日 
*/
@Slf4j
public class PackagesSqlSessionFactoryBean extends SqlSessionFactoryBean { // org.mybatis.spring包中的类
	
	static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";
	
	public void setTypeAliasesPackage(String typeAliasesPackage) {
		
		ResourcePatternResolver resolver = (ResourcePatternResolver) new PathMatchingResourcePatternResolver();  
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);  
        typeAliasesPackage = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +  
                ClassUtils.convertClassNameToResourcePath(typeAliasesPackage) + "/" + DEFAULT_RESOURCE_PATTERN;
        
      //将加载多个绝对匹配的所有Resource  
        //将首先通过ClassLoader.getResource("META-INF")加载非模式路径部分  
        //然后进行遍历模式匹配  
        try {  
            List<String> result = new ArrayList<String>();  
            Resource[] resources =  resolver.getResources(typeAliasesPackage);  
            if(resources != null && resources.length > 0){  
                MetadataReader metadataReader = null;  
                for(Resource resource : resources){  
                    if(resource.isReadable()){  
                       metadataReader =  metadataReaderFactory.getMetadataReader(resource);  
                        try {  
                            result.add(Class.forName(metadataReader.getClassMetadata().getClassName()).getPackage().getName());  
                        } catch (ClassNotFoundException e) {  
                            e.printStackTrace();  
                        }  
                    }  
                }  
            }  
            if(result.size() > 0) {  
                super.setTypeAliasesPackage(StringUtils.join(result.toArray(), ","));
                //super.setObjectWrapperFactory(new MapWrapperFactory());
            }else{  
                log.warn("参数typeAliasesPackage:"+typeAliasesPackage+"，未找到任何包");  
            }  
            //logger.info("d");  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
		
	}

}
