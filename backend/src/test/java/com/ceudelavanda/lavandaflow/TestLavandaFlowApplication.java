package com.ceudelavanda.lavandaflow;

import org.springframework.boot.SpringApplication;

public class TestLavandaFlowApplication {

	public static void main(String[] args) {
		SpringApplication.from(LavandaFlowApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
