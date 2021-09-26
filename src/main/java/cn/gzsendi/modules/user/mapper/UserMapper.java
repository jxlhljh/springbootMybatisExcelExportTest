package cn.gzsendi.modules.user.mapper;

import java.util.List;

import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Repository;

import cn.gzsendi.config.mybatis.UsingDefaultDB;
import cn.gzsendi.modules.user.model.User;

@UsingDefaultDB
@Repository
public interface UserMapper {
	
	int batchInsert(List<User> list);
	
	/**根据主键查询*/
	public User queryById(Integer id);

	/**ResultHandler测试，这里我没加参数，可以加上你的条件参数**/
	public void resultHandlerTest(ResultHandler<User> resultHandler);
	
	/**导出，mapper的方法需要是void返回，并且参数中含ResultHandler(流式查询遍历的条件)，这里我没加参数，可以加上你的条件参数*/
	public void export(ResultHandler<User> resultHandler);
}
