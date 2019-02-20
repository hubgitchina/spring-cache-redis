package cn.com.ut;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cache.annotation.EnableCaching;

//@EnableDiscoveryClient
@SpringBootApplication
@EnableCaching
public class CacheRedisApplication {

	public static void main(String[] args) {

		new SpringApplicationBuilder(CacheRedisApplication.class).web(true).run(args);
	}
}
