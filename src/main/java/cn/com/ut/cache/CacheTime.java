package cn.com.ut.cache;

import lombok.Getter;

@Getter
public class CacheTime {

	public CacheTime(long preloadSecondTime, long expirationSecondTime) {
		this.preloadSecondTime = preloadSecondTime;
		this.expirationSecondTime = expirationSecondTime;
	}

	/**
	 * 缓存主动在失效前强制刷新缓存的时间 单位：秒
	 */
	private long preloadSecondTime = 0;

	/**
	 * 缓存有效时间
	 */
	private long expirationSecondTime;
}