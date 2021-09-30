package cn.gzsendi.modules.user.service.impl;

import java.util.Arrays;
import java.util.List;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cn.gzsendi.modules.framework.utils.ExcelResultHandler;
import cn.gzsendi.modules.user.mapper.UserMapper;
import cn.gzsendi.modules.user.model.User;
import cn.gzsendi.modules.user.service.UserService;

@Service
public class UserServiceImpl implements UserService{
	
	@Autowired
	private UserMapper userMapper;
	
	@Override
	public int batchInsert(List<User> list) {
		
		return userMapper.batchInsert(list);
		
	}
	
	/**根据主键查询*/
	public User queryById(Integer id){
		
		return userMapper.queryById(id);
		
	}

	/**resultHandler写法测试*/
	public void resultHandlerTest(){
		userMapper.resultHandlerTest(new ResultHandler<User>() {
			@Override
			public void handleResult(ResultContext<? extends User> resultContext) {
				User user = resultContext.getResultObject();
				System.out.println("username:" +user.getUsername() + ",age:" +user.getAge());
			}
		});
	}

	/**导出*/
	public void export() {

		//定义导出的的表头，以及每个表头字段对应的对象变量名
		List<String> headerArray = Arrays.asList("姓名","年龄");
		List<String> fieldArray = Arrays.asList("username","age");

		//定义要导出的excel的文件名，不带"xlsx"后缀。
		String exportExcelFileName = "文件测试";
		
		//每次导出new一个handler对象，将headerArray,fieldArray,exportExcelFileName传递进去。
		ExcelResultHandler<User> handler = new ExcelResultHandler<User>(headerArray,fieldArray,exportExcelFileName) {
			public void tryFetchDataAndWriteToExcel() {
				//这里的this,指的就是ExcelResultHandler<User> handler这个对象，在这里写mapper调用获取数据的调用
				userMapper.export(this);
			}
		};
		
		//真正调用excel的导出开始，在方法中exportExcel会调用写excel表头，
		//然后调用tryFetchDataAndWriteToExcel，进行驱动调用userMapper的方法，然后遍历结果集，一条一条写入excel,最后关闭盯应的流资源。
		handler.startExportExcel();

		/**下面的方式类似，只是封装的方式不一样**/
		/*//调用ExportExcelUtils的公共方法进行excel的导出
		new ExportExcelUtils(headerArray,fieldArray,exportExcelFileName) {
			
			public void tryFetchDataAndWriteToExcel() {

				//通过流式查询（不占用内存）调用mybatis的查询，并遍历每一行数据进行excel的写入
				//不同的导出修改这里的代码即可，换成对应的的mapper进行调用
				//mapper的方法需要是void返回，并且参数中含ResultHandler(流式查询遍历的条件)
				userMapper.export(new ResultHandler<User>() {
					public void handleResult(ResultContext<? extends User> resultContext) {

						//获取数据，并回调ExportExcelUtils中的方法进行数据写入到excel，固定写法即可，不需要修改
						Object aRowData = resultContext.getResultObject();
						callBackWriteRowdataToExcel(aRowData);

					}
				});

			}
			
		}.export();*/
	}
}
