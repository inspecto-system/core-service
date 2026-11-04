package dev.inspecto.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of core-service. */
@SpringBootApplication
public class CoreServiceApplication {

  /**
   * Starts the application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(CoreServiceApplication.class, args);
  }
}
