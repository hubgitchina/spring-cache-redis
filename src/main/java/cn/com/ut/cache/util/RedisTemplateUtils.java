package cn.com.ut.cache.util;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import cn.com.ut.cache.serializer.FastJsonRedisSerializer;
import cn.com.ut.cache.serializer.StringRedisSerializer;

/**
 * 获取默认的RedisTemplate
 * 
 * @author wangpeng1
 * @since 2019年2月19日
 */
public final class RedisTemplateUtils {

	private static RedisTemplate redisTemplate;

	public static RedisTemplate getRedisTemplate(RedisConnectionFactory redisConnectionFactory) {

		if (redisTemplate == null) {
			synchronized (RedisTemplateUtils.class) {
				if (redisTemplate == null) {
					redisTemplate = new RedisTemplate();
					redisTemplate.setConnectionFactory(redisConnectionFactory);

					// JdkSerializationRedisSerializer
					// jdkSerializationRedisSerializer = new
					// JdkSerializationRedisSerializer();
					// redisTemplate.setValueSerializer(jdkSerializationRedisSerializer);
					// redisTemplate.setHashValueSerializer(jdkSerializationRedisSerializer);

					FastJsonRedisSerializer<Object> fastJsonRedisSerializer = new FastJsonRedisSerializer<>(
							Object.class);
					redisTemplate.setValueSerializer(fastJsonRedisSerializer);
					redisTemplate.setHashValueSerializer(fastJsonRedisSerializer);

					// 设置键（key）的序列化采用StringRedisSerializer。
					redisTemplate.setKeySerializer(new StringRedisSerializer());
					redisTemplate.setHashKeySerializer(new StringRedisSerializer());
					redisTemplate.afterPropertiesSet();
				}
			}

		}
		return redisTemplate;
	}
}