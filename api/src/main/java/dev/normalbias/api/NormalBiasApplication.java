package dev.normalbias.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class NormalBiasApplication {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("admin")) { AuthAdmin.command(args); return; }
        if (args.length > 0 && !args[0].startsWith("--")) throw new IllegalArgumentException("Only local admin commands are supported; use the HTTP API for inspections");
        SpringApplication.run(NormalBiasApplication.class, args);
    }
}
