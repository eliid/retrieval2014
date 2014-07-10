package frame.retrieval.engine.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldCollector;

import frame.base.core.util.StringClass;
import frame.retrieval.engine.RetrievalType;
import frame.retrieval.engine.analyzer.IRAnalyzerFactory;
import frame.retrieval.engine.common.RetrievalUtil;
import frame.retrieval.engine.pool.proxy.IndexReaderProxy;
import frame.retrieval.engine.query.formatter.IHighlighterFactory;
import frame.retrieval.engine.query.item.QueryItem;
import frame.retrieval.engine.query.item.QuerySort;
import frame.retrieval.engine.query.item.QueryUtil;
import frame.retrieval.engine.query.item.QueryWrap;
import frame.retrieval.engine.query.result.DatabaseQueryResult;
import frame.retrieval.engine.query.result.FileQueryResult;
import frame.retrieval.engine.query.result.QueryResult;
import frame.retrieval.engine.query.score.ScoreQuery;

/**
 * 索引查询对象
 * @author 
 *
 */
public class RQuery {
	private Log log=RetrievalUtil.getLog(this.getClass());

	private IRAnalyzerFactory analyzerFactory = null;
	private IHighlighterFactory highlighterFactory=null;
	private int queryResultTopDocsNum=500;

	private Searcher searcher = null;
	
	private IndexReaderProxy[] indexReaderProxys=null;

	/**
	 * 创建全文搜索对象
	 * @param analyzerFactory
	 *            分词器工厂
	 *            
	 * @param highlighterFactory
	 *            内容高亮处理器工厂
	 *            
	 * @param queryResultTopDocsNum
	 *            返回的最大结果集数量
	 *            
	 * @param baseIndexPath
	 *            索引文件绝对路径根目录
	 *            
	 * @param indexReaderProxy
	 *            
	 */
	public RQuery(IRAnalyzerFactory analyzerFactory,
			IHighlighterFactory highlighterFactory,
			int queryResultTopDocsNum,
			String baseIndexPath,
			IndexReaderProxy indexReaderProxy) {
		this.analyzerFactory=analyzerFactory;
		this.highlighterFactory=highlighterFactory;
		this.queryResultTopDocsNum=queryResultTopDocsNum;
		indexReaderProxys=new IndexReaderProxy[]{indexReaderProxy};
		try {
			searcher = new IndexSearcher(indexReaderProxy.getIndexReader());
			searcher.setSimilarity(analyzerFactory.createSimilarity());
		} catch (Exception e) {
			throw new RetrievalQueryException(e);
		}
	}

