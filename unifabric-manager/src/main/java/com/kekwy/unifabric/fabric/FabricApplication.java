package com.kekwy.unifabric.fabric;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.kekwy.unifabric.fabric")
public class FabricApplication {

    public static void main(String[] args) {
        SpringApplication.run(FabricApplication.class, args);
    }
}
