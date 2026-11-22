package br.com.ecowatch.read_model_service.infrastructure.api;

import br.com.ecowatch.read_model_service.application.dto.EmissoesDTO.*;
import br.com.ecowatch.read_model_service.application.service.EmissoesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping({"/api/emissoes", "/api/v1/emissoes"})
@RequiredArgsConstructor
@Tag(name = "Emissões de Gases de Efeito Estufa (SEEG)", description = "Ranking de municípios e estados emissores por gás e setor.")
public class EmissoesController {

    private final EmissoesService emissoesService;

    @Operation(
            summary = "Ranking de municípios emissores",
            description = "Retorna os municípios que mais emitem gases de efeito estufa (CO2e, CH4, CO2, N2O), com filtro opcional por UF e quebra por setor."
    )
    @GetMapping("/ranking")
    public ResponseEntity<List<RankingMunicipioItem>> getRanking(
            @Parameter(description = "Gás analisado: CO2e, CH4, CO2, N2O") @RequestParam(defaultValue = "CO2e") String gas,
            @Parameter(description = "Sigla do estado (UF), ex.: PA, MT, SP (ou omitir para Brasil inteiro)") @RequestParam(required = false) String uf,
            @Parameter(description = "Ano dos dados (padrão: ano mais recente disponível, ex. 2023)") @RequestParam(required = false) Integer ano,
            @Parameter(description = "Limite de resultados no ranking (padrão 10, máx 100)") @RequestParam(defaultValue = "10") Integer limite
    ) {
        return ResponseEntity.ok(emissoesService.getRanking(gas, uf, ano, limite));
    }

    @Operation(
            summary = "Emissões totais agregadas por estado (UF)",
            description = "Retorna o total de emissões de cada um dos estados brasileiros e do Distrito Federal para o gás escolhido."
    )
    @GetMapping("/estados")
    public ResponseEntity<List<EmissaoEstadoItem>> getEstados(
            @Parameter(description = "Gás analisado: CO2e, CH4, CO2, N2O") @RequestParam(defaultValue = "CO2e") String gas,
            @Parameter(description = "Ano dos dados (padrão: ano mais recente)") @RequestParam(required = false) Integer ano
    ) {
        return ResponseEntity.ok(emissoesService.getEstados(gas, ano));
    }

    @Operation(
            summary = "Série histórica e setores de um município",
            description = "Retorna o histórico dos últimos anos e a quebra detalhada por setor econômico emissor para um município específico."
    )
    @GetMapping("/municipio/{nome}")
    public ResponseEntity<MunicipioDetalheDTO> getMunicipio(
            @Parameter(description = "Nome do município, ex.: São Félix do Xingu, Altamira, Cuiabá, Belém") @PathVariable String nome,
            @Parameter(description = "Gás analisado: CO2e, CH4, CO2, N2O") @RequestParam(defaultValue = "CO2e") String gas
    ) {
        MunicipioDetalheDTO dto = emissoesService.getMunicipioDetalhe(nome, gas);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @Operation(
            summary = "Grandes fontes emissoras pontuais (Climate TRACE)",
            description = "Retorna as principais fontes físicas pontuais de emissão de gases de efeito estufa no Brasil (usinas, aterros, mineração, refinarias, pecuária) com coordenadas geográficas."
    )
    @GetMapping("/fontes")
    public ResponseEntity<List<FonteEmissaoDTO>> getFontes(
            @Parameter(description = "Gás analisado: CO2e, CH4") @RequestParam(defaultValue = "CH4") String gas,
            @Parameter(description = "Filtrar por setor (ex.: waste, power, manufacturing, agriculture)") @RequestParam(required = false) String setor,
            @Parameter(description = "Limite de fontes (padrão 100, máx 200)") @RequestParam(defaultValue = "100") Integer limite
    ) {
        return ResponseEntity.ok(emissoesService.getFontesEmissao(gas, setor, limite));
    }
}