	/**
	 * 创建全文搜索对象
	 * @param analyzerFactory
	 *            分词器工厂
	 *            
	 * @param highlighterFactory
	 *            内容高亮处理器工厂
	 *            
	 * @param queryResultTopDocsNum
	 *            返回的最大结果集数量
	 *            
	 * @param baseIndexPath
	 *            索引文件绝对路径根目录
	 *            
	 * @param indexReaders
	 *            
	 */
	public RQuery(IRAnalyzerFactory analyzerFactory,
			IHighlighterFactory highlighterFactory,
			int queryResultTopDocsNum,
			String baseIndexPath,
			IndexReaderProxy[] indexReaderProxys) {
		this.analyzerFactory=analyzerFactory;
		this.highlighterFactory=highlighterFactory;
		this.queryResultTopDocsNum=queryResultTopDocsNum;
		this.indexReaderProxys=indexReaderProxys;
		
		int length = indexReaderProxys.length;
		if (length > 1) {
			IndexSearcher[] searchers = new IndexSearcher[length];
			for (int i = 0; i < length; i++) {
				IndexSearcher searcher = null;
				try {
					searcher = new IndexSearcher(indexReaderProxys[i].getIndexReader());
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
				searchers[i] = searcher;
			}
			try {
				searcher = new MultiSearcher(searchers);
			} catch (Exception e) {
				throw new RetrievalQueryException(e);
			}
		} else {
			try {
				searcher = new IndexSearcher(indexReaderProxys[0].getIndexReader());
			} catch (Exception e) {
				throw new RetrievalQueryException(e);
			}
		}
		searcher.setSimilarity(analyzerFactory.createSimilarity());
	}

	/**
	 * 根据唯一索引ID查询结果
	 * 
	 * @param indexId
	 * @return
	 */
	public QueryResult getQueryResultByIndexId(String indexId) {
		QueryResult queryResult = null;

		Query query = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IID), indexId));

		ScoreDoc[] hits = getHits(query);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}

			try {
				Document doc = searcher.doc(hits[0].doc);
				queryResult = document2QueryResult(doc);
				queryResult.setHitId(hits[0].doc);
				queryResult.setAllQueryResultCount(1);
				queryResult.setQuery(query);
			} catch (Exception e) {
				throw new RetrievalQueryException(e);
			}
		}

		return queryResult;
	}

	/**
	 * 根据数据库表名和数据库记录ID获取唯一查询结果
	 * 
	 * @param tableName
	 * @param recordId
	 * @return
	 */
	public String getDatabaseIndexId(String tableName, String recordId) {
		String indexId = "";

		Query tableNameQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDatabaseDocItemType._DT), tableName
				.toUpperCase()));
		
		Query recordIdQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDatabaseDocItemType._DID), recordId));
		
		Query sourceIndexTypeQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IST), StringClass
				.getString(RetrievalType.RIndexSourceType.D)));

		BooleanQuery query = new BooleanQuery();
		query.add(tableNameQuery, BooleanClause.Occur.MUST);
		query.add(recordIdQuery, BooleanClause.Occur.MUST);
		query.add(sourceIndexTypeQuery, BooleanClause.Occur.MUST);

		ScoreDoc[] hits = getHits(query);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}
			try {
				Document doc = searcher.doc(hits[0].doc);
				indexId = StringClass.getString(doc.get(StringClass
						.getString(RetrievalType.RDocItemSpecialName._IID)));
			} catch (Exception e) {
				throw new RetrievalQueryException(e);
			}
		}

		return indexId;
	}

	/**
	 * 根据数据库表名和数据库记录ID获取唯一查询结果
	 * 
	 * @param tableName
	 * @param recordId
	 * @return
	 */
	public DatabaseQueryResult getDatabaseQueryResult(String tableName,
			String recordId) {
		DatabaseQueryResult queryResult = null;

		Query tableNameQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDatabaseDocItemType._DT), tableName
				.toUpperCase()));
		
		Query recordIdQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDatabaseDocItemType._DID), recordId));
		
		Query sourceIndexTypeQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IST), StringClass
				.getString(RetrievalType.RIndexSourceType.D)));

		BooleanQuery query = new BooleanQuery();
		query.add(tableNameQuery, BooleanClause.Occur.MUST);
		query.add(recordIdQuery, BooleanClause.Occur.MUST);
		query.add(sourceIndexTypeQuery, BooleanClause.Occur.MUST);

		ScoreDoc[] hits = getHits(query);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}
			try {
				Document doc = searcher.doc(hits[0].doc);
				queryResult = (DatabaseQueryResult) document2QueryResult(doc);
				queryResult.setHitId(hits[0].doc);
				queryResult.setAllQueryResultCount(1);
				queryResult.setQuery(query);
			} catch (Exception e) {
				throw new RetrievalQueryException(e);
			}
		}

		return queryResult;
	}

	/**
	 * 根据数据库表名和数据库记录ID获取查询结果
	 * 
	 * @param tableName
	 * @param recordId
	 * @return
	 */
	public DatabaseQueryResult[] getDatabaseQueryResultArray(String tableName,
			String recordId) {
		DatabaseQueryResult[] queryResults = null;

		Query tableNameQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDatabaseDocItemType._DT), tableName
				.toUpperCase()));
		
		Query recordIdQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDatabaseDocItemType._DID), recordId));
		
		Query sourceIndexTypeQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IST), StringClass
				.getString(RetrievalType.RIndexSourceType.D)));

		BooleanQuery query = new BooleanQuery();
		query.add(tableNameQuery, BooleanClause.Occur.MUST);
		query.add(recordIdQuery, BooleanClause.Occur.MUST);
		query.add(sourceIndexTypeQuery, BooleanClause.Occur.MUST);

		ScoreDoc[] hits = getHits(query);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}

			queryResults = new DatabaseQueryResult[length];

			for (int i = 0; i < length; i++) {
				try {
					Document doc = searcher.doc(hits[i].doc);
					queryResults[i] = (DatabaseQueryResult) document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(query);
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据文件ID获取文件索引
	 * 
	 * @param fileId
	 * @return
	 */
	public String getFileIndexIdByFileId(String fileId) {
		String indexId = "";

		Query fileIdQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RFileDocItemType._FID), fileId));
		
		Query sourceIndexTypeQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IST), StringClass
				.getString(RetrievalType.RIndexSourceType.F)));

		BooleanQuery query = new BooleanQuery();

		query.add(fileIdQuery, BooleanClause.Occur.MUST);
		query.add(sourceIndexTypeQuery, BooleanClause.Occur.MUST);

		ScoreDoc[] hits = getHits(query);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}

			try {
				Document doc = searcher.doc(hits[0].doc);
				indexId = StringClass.getString(doc.get(StringClass
						.getString(RetrievalType.RDocItemSpecialName._IID)));
			} catch (Exception e) {
				throw new RetrievalQueryException(e);
			}
		}

		return indexId;
	}

	/**
	 * 根据文件相对路径名称获取文件索引
	 * 
	 * @param fileRelativePath
	 * @return
	 */
	public String getFileIndexIdByFileRelativePath(String fileRelativePath) {
		String indexId = "";

		Query fileRelativePathQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RFileDocItemType._FP),
				fileRelativePath));
		
		Query sourceIndexTypeQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IST), StringClass
				.getString(RetrievalType.RIndexSourceType.F)));

		BooleanQuery query = new BooleanQuery();

		query.add(fileRelativePathQuery, BooleanClause.Occur.MUST);
		query.add(sourceIndexTypeQuery, BooleanClause.Occur.MUST);

		ScoreDoc[] hits = getHits(query);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}

			try {
				Document doc = searcher.doc(hits[0].doc);
				indexId = StringClass.getString(doc.get(StringClass
						.getString(RetrievalType.RDocItemSpecialName._IID)));
			} catch (Exception e) {
				throw new RetrievalQueryException(e);
			}
		}

		return indexId;
	}

	/**
	 * 根据文件ID获取文件索引
	 * 
	 * @param fileId
	 * @return
	 */
	public FileQueryResult getFileQueryResultByFileId(String fileId) {
		FileQueryResult queryResult = null;

		Query fileIdQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RFileDocItemType._FID), fileId));
		
		Query sourceIndexTypeQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IST), StringClass
				.getString(RetrievalType.RIndexSourceType.F)));

		BooleanQuery query = new BooleanQuery();

		query.add(fileIdQuery, BooleanClause.Occur.MUST);
		query.add(sourceIndexTypeQuery, BooleanClause.Occur.MUST);

		ScoreDoc[] hits = getHits(query);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}

			try {
				Document doc = searcher.doc(hits[0].doc);
				queryResult = (FileQueryResult) document2QueryResult(doc);
				queryResult.setHitId(hits[0].doc);
				queryResult.setAllQueryResultCount(1);
				queryResult.setQuery(query);
			} catch (Exception e) {
				throw new RetrievalQueryException(e);
			}
		}

		return queryResult;
	}

	/**
	 * 根据文件相对路径名称获取文件索引
	 * 
	 * @param fileRelativePath
	 * @return
	 */
	public FileQueryResult getFileQueryResultByFileRelativePath(String fileRelativePath) {
		FileQueryResult queryResult = null;

		Query fileRelativePathQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RFileDocItemType._FP),
				fileRelativePath));
		
		Query sourceIndexTypeQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IST), StringClass
				.getString(RetrievalType.RIndexSourceType.F)));

		BooleanQuery query = new BooleanQuery();

		query.add(fileRelativePathQuery, BooleanClause.Occur.MUST);
		query.add(sourceIndexTypeQuery, BooleanClause.Occur.MUST);

		ScoreDoc[] hits = getHits(query);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}

			try {
				Document doc = searcher.doc(hits[0].doc);
				queryResult = (FileQueryResult) document2QueryResult(doc);
				queryResult.setHitId(hits[0].doc);
				queryResult.setAllQueryResultCount(1);
				queryResult.setQuery(query);
			} catch (Exception e) {
				throw new RetrievalQueryException(e);
			}
		}

		return queryResult;
	}

	/**
	 * 根据相关数据库表名和数据库记录ID，获取相关文件索引内容
	 * 
	 * @param tableName
	 * @param recordId
	 * @return
	 */
	public FileQueryResult[] getFileQueryResultArray(String tableName,String recordId) {
		FileQueryResult[] queryResults = null;

		Query tableNameQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDatabaseDocItemType._DT), tableName
				.toUpperCase()));
		
		Query recordIdQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDatabaseDocItemType._DID), recordId));
		
		Query sourceIndexTypeQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IST), StringClass
				.getString(RetrievalType.RIndexSourceType.F)));

		BooleanQuery query = new BooleanQuery();

		query.add(tableNameQuery, BooleanClause.Occur.MUST);
		query.add(recordIdQuery, BooleanClause.Occur.MUST);
		query.add(sourceIndexTypeQuery, BooleanClause.Occur.MUST);

		ScoreDoc[] hits = getHits(query);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}
			queryResults = new FileQueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = searcher.doc(hits[i].doc);
					queryResults[i] = (FileQueryResult) document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(query);
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据相关数据库表名和数据库记录ID，获取相关文件索引内容
	 * 
	 * @param tableName
	 * @param recordId
	 * @return
	 */
	public int getFileQueryResultArrayCount(String tableName, String recordId) {

		Query tableNameQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDatabaseDocItemType._DT), tableName
				.toUpperCase()));
		
		Query recordIdQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDatabaseDocItemType._DID), recordId));
		
		Query sourceIndexTypeQuery = new TermQuery(new Term(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IST), StringClass
				.getString(RetrievalType.RIndexSourceType.F)));

		BooleanQuery query = new BooleanQuery();

		query.add(tableNameQuery, BooleanClause.Occur.MUST);
		query.add(recordIdQuery, BooleanClause.Occur.MUST);
		query.add(sourceIndexTypeQuery, BooleanClause.Occur.MUST);

		ScoreDoc[] hits = getHits(query);

		int length = 0;

		if (hits != null) {
			length = hits.length;
		}
		return length;
	}

	/**
	 * 根据Query获取查询结果
	 * 
	 * @param query
	 * @return
	 */
	public QueryResult[] getQueryResults(Query query) {
		QueryResult[] queryResults = null;

		QueryWrap queryWrap = new QueryWrap(query);

		//ScoreDoc[] hits = getHits(query);//remove by sxjun 2014-7-10
		ScoreDoc[] hits = getHits(queryWrap.getQuery(),null,null,null);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = searcher.doc(hits[i].doc);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(queryWrap.getQuery());
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据Query获取查询结果
	 * 
	 * @param query
	 * @return
	 */
	public int getQueryResultsCount(Query query) {

		//ScoreDoc[] hits = getHits(query);//remove by sxjun 2014-7-10
		ScoreDoc[] hits = getHits(query,null,null,null);

		int length = 0;

		if (hits != null) {
			length = hits.length;
		}

		return length;
	}

	/**
	 * 根据Query获取查询并返回排序后的结果
	 * 
	 * @param query
	 * @param querySort
	 * @return
	 */
	public QueryResult[] getQueryResults(Query query, QuerySort querySort) {
		QueryResult[] queryResults = null;

		QueryWrap queryWrap = new QueryWrap(query);

		//ScoreDoc[] hits = getHits(query, querySort.getSort());//remove by sxjun 2014-7-10
		ScoreDoc[] hits = getHits(query,querySort.getSort(),null,null);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = searcher.doc(hits[i].doc);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(queryWrap.getQuery());
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据Query获取分页查询结果
	 * 
	 * @param query
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public QueryResult[] getQueryResults(Query query, int startIndex,int endIndex) {
		QueryResult[] queryResults = null;

		QueryWrap queryWrap = new QueryWrap(query);

		//ScoreDoc[] hits = getHits(query);//remove by sxjun 2014-7-10
		ScoreDoc[] hits = getHits(query,null,null,null);

		List<Document> documents = getPageDocuments(hits, query, startIndex,
				endIndex);

		if (documents != null) {
			int length = documents.size();
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = documents.get(i);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(queryWrap.getQuery());
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据Query获取分页查询并返回排序后的结果
	 * 
	 * @param query
	 * @param querySort
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public QueryResult[] getQueryResults(Query query, 
			QuerySort querySort,
			int startIndex, 
			int endIndex) {
		QueryResult[] queryResults = null;

		QueryWrap queryWrap = new QueryWrap(query);

		//ScoreDoc[] hits = getHits(query, querySort.getSort());//remove by sxjun 2014-7-10
		ScoreDoc[] hits = getHits(query,null,null,null);

		List<Document> documents = getPageDocuments(hits, query, startIndex,
				endIndex);

		if (documents != null) {
			int length = documents.size();
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = documents.get(i);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(queryWrap.getQuery());
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据输入的内容获取FullContent查询结果
	 * 
	 * @param keyWord
	 * @return
	 */
	public QueryResult[] getFullContentQueryResults(String keyWord) {
		QueryResult[] queryResults = null;

		Query query = QueryUtil.createQuery(analyzerFactory,StringClass
				.getString(RetrievalType.RDocItemSpecialName._IAC),
				keyWord);

		ScoreDoc[] hits = getHits(query);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = searcher.doc(hits[i].doc);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(query);
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据输入的内容获取FullContent查询结果
	 * 
	 * @param keyWord
	 * @return
	 */
	public int getFullContentQueryResultsCount(String keyWord) {


		Query query = QueryUtil.createQuery(analyzerFactory,StringClass
				.getString(RetrievalType.RDocItemSpecialName._IAC),
				keyWord);

		ScoreDoc[] hits = getHits(query);

		int length = 0;

		if (hits != null) {
			length = hits.length;
		}

		return length;
	}

	/**
	 * 根据输入的内容获取FullContent查询并返回排序后的结果
	 * 
	 * @param keyWord
	 * @param querySort
	 * @return
	 */
	public QueryResult[] getFullContentQueryResults(String keyWord,QuerySort querySort) {
		QueryResult[] queryResults = null;

		Query query = QueryUtil.createQuery(analyzerFactory,StringClass
				.getString(RetrievalType.RDocItemSpecialName._IAC),
				keyWord);

		ScoreDoc[] hits = getHits(query, querySort.getSort());

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = searcher.doc(hits[i].doc);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(query);
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据输入的内容分页获取FullContent查询结果
	 * 
	 * @param keyWord
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public QueryResult[] getFullContentQueryResults(String keyWord, int startIndex,int endIndex) {
		QueryResult[] queryResults = null;

		Query query = QueryUtil.createQuery(analyzerFactory,StringClass
				.getString(RetrievalType.RDocItemSpecialName._IAC),
				keyWord);

		ScoreDoc[] hits = getHits(query);

		List<Document> documents = getPageDocuments(hits, query, startIndex,
				endIndex);

		if (documents != null) {
			int length = documents.size();
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = documents.get(i);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(query);
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据输入的内容分页获取FullContent查询并返回排序后的结果
	 * 
	 * @param keyWord
	 * @param querySort
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public QueryResult[] getFullContentQueryResults(String keyWord,
			QuerySort querySort, 
			int startIndex, 
			int endIndex) {
		QueryResult[] queryResults = null;

		Query query = QueryUtil.createQuery(analyzerFactory,StringClass
				.getString(RetrievalType.RDocItemSpecialName._IAC),
				keyWord);

		ScoreDoc[] hits = getHits(query, querySort.getSort());

		List<Document> documents = getPageDocuments(hits, query, startIndex,
				endIndex);

		if (documents != null) {
			int length = documents.size();
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = documents.get(i);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(query);
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据QueryItem获取查询结果
	 * 
	 * @param queryItem
	 * @return
	 */
	public QueryResult[] getQueryResults(QueryItem queryItem) {
		QueryResult[] queryResults = null;

		QueryWrap queryWrap = queryItem.getQueryWrap();
		//ScoreDoc[] hits = getHits(queryWrap.getQuery());//remove by sxjun 2014-7-10
		ScoreDoc[] hits = getHits(queryWrap.getQuery(),null,null,null);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = searcher.doc(hits[i].doc);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(queryWrap.getQuery());
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 根据QueryItem获取查询结果
	 * 
	 * @param queryItem
	 * @return
	 */
	public int getQueryResultsCount(QueryItem queryItem) {
		return  getQueryResultsCount(queryItem,false);
	}
	public int getQueryResultsCount(QueryItem queryItem,boolean flag) {
		QueryWrap queryWrap = queryItem.getQueryWrap();
		if(flag){
			return getHitsCount(queryWrap.getQuery());
		}else{
			ScoreDoc[] hits = getHits(queryWrap.getQuery());

			int length = 0;

			if (hits != null) {
				length = hits.length;
			}

			return length;
		}
		
	}

	/**
	 * 根据QueryItem获取查询并返回排序后的结果
	 * 
	 * @param queryItem
	 * @param querySort
	 * @return
	 */
	public QueryResult[] getQueryResults(QueryItem queryItem,QuerySort querySort) {
		QueryResult[] queryResults = null;

		QueryWrap queryWrap = queryItem.getQueryWrap();
		//ScoreDoc[] hits = getHits(queryWrap.getQuery(), querySort.getSort());//remove by sxjun 2014-7-10
		ScoreDoc[] hits = getHits(queryWrap.getQuery(),querySort.getSort(),null,null);

		if (hits != null) {
			int length = hits.length;
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = searcher.doc(hits[i].doc);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(queryWrap.getQuery());
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}
	

	/**
	 * 根据QueryItem获取分页查询结果
	 * 
	 * @param queryItem
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public QueryResult[] getQueryResults(QueryItem queryItem, 
			int startIndex,
			int endIndex) {
		QueryResult[] queryResults = null;

		QueryWrap queryWrap = queryItem.getQueryWrap();
		
		//ScoreDoc[] hits = getHits(queryWrap.getQuery());//remove by sxjun 2014-7-10
		ScoreDoc[] hits = getHits(queryWrap.getQuery(),null,startIndex,endIndex);
		List<Document> documents = getPageDocuments(hits, queryWrap.getQuery(),
				startIndex, endIndex);

		if (documents != null) {
			int length = documents.size();
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = documents.get(i);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(queryWrap.getQuery());
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}
		return queryResults;
	}

	/**
	 * 根据QueryItem获取分页查询并返回排序后的结果
	 * 
	 * @param queryItem
	 * @param querySort
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public QueryResult[] getQueryResults(QueryItem queryItem,
			QuerySort querySort, 
			int startIndex, 
			int endIndex) {
		QueryResult[] queryResults = null;

		QueryWrap queryWrap = queryItem.getQueryWrap();

		//ScoreDoc[] hits = getHits(queryWrap.getQuery(), querySort.getSort());//remove by sxjun 2014-7-10
		ScoreDoc[] hits = getHits(queryWrap.getQuery(),querySort.getSort(),startIndex,endIndex);

		List<Document> documents = getPageDocuments(hits, queryWrap.getQuery(),
				startIndex, endIndex);

		if (documents != null) {
			int length = documents.size();
			if (length <= 0) {
				return null;
			}
			queryResults = new QueryResult[length];
			for (int i = 0; i < length; i++) {
				try {
					Document doc = documents.get(i);
					queryResults[i] = document2QueryResult(doc);
					queryResults[i].setHitId(hits[i].doc);
					queryResults[i].setAllQueryResultCount(length);
					queryResults[i].setQuery(queryWrap.getQuery());
				} catch (Exception e) {
					throw new RetrievalQueryException(e);
				}
			}
		}

		return queryResults;
	}

	/**
	 * 关闭索引对象
	 */
	public void close() {
		if (searcher != null) {
			try {
				searcher.close();
			} catch (Exception e) {
				throw new RetrievalQueryException(e);
			}
		}
		if(indexReaderProxys!=null){
			int length=indexReaderProxys.length;
			for(int i=0;i<length;i++){
				IndexReaderProxy indexReaderProxy=indexReaderProxys[i];
				log.debug("close : "+indexReaderProxy);
				indexReaderProxy.close();
			}
		}
	}

	/**
	 * 将Lucene Document 对象转换为 QueryResult 对象
	 * 
	 * @param doc
	 * @return
	 */
	private QueryResult document2QueryResult(Document doc) {
		String fieldNames = doc.get(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IAF));
		fieldNames = StringClass.getString(fieldNames);

		if (fieldNames.equals("")) {
			return null;
		}

		List<String> fieldList = RetrievalUtil.getAllFields(fieldNames);
		Map<String, String> map = new HashMap<String, String>();

		int length = fieldList.size();
		for (int i = 0; i < length; i++) {
			String fieldName = fieldList.get(i);
			String value = StringClass.getString(doc.get(fieldName));
			map.put(fieldName, value);
		}

		String sourceIndexType = StringClass.getString(map.get(StringClass
				.getString(RetrievalType.RDocItemSpecialName._IST)));

		QueryResult queryResult = null;

		if (sourceIndexType.equals(StringClass
				.getString(RetrievalType.RIndexSourceType.D))) {
			queryResult = new DatabaseQueryResult(highlighterFactory,map);
		} else if (sourceIndexType.equals(StringClass
				.getString(RetrievalType.RIndexSourceType.F))) {
			queryResult = new FileQueryResult(highlighterFactory,map);
		} else if (sourceIndexType.equals(StringClass
				.getString(RetrievalType.RIndexSourceType.T))) {
			queryResult = new QueryResult(highlighterFactory,map);
		}

		return queryResult;
	}
	
	/**
	 * 通过Query获取Hits
	 * @param query
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public ScoreDoc[] getHits(Query query, Sort sort,Integer startIndex,Integer endIndex){
		TopDocs hits = null;
		try {
			if(sort==null)
				sort = new Sort(new SortField[] {SortField.FIELD_SCORE});
			if(startIndex==null)
				startIndex=0;
			if(endIndex==null)
				endIndex=searcher.maxDoc();
			TopFieldCollector collector = TopFieldCollector.create(sort,searcher.maxDoc(), false, true, false, false);
			searcher.search(query, collector);
			hits = collector.topDocs(startIndex, endIndex);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (hits != null) {
			return hits.scoreDocs;
		} else {
			return null;
		}
	}

	/**
	 * 通过Query获取Hits
	 * 
	 * @param query
	 * @return
	 */
	private ScoreDoc[] getHits(Query query) {
		TopDocs hits = null;
		try {
			hits = searcher.search(new ScoreQuery(query), null,queryResultTopDocsNum);
		} catch (Exception e) {
			throw new RetrievalQueryException(e);
		}
		if (hits != null) {
			return hits.scoreDocs;
		} else {
			return null;
		}
	}
	
	private int getHitsCount(Query query) {
		TopDocs hits = null;
		try {
			hits = searcher.search(new ScoreQuery(query), null,queryResultTopDocsNum);
		} catch (Exception e) {
			throw new RetrievalQueryException(e);
		}
		if (hits != null) {
			return hits.totalHits;
		} else {
			return 0;
		}
	}

	/**
	 * 通过Query获取Hits
	 * 
	 * @param query
	 * @param sort
	 * @return
	 */
	private ScoreDoc[] getHits(Query query, Sort sort) {
		TopDocs hits = null;

		try {
			hits = searcher.search(new ScoreQuery(query), null,queryResultTopDocsNum, sort);
		} catch (IOException e) {
			throw new RetrievalQueryException(e);
		}
		if (hits != null) {
			return hits.scoreDocs;
		} else {
			return null;
		}
	}

	/**
	 * 通过Query获取分页获取Document
	 * 
	 * @param query
	 * @return
	 */
	private List<Document> getPageDocuments(ScoreDoc[] hits, 
			Query query,
			int startIndex, 
			int endIndex) {
		List<Document> docs = null;
		try {
			docs = new ArrayList<Document>();
			if (endIndex >= hits.length) {
				endIndex = hits.length - 1;
			}
			for (int i = startIndex; i <= endIndex; i++) {
				Document doc = searcher.doc(hits[i].doc);
				docs.add(doc);
			}
		} catch (Exception e) {
			throw new RetrievalQueryException(e);
		}
		return docs;
	}

	public Searcher getSearcher() {
		return searcher;
	}

	public void setSearcher(Searcher searcher) {
		this.searcher = searcher;
	}
	
}
