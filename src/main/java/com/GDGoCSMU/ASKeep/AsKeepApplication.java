package com.GDGoCSMU.ASKeep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class AsKeepApplication {

	public static void main(String[] args) {
		SpringApplication.run(AsKeepApplication.class, args);
	}

}
