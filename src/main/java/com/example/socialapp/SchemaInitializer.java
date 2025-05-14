package com.example.socialapp;

import com.datastax.oss.driver.api.core.CqlSession;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.stream.Collectors;


public class SchemaInitializer {
    public static void main(String[] args) {
        try (CqlSession session = CqlSession.builder().build()) {
            String cqlContent;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    SchemaInitializer.class.getClassLoader().getResourceAsStream("schema.cql")
            ))) {
                cqlContent = reader.lines().collect(Collectors.joining("\n"));
            }

            for (String raw : cqlContent.split(";")) {
                String stmt = raw.trim();
                if (!stmt.isEmpty()) {
                    session.execute(stmt);
                    System.out.println("Executed: " + stmt);
                }
            }

            System.out.println("Schema initialized successfully.");
        } catch (IOException e) {
            System.err.println("Error reading schema file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error executing CQL: " + e.getMessage());
        }
    }
}
