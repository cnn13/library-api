package ru.lab15.library.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.lab15.library.service.LibraryService;

/** Проверка обработки ошибок контроллером без БД (сервис подменён моком). */
@WebMvcTest(BookController.class)
@Import({ErrorHandler.class, RequestLoggingFilter.class})
class BookControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LibraryService service;

    @Test
    void malformedJsonGives400() throws Exception {
        mvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Malformed JSON request body"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void invalidFieldsGive422WithDetails() throws Exception {
        mvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"authorId\":-1,\"year\":1000,\"isbn\":\"abc\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.authorId").exists())
                .andExpect(jsonPath("$.errors.year").exists())
                .andExpect(jsonPath("$.errors.isbn").value("must be ISBN-10 or ISBN-13"));
    }

    @Test
    void unknownFieldGives400() throws Exception {
        mvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"authorId\":1,\"year\":2000,\"color\":\"red\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field 'color'"));
    }

    @Test
    void wrongTypeGives400() throws Exception {
        mvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"authorId\":1,\"year\":\"next year\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Field 'year' has invalid value 'next year'"));
    }

    @Test
    void nonNumericIdGives400() throws Exception {
        mvc.perform(get("/api/v1/books/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void missingBookGives404() throws Exception {
        when(service.getBook(eq(999L))).thenThrow(ApiException.notFound("Book", 999L));
        mvc.perform(get("/api/v1/books/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Book with id=999 not found"));
    }

    @Test
    void tooLargePageSizeGives400() throws Exception {
        mvc.perform(get("/api/v1/books").param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
    }

    @Test
    void emptyPatchGives400() throws Exception {
        mvc.perform(patch("/api/v1/books/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongContentTypeGives415() throws Exception {
        mvc.perform(post("/api/v1/books").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unexpectedErrorGives500WithoutStacktrace() throws Exception {
        when(service.listBooks(any(), any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("boom"));
        mvc.perform(get("/api/v1/books"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected server error"));
    }
}
