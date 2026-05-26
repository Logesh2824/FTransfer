package org.example;



import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// This annotation tells Spring to scan this folder and autoconfigure the web server
@SpringBootApplication
public class FTApp {
    public static void main(String[] args) {
        SpringApplication.run(FTApp.class, args);
        System.out.println("\n🚀 AegisNode Local API is running on http://localhost:8081");
    }
}
