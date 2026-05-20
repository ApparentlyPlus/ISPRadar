package com.ispradar.backend.config;

public class VodafoneConfig {
    public static final String BASE = "https://www.vodafone.gr";
    public static final String HOME = BASE + "/statheri-internet-programmata";
    public static final String GEO_API = BASE + "/api/geographicAddress";
    public static final String AVAIL_API = BASE + "/api/eligibilityTool/queryServiceQualification";
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";

    public static final String PARAM_STATE = "externalIdentifier[?(externalIdentifierType==\"stateOrProvince\")].id";
    public static final String PARAM_CITY = "externalIdentifier[?(externalIdentifierType==\"city\")].id";
    public static final String FIELDS_STATE = "stateOrProvince," + PARAM_STATE;
    public static final String FIELDS_CITY = "city," + PARAM_CITY;
    public static final String FIELDS_POSTAL = "postcode";
    public static final String FIELDS_STREET = "streetName";
    public static final String FIELDS_NUMBER = "streetNr,streetNrSuffix";
}
