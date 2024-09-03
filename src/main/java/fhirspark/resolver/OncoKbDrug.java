package fhirspark.resolver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fhirspark.resolver.model.Drug;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cache for available Drugs from OncoKB (offline).
 */
public final class OncoKbDrug {

    private static final Map<String, Drug> DRUG_MAP = new HashMap<>();

    private OncoKbDrug() {
    }

    /**
     * Initalizes cache for Drugs.
     * @param dbPath Path of the database.
     */
    public static void initalize(String dbPath) {
        try {
            List<Drug> drugs = new ObjectMapper().readerFor(new TypeReference<List<Drug>>() {
            }).readValue(new FileInputStream(dbPath));
            for (Drug d : drugs) {
                DRUG_MAP.put(d.getDrugName(), d);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     *
     * @param name drug that shall be resolved.
     * @return Drug entry from OncoKB.
     */
    public static Drug resolve(String name) {
        return DRUG_MAP.getOrDefault(name, new Drug());
    }
}
