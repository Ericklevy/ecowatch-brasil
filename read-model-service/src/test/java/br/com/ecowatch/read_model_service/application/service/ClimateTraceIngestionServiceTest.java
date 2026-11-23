package br.com.ecowatch.read_model_service.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClimateTraceIngestionServiceTest {

    @Test
    @DisplayName("Deve formatar e traduzir fontes municipais e instalações em inglês do Climate TRACE")
    void deveFormatarNomesFontes() {
        // Municípios com setores específicos
        assertEquals("Município de Santa Vitória do Palmar (Cultivo de Arroz)",
                ClimateTraceIngestionService.formatarNomeFonte("Santa Vitória do Palmar Municipality", "rice-cultivation", null));

        assertEquals("Município de Corumbá (Pecuária)",
                ClimateTraceIngestionService.formatarNomeFonte("Corumbá Municipality", "enteric-fermentation-cattle-pasture", null));

        assertEquals("Município de São Félix do Xingu (Queimadas)",
                ClimateTraceIngestionService.formatarNomeFonte("São Félix do Xingu Municipality", "forest-land-fires", null));

        assertEquals("Município de São Félix do Xingu (Desmatamento)",
                ClimateTraceIngestionService.formatarNomeFonte("São Félix do Xingu Municipality", "forest-land-clearing", null));

        // Instalações industriais e de infraestrutura
        assertEquals("Represa de Sobradinho",
                ClimateTraceIngestionService.formatarNomeFonte("Sobradinho Main Dam", "water-reservoirs", "Hydroelectricity"));

        assertEquals("Aterro Sanitário Seropedica",
                ClimateTraceIngestionService.formatarNomeFonte("Seropedica Landfill", "solid-waste-disposal", "Sanitary Landfill"));

        assertEquals("Siderúrgica ArcelorMittal Tubarão",
                ClimateTraceIngestionService.formatarNomeFonte("ArcelorMittal Tubarão steel plant", "iron-and-steel", "BF/BOF"));

        assertEquals("Mina de Carvão Calombo",
                ClimateTraceIngestionService.formatarNomeFonte("Calombo Coal Mine", "coal-mining", "Surface"));
    }

    @Test
    @DisplayName("Deve traduzir setores e subsetores com precisão")
    void deveTraduzirSetoresESubSetores() {
        assertEquals("Geração Hidrelétrica (Represas)", ClimateTraceIngestionService.traduzirSetor("water-reservoirs"));
        assertEquals("Agropecuária", ClimateTraceIngestionService.traduzirSetor("rice-cultivation"));
        assertEquals("Indústria e Siderurgia", ClimateTraceIngestionService.traduzirSetor("iron-and-steel"));
        assertEquals("Mudança do Uso da Terra e Queimadas", ClimateTraceIngestionService.traduzirSetor("forest-land-fires"));

        assertEquals("Aterro Sanitário", ClimateTraceIngestionService.traduzirSubSetor("Sanitary Landfill", "solid-waste-disposal"));
        assertEquals("Cultivo de Arroz Irrigado", ClimateTraceIngestionService.traduzirSubSetor(null, "rice-cultivation"));
        assertEquals("Fermentação Entérica (Pecuária)", ClimateTraceIngestionService.traduzirSubSetor(null, "enteric-fermentation-cattle-pasture"));
        assertEquals("Usinas Hidrelétricas", ClimateTraceIngestionService.traduzirSubSetor("Hydroelectricity", "water-reservoirs"));
    }
}
