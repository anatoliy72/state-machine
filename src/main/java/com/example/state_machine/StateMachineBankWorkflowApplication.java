package com.example.state_machine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.statemachine.config.EnableStateMachine;

@SpringBootApplication
@EnableStateMachine
public class StateMachineBankWorkflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(StateMachineBankWorkflowApplication.class, args);
	}

}
