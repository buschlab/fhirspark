package fhirspark.resolver;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import fhirspark.resolver.model.Genenames;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cache for available Genenames from HGNC (offline).
 */
public final class HgncGeneName {

    private static final Map<Integer, Genenames> HGNC_MAP = new HashMap<>();

    private HgncGeneName() {
    }

    /**
     * Initalizes cache for Genenames.
     * @param dbPath Path of the database.
     */
    public static void initialize(String dbPath) {
        try {
            Iterator<Genenames> iterator = new CsvMapper().readerFor(Genenames.class)
                    .with(CsvSchema.emptySchema().withHeader().withColumnSeparator('\t'))
                    .readValues(new FileInputStream(dbPath));
            while (iterator.hasNext()) {
                Genenames g = iterator.next();
                HGNC_MAP.put(g.getNcbiGeneId(), g);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static Genenames resolve(int ncbiGeneId) {
        return HGNC_MAP.get(ncbiGeneId);
    }

}
