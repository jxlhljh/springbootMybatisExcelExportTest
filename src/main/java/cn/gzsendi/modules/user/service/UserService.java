package cn.gzsendi.modules.user.service;

import java.util.List;

import cn.gzsendi.modules.user.model.User;

/**
 * Created by jxlhl on 2021/9/24.
 */
public interface UserService {
	
	int batchInsert(List<User> list);
	
	/**根据主键查询*/
	public User queryById(Integer id);

	/**resultHandler写法测试*/
	public void resultHandlerTest();

	/**导出*/
	public void export();

}
