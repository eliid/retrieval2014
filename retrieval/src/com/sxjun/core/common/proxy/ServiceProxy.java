package com.sxjun.core.common.proxy;

import com.sxjun.core.common.service.CommonService;
import com.sxjun.core.common.service.impl.BerkeleyCommonService;
import com.sxjun.core.common.service.impl.RedisCommonService;
import com.sxjun.core.common.utils.Global;

public class ServiceProxy<T> {
	public static final String BERKELEY_PROXY = "0";
	public static final String REDIS_PROXY = "1";
	public CommonService<T> getproxy(){
		if(REDIS_PROXY.endsWith(Global.getDatabasetype()))
			return new RedisCommonService<T>();
		else if(BERKELEY_PROXY.endsWith(Global.getDatabasetype()))
			return new BerkeleyCommonService<T>();
		else
			return new BerkeleyCommonService<T>();
	}
	
}
