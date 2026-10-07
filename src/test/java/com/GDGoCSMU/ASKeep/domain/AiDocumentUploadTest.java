package com.GDGoCSMU.ASKeep.domain;

import com.GDGoCSMU.ASKeep.domain.material.client.AiClientServer;
import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AiDocumentUploadTest {
    @Test
    void sendsPdfAndIdsAsMultipartAndPropagatesFailures(@TempDir Path directory) throws Exception {
        Path pdf = directory.resolve("lecture.pdf");
        byte[] bytes = "%PDF-1.7\n\u0000\u00ffPDF payload".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(pdf, bytes);
        var contentType = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var fails = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/documents/process", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = "{\"materialId\":7,\"status\":\"success\",\"chunkCount\":2}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(fails.get() ? 500 : 200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
            exchange.close();
        });
        server.start();
        try {
            var client = new AiClientServer(RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build());
            var request = new AiDocumentRequest(7L, 3L, pdf.toString());
            var response = client.processDocument(request);
            assertEquals("success", response.status());
            assertEquals(2, response.chunkCount());
            assertTrue(contentType.get().startsWith("multipart/form-data;boundary="));
            assertTrue(body.get().contains("name=\"materialId\""));
            assertTrue(body.get().contains("\r\n\r\n7\r\n"));
            assertTrue(body.get().contains("name=\"sessionId\""));
            assertTrue(body.get().contains("\r\n\r\n3\r\n"));
            assertTrue(body.get().contains("name=\"file\"; filename=\"lecture.pdf\""));
            assertTrue(body.get().contains(new String(bytes, StandardCharsets.ISO_8859_1)));
            assertFalse(body.get().contains("filePath"));
            assertFalse(body.get().contains(directory.toString()));
            // 재시도 때도 저장된 PDF를 다시 전송하고 AI 오류는 호출자에게 전달한다.
            fails.set(true);
            assertThrows(HttpServerErrorException.class, () -> client.processDocument(request));
            assertTrue(body.get().contains(new String(bytes, StandardCharsets.ISO_8859_1)));
        } finally {
            server.stop(0);
        }
    }
}
