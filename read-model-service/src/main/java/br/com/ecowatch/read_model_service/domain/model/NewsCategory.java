package br.com.ecowatch.read_model_service.domain.model;

/**
 * Categorias temáticas de notícias sobre desastres e monitoramento ambiental.
 */
public enum NewsCategory {
    FIRE,           // Queimadas, incêndios florestais e fumaça
    FLOOD,          // Rios, cheias, enchentes, vazantes e inundações
    RAIN,           // Chuvas fortes, tempestades, temporais e granizo
    DENGUE,         // Dengue, aedes e arboviroses
    DEFORESTATION,  // Desmatamento, alertas DETER/INPE e pressão sobre biomas
    GENERAL         // Secas, estiagens, ondas de calor e ar seco
}
