package com.fluxaria.middleware.domain.model;

/**
 * Detalles de país obtenidos del servicio de enriquecimiento (RestCountries).
 * Objeto de valor puro e inmutable.
 */
public record CountryDetails(
        String officialName,
        String region,
        String localCurrency,
        String phonePrefix
) {
    public static CountryDetails empty() {
        return new CountryDetails(null, null, null, null);
    }
}