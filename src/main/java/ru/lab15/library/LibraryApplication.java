package ru.lab15.library;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "Library API",
        version = "1.0.0",
        description = "Учебный микросервис (лабораторная 15, 8k33-27-chernov): авторы и книги, PostgreSQL. "
                + "Ошибки возвращаются в формате RFC 7807 (application/problem+json)."))
public class LibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }
}
