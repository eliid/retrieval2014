package frame.retrieval.test.fileindexall;

import java.util.HashMap;
import java.util.Map;

import frame.retrieval.engine.index.all.file.IIndexAllFileInterceptor;
import frame.retrieval.engine.index.doc.file.FileIndexDocument;

public class TestFileIndexAllInterceptor implements IIndexAllFileInterceptor{

	public Map<String, Object> getFieldsType() {
		return null;
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> interceptor(FileIndexDocument fileIndexDocument) {

		Map map=new HashMap();
		
		map.put("MEM_ADDRESS", fileIndexDocument);
		
		return map;
	}

}
