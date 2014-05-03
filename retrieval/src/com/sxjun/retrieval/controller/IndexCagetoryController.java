/**
 * code generation
 */
package com.sxjun.retrieval.controller;

import java.util.Map;

import com.sxjun.retrieval.common.DictUtils;
import com.sxjun.retrieval.pojo.IndexCategory;

/**
 * 索引分类Controller
 * @author sxjun
 * @version 2014-01-14
 */
public class IndexCagetoryController extends BaseController<IndexCategory> {
	private final static String cachename = IndexCategory.class.getSimpleName();
	
	public void list() {
		list(cachename);
	}
	
	public void form(){
		
		Map<String,String> indexPathTypes = DictUtils.getDictMap(DictUtils.INDEXPATH_TYPE);
		setAttr("indexPathTypes",indexPathTypes);
		form(cachename);
	}
	
	public void save(){
		save(getModel(IndexCategory.class));
	}
	
	public void delete(){
		delete(cachename);
	}
	
	/*public List<IndexCagetory> getIndexCagetoryList(){
		List<IndexCagetory> l = new ArrayList<IndexCagetory>();
		IndexCagetory indexCagetory = new IndexCagetory();
		indexCagetory.setId(UUID.randomUUID().toString());
		indexCagetory.setIndexInfoType("数据库");
		indexCagetory.setIndexPath("test/java");
		indexCagetory.setIndexPathType("0");
		l.add(indexCagetory);
		return l;
	}*/
}
