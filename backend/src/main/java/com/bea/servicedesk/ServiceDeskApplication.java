package com.bea.servicedesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point for the Enterprise Service Desk REST API. */
@SpringBootApplication
public class ServiceDeskApplication {

  /**
   * Starts the embedded web server and application context.
   *
   * @param args command-line arguments passed to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(ServiceDeskApplication.class, args);
  }
}
