package br.com.ecowatch.read_model_service.infrastructure.api;

import br.com.ecowatch.read_model_service.application.dto.AiAskRequest;
import br.com.ecowatch.read_model_service.application.dto.AiAskResponse;
import br.com.ecowatch.read_model_service.application.dto.AiStatusResponse;
import br.com.ecowatch.read_model_service.application.service.AiRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "Assistente de IA & RAG", description = "Endpoints para consultas semânticas e RAG (Retrieval-Augmented Generation) sobre notícias de desastres ambientais.")
public class AiController {

    private final AiRagService aiRagService;

    @Operation(
            summary = "Consultar o Assistente IA (RAG)",
            description = "Recebe uma pergunta em linguagem natural sobre o cenário climático, busca as notícias mais relevantes no banco vetorial e gera uma resposta fundamentada com citações das fontes oficiais."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resposta da IA gerada com sucesso e fontes anexadas"),
            @ApiResponse(responseCode = "400", description = "Pergunta inválida ou malformada")
    })
    @PostMapping("/ask")
    public ResponseEntity<AiAskResponse> askAi(@Valid @RequestBody AiAskRequest request) {
        AiAskResponse response = aiRagService.ask(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Status da Infraestrutura de IA",
            description = "Informa se o banco vetorial pgvector está operacional, quantas matérias foram vetorizadas e se o modelo Gemini está configurado."
    )
    @GetMapping("/status")
    public ResponseEntity<AiStatusResponse> getStatus() {
        return ResponseEntity.ok(aiRagService.getStatus());
    }

    @Operation(
            summary = "Sincronizar e Indexar Matérias para o RAG",
            description = "Varre o banco de notícias ambientais e gera os embeddings para as matérias que ainda não foram indexadas."
    )
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncEmbeddings() {
        int count = aiRagService.syncEmbeddings();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", count + " matérias sincronizadas com a base do RAG.",
                "totalProcessadas", count
        ));
    }
}
