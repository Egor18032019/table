package org.tablebuilder.demo.controllers;

import org.tablebuilder.demo.model.AiSearchRequest;
import org.tablebuilder.demo.model.AiSearchResponse;
import org.tablebuilder.demo.service.SimpleAiAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assistant Controller", description = "ИИ-ассистент для поиска данных на естественном языке")
public class AiAssistantController {

    private final SimpleAiAssistantService aiAssistantService;

    @Operation(summary = "Поиск информации по естественно-языковому запросу")
    @PostMapping("/search")
    public ResponseEntity<AiSearchResponse> searchData(@RequestBody AiSearchRequest request) {
        try {
            AiSearchResponse response = aiAssistantService.processNaturalLanguageQuery(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new AiSearchResponse(false, "Ошибка обработки запроса", null, null, e.getMessage())
            );
        }
    }

    @Operation(summary = "Быстрый поиск через GET запрос")
    @GetMapping("/search")
    public ResponseEntity<AiSearchResponse> quickSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "anonymous") String username) {

        AiSearchRequest request = new AiSearchRequest();
        request.setQuery(q);
        request.setUsername(username);

        return searchData(request);
    }
}