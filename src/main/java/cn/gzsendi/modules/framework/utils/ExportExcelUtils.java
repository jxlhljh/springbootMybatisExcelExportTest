package cn.gzsendi.modules.framework.utils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.ResultContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.gzsendi.modules.framework.reflect.Reflector;
import cn.gzsendi.modules.framework.reflect.reflectasm.MethodAccessor;

public abstract class ExportExcelUtils {
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private AtomicInteger currentRowNumber = new AtomicInteger(0);//记录当前excel行号，从0开始
	private Sheet sheet = null;

	private List<String> headerArray = Arrays.asList("姓名","年龄");
	private List<String> fieldArray = Arrays.asList("username","age");
	
	//定义totalCellNumber变量，
	private int totalCellNumber = headerArray.size();

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	//定义要导出的excel文件名,不带xlsx后缀,默认为uuID,也可以通过构造函数传进来进行改变。
	private String exportFileName = UUID.randomUUID().toString().replace("-", "");

	public ExportExcelUtils(List<String> headerArray,List<String> fieldArray){

		this.headerArray = headerArray;
		this.fieldArray = fieldArray;

	}

	public ExportExcelUtils(List<String> headerArray,List<String> fieldArray,String exportFileName){

		this.headerArray = headerArray;
		this.fieldArray = fieldArray;
		this.exportFileName = exportFileName;

	}

	//出象方法，提供给子类进行实现，遍历写入数据到excel
	public abstract void tryFetchDataAndWriteToExcel();
	
	/**导出*/
	public void export() {
		
		HttpServletResponse response = ContextHolderUtils.getResponse();
		
		ZipOutputStream zos = null;
		
		try {
			
			logger.info("--------->>>>写入Excel开始.." );
			
			//写入文件
			response.setContentType("application/octet-stream");
			response.setHeader("Content-Disposition", "attachment;filename=" + new String((exportFileName+".zip").replaceAll(" ", "").getBytes("utf-8"),"iso8859-1"));
			OutputStream os = new BufferedOutputStream(response.getOutputStream());
			
			zos = new ZipOutputStream(os);
			
			ZipEntry zipEntry = new ZipEntry(new String((exportFileName+".xlsx").replaceAll(" ", "")));
			zos.putNextEntry(zipEntry);
			
			SXSSFWorkbook wb = new SXSSFWorkbook();//默认100行，超100行将写入临时文件
			wb.setCompressTempFiles(false); //是否压缩临时文件，否则写入速度更快，但更占磁盘，但程序最后是会将临时文件删掉的
            sheet = wb.createSheet("Sheet 1");
			
	        //写入表头，Rows从0开始.
	        Row row = sheet.createRow(0);
	        for (int cellNumber = 0; cellNumber < totalCellNumber; cellNumber++) {
	        	
	        	Cell cell = row.createCell(cellNumber);
                cell.setCellValue(headerArray.get(cellNumber)); //写入表头数据
	        	
	        }
	        
	        //写入数据
		    /****************************/
	        //调用具体的实现子类的代码，尝试获取数据进行遍历并写入excel
	        tryFetchDataAndWriteToExcel();

	        //最后打印一下最终写入的行数
			logger.info("--------->>>> write to excel size now is {}", currentRowNumber.get() );
	        
	        //Write excel to a file
	        wb.write(zos);
	        
	        if (wb != null) {
            	wb.dispose();// 删除临时文件，很重要，否则磁盘可能会被写满
            }
	        
	        wb.close();
	        
		    /****************************/
	  		
	  		logger.info("--------->>>>全部数据写入Excel完成.." );
			
		} catch (Exception e) {
			
			logger.error("error",e);
			
		} finally {
			
			//关闭资源
	    	try {if(zos!=null) zos.close();} catch (IOException e1) {logger.error("error",e1);	}
	    	  
		}

	}

	//写入一行数据到excel中,提供给ResultHandler中遍历时进行回调调用
	@SuppressWarnings("rawtypes")
	public void callBackWriteRowdataToExcel(Object aRowData) {

		//反射获取值并设置到excel的中cell列中
		MethodAccessor methodAccessor = Reflector.getMethodAccessor(aRowData.getClass());

		//先将行号增加
		currentRowNumber.incrementAndGet();
		//创建excel中新的一行
		Row row = sheet.createRow(currentRowNumber.get());
		for (int cellNumber = 0; cellNumber < totalCellNumber; cellNumber++) {

			//aRowData为map时，要特殊处理进行获取。不能通过methodAccessor反射调用.
			Object value = null;
			if(aRowData instanceof Map){
				value = ((Map)aRowData).get(fieldArray.get(cellNumber));
			}else {
				value = methodAccessor.getFieldValue(aRowData, fieldArray.get(cellNumber));
			}
			
			Cell cell = row.createCell(cellNumber);

			//date类型默认转换string格式化日期
            if (value!=null && value instanceof Date){
            	cell.setCellValue(sdf.format(value));//
            }else {
            	cell.setCellValue(value==null?"":value.toString());//写入数据
            }

		}

		//每写入5000条就打印一下
		if(currentRowNumber.get() % 5000 == 0 ){
			logger.info("--------->>>> write to excel size now is {}", currentRowNumber.get() );
		}
	}

}
