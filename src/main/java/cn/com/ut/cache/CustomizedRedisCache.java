package cn.com.ut.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheElement;
import org.springframework.data.redis.cache.RedisCacheKey;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;

import cn.com.ut.cache.util.SpringContextUtils;
import cn.com.ut.cache.util.ThreadTaskUtils;
import cn.com.ut.redis.RedisLock;
import lombok.extern.slf4j.Slf4j;

/**
 * 自定义的redis缓存
 * 
 * @author wangpeng1
 * @since 2019年2月18日
 */
@Slf4j
public class CustomizedRedisCache extends RedisCache {

	private CacheSupport getCacheSupport() {

		return SpringContextUtils.getBean(CacheSupport.class);
	}

	private final RedisOperations redisOperations;

	private final byte[] prefix;

	/**
	 * 缓存主动在失效前强制刷新缓存的时间 单位：秒
	 */
	private long preloadSecondTime = 0;

	/**
	 * 缓存有效时间
	 */
	private long expirationSecondTime;

	public CustomizedRedisCache(String name, byte[] prefix,
			RedisOperations<? extends Object, ? extends Object> redisOperations, long expiration,
			long preloadSecondTime) {
		super(name, prefix, redisOperations, expiration);
		this.redisOperations = redisOperations;
		// 指定有效时间
		this.expirationSecondTime = expiration;
		// 指定自动刷新时间
		this.preloadSecondTime = preloadSecondTime;
		this.prefix = prefix;
	}

	public CustomizedRedisCache(String name, byte[] prefix,
			RedisOperations<? extends Object, ? extends Object> redisOperations, long expiration,
			long preloadSecondTime, boolean allowNullValues) {
		super(name, prefix, redisOperations, expiration, allowNullValues);
		this.redisOperations = redisOperations;
		// 指定有效时间
		this.expirationSecondTime = expiration;
		// 指定自动刷新时间
		this.preloadSecondTime = preloadSecondTime;
		this.prefix = prefix;
	}

	/**
	 * 重写get方法，获取到缓存后再次取缓存剩余的时间，如果时间小余我们配置的刷新时间就手动刷新缓存。
	 * 为了不影响get的性能，启用后台线程去完成缓存的刷。 并且只放一个线程去刷新数据。
	 *
	 * @param key
	 * @return
	 */
	@Override
	public ValueWrapper get(final Object key) {

		RedisCacheKey cacheKey = getRedisCacheKey(key);
		String cacheKeyStr = getCacheKey(key);
		// 调用重写后的get方法
		ValueWrapper valueWrapper = this.get(cacheKey);

		if (null != valueWrapper) {
			// 刷新缓存数据
			refreshCache(key, cacheKeyStr);
		}
		return valueWrapper;
	}

	/**
	 * 重写父类的get函数。
	 * 父类的get方法，是先使用exists判断key是否存在，不存在返回null，存在再到redis缓存中去取值。这样会导致并发问题，
	 * 假如有一个请求调用了exists函数判断key存在，但是在下一时刻这个缓存过期了，或者被删掉了。
	 * 这时候再去缓存中获取值的时候返回的就是null了。 可以先获取缓存的值，再去判断key是否存在。
	 *
	 * @param cacheKey
	 * @return
	 */
	@Override
	public RedisCacheElement get(final RedisCacheKey cacheKey) {

		Assert.notNull(cacheKey, "CacheKey must not be null!");

		// 根据key获取缓存值
		RedisCacheElement redisCacheElement = new RedisCacheElement(cacheKey,
				fromStoreValue(lookup(cacheKey)));
		// 判断key是否存在
		Boolean exists = (Boolean) redisOperations.execute(new RedisCallback<Boolean>() {

			@Override
			public Boolean doInRedis(RedisConnection connection) throws DataAccessException {

				return connection.exists(cacheKey.getKeyBytes());
			}
		});

		if (!exists.booleanValue()) {
			return null;
		}

		return redisCacheElement;
	}

	@Override
	public <T> T get(final Object key, final Callable<T> valueLoader) {

		ValueWrapper val = get(key);
		Object value = null;
		if (val == null) {
			// 加一个分布式锁，只放一个请求去刷新缓存
			RedisLock redisLock = new RedisLock((RedisTemplate) redisOperations,
					this.getCacheKey(key));
			try {
				if (redisLock.lock()) {
					val = get(key);
					if (val != null) {
						return (T) val.get();
					}

					value = valueLoader.call();
					redisOperations.opsForValue().set(this.getCacheKey(key), value,
							this.getExpirationSecondTime(), TimeUnit.SECONDS);
				} else {
					Thread.sleep(100);// 小憩一会儿
					return get(key, valueLoader);
				}
			} catch (Exception e) {
				log.info(e.getMessage(), e);
			} finally {
				redisLock.unlock();
			}
		} else {
			// 完善在高并发时，前面获取锁的请求还未完成设置缓存时，请求缓存结果为null的情形,递归调用重新取值
			if (val.get() == null) {
				return get(key, valueLoader);
			}

			return (T) val.get();
		}

		return (T) value;
	}

	/**
	 * 刷新缓存数据
	 */
	private void refreshCache(Object key, String cacheKeyStr) {

		Long ttl = this.redisOperations.getExpire(cacheKeyStr);

		log.info("ttl:{}, key:{}, cacheKeyStr:{}, preloadSecondTime:{}", ttl, key, cacheKeyStr,
				preloadSecondTime);

		if (null != ttl && ttl <= CustomizedRedisCache.this.preloadSecondTime) {
			// 尽量少的去开启线程，因为线程池是有限的
			ThreadTaskUtils.run(new Runnable() {
				@Override
				public void run() {

					// 加一个分布式锁，只放一个请求去刷新缓存
					RedisLock redisLock = new RedisLock((RedisTemplate) redisOperations,
							cacheKeyStr + "_lock");
					try {
						if (redisLock.lock()) {
							// 获取锁之后再判断一下过期时间，看是否需要加载数据
							Long ttl = CustomizedRedisCache.this.redisOperations
									.getExpire(cacheKeyStr);
							if (null != ttl && ttl <= CustomizedRedisCache.this.preloadSecondTime) {
								// 通过获取代理方法信息重新加载缓存数据
								CustomizedRedisCache.this.getCacheSupport().refreshCacheByKey(
										CustomizedRedisCache.super.getName(), cacheKeyStr);
							}
						}
					} catch (Exception e) {
						log.info(e.getMessage(), e);
					} finally {
						redisLock.unlock();
					}
				}
			});
		}
	}

	public long getExpirationSecondTime() {

		return expirationSecondTime;
	}

	/**
	 * 获取RedisCacheKey
	 *
	 * @param key
	 * @return
	 */
	public RedisCacheKey getRedisCacheKey(Object key) {

		return new RedisCacheKey(key).usePrefix(this.prefix)
				.withKeySerializer(redisOperations.getKeySerializer());
	}

	/**
	 * 获取RedisCacheKey
	 *
	 * @param key
	 * @return
	 */
	public String getCacheKey(Object key) {

		return new String(getRedisCacheKey(key).getKeyBytes());
	}
}
