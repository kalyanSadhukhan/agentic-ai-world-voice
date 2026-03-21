package com.voiceai.contact;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ContactApplication {

    public static void main(String[] args) {
        // Explicitly load .env and inject explicitly to system properties so Spring resolves ${} placeholders
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            dotenv.entries().forEach(entry -> {
                String val = entry.getValue();
                if (entry.getKey().equals("EMAIL_PASS") && val != null) {
                    val = val.replace(" ", "");
                }
                System.setProperty(entry.getKey(), val);
            });
        } catch (Exception e) {
            System.err.println("Could not load .env file manually, continuing with system env vars.");
        }
        
        SpringApplication.run(ContactApplication.class, args);
    }

}
