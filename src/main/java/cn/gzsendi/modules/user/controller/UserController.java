package cn.gzsendi.modules.user.controller;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import cn.gzsendi.modules.user.model.User;
import cn.gzsendi.modules.user.service.UserService;


@RestController
@RequestMapping("/user")
public class UserController {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	private UserService userService;

	//1.插入1000101行测试数据
	//http://localhost:8080/test/user/batchInsert
	@RequestMapping(value="/batchInsert", method = RequestMethod.GET)
	public String batchInsert(){

		logger.info("method starting...");
		long startTime = System.currentTimeMillis();
		
		//每次批量插入2000条记录，提高插入效率
		int batchSize = 2000;

		List<User> list = new LinkedList<User>();
		for(int i=0;i<1000101;i++){
			
			User user = new User();
			user.setUsername("name"+ i);
			user.setAge(18);
			
			list.add(user);
			
			if(list.size()>0 && list.size() % batchSize == 0) {
				userService.batchInsert(list);
				logger.info("has batchInsert size: {}", i);
				list.clear();//清除list
			}
			
		}
		
		long endTime = System.currentTimeMillis();

		logger.info("method finished,total spend time: {} ms.",(endTime-startTime));

		return "batchInsert";
	}
	
	//http://localhost:8080/test/user/queryById
	@RequestMapping(value="/queryById", method = RequestMethod.GET)
	public Object queryById(){
		
		logger.info("method starting...");
		long startTime = System.currentTimeMillis();
		
		User returnObject = userService.queryById(1);
		
		long endTime = System.currentTimeMillis();

		logger.info("method finished,total spend time: {} ms.",(endTime-startTime));
		
		return returnObject;
		
	}

	//http://localhost:8080/test/user/resultHandlerTest
	@RequestMapping(value="/resultHandlerTest", method = RequestMethod.GET)
	public Object resultHandlerTest(){

		logger.info("method starting...");
		long startTime = System.currentTimeMillis();

		userService.resultHandlerTest();

		long endTime = System.currentTimeMillis();

		logger.info("method finished,total spend time: {} ms.",(endTime-startTime));

		return "ok";

	}
	
	//http://localhost:8080/test/user/export
	@RequestMapping(value="/export", method = RequestMethod.GET)
	public Object export(){

		logger.info("method starting...");
		long startTime = System.currentTimeMillis();

		//调用service方法进行excel的导出
		userService.export();

		long endTime = System.currentTimeMillis();

		logger.info("method finished,total spend time: {} ms.",(endTime-startTime));

		return "ok";

	}

}
