/**
 * code generation
 */
package com.sxjun.retrieval.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;

import com.jfinal.core.Controller;
import com.sxjun.core.common.utils.DictUtils;
import com.sxjun.retrieval.pojo.FiledSpecialMapper;

/**
 * 特殊字段映射Controller
 * @author sxjun
 * @version 2014-01-14
 */
public class FiledSpecialMapperController extends Controller {

	public void index() {
	}
	
	public void list() {
		List<FiledSpecialMapper> filedSpecialMapperList = getFiledSpecialMapperList(); 
		setAttr("filedSpecialMapper",filedSpecialMapperList);
		render("filedSpecialMapperList.jsp");
	}
	
	public void form(){
		Map<String,String> fieldtypes = DictUtils.getDictMap(DictUtils.FIELD_TYPE);
		String id = getPara();
		if(StringUtils.isNotBlank(id))
			setAttr("filedSpecialMapper",getFiledSpecialMapperList().get(0));
		setAttr("specialTypes",fieldtypes);
		render("filedSpecialMapperForm.jsp");
	}
	
	public void save(){
		FiledSpecialMapper filedSpecialMapper = getModel(FiledSpecialMapper.class);
		render("filedSpecialMapperForm.jsp");
	}
	
	public void delete(){
		String id=getPara();
		list();
	}

	public List<FiledSpecialMapper> getFiledSpecialMapperList(){
		List<FiledSpecialMapper> l = new ArrayList<FiledSpecialMapper>();
		FiledSpecialMapper filedSpecialMapper = new FiledSpecialMapper();
		filedSpecialMapper.setId(UUID.randomUUID().toString());
		filedSpecialMapper.setSqlField("page");
		filedSpecialMapper.setSpecialType("0");
		l.add(filedSpecialMapper);
		return l;
	}
}
