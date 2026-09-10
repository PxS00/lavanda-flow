package com.ceudelavanda.lavandaflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LavandaFlowApplication {

	public static void main(String[] args) {
		var context = SpringApplication.run(LavandaFlowApplication.class, args);
		if (context.getEnvironment().getProperty("lavanda.inventory.initial-import.enabled", Boolean.class, false)) {
			System.exit(SpringApplication.exit(context));
		}
	}

}
