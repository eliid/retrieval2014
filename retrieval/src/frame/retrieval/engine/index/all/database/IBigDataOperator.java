package frame.retrieval.engine.index.all.database;

import java.sql.Blob;
import java.sql.Clob;

/**
 * clob、blob转string。
 * @作者 sxjun
 */
public interface IBigDataOperator {

	public String getStringFromBlob(Blob blob);
	
	public byte[] getByteFromBlob(Blob blob);
	
	public String getStringFromBlob(byte[] bytes);
	
	public byte[] getByteFromBlob(byte[] bytes);

	public String getStringFromClob(Clob clob);
	
	public String getStringFromClob(char[] ch);
	
}
