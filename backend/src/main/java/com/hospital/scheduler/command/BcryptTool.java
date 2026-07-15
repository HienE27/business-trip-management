package com.hospital.scheduler.command;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptTool {
    public static void main(String[] args) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        if (args.length > 0) {
            for (String raw : args) {
                String hashed = enc.encode(raw);
                System.out.println(raw + " -> " + hashed);
                System.out.println("verify: " + enc.matches(raw, hashed));
            }
        } else {
            System.out.println(enc.encode("admin123"));
        }
    }
}