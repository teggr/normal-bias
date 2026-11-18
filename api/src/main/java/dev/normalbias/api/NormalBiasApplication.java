package dev.normalbias.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NormalBiasApplication {
    public static void main(String[] args) {
        if (args.length > 0 && !args[0].startsWith("--")) throw new IllegalArgumentException("Use the HTTP inspection API; no command subcommands are supported");
        SpringApplication.run(NormalBiasApplication.class, args);
    }
}
