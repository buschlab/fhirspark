package fhirspark.adapter;

import fhirspark.settings.Regex;
import java.util.List;

public final class RegexAdapter {

    private RegexAdapter() {
    }

    public static String applyRegexToCbioportal(List<Regex> regex, String input) {
        String output = input;
        for (Regex r : regex) {
            output = output.replaceAll(r.getHis(), r.getCbio());
        }
        return output;
    }

    public static String applyRegexFromCbioportal(List<Regex> regex, String input) {
        String output = input;
        for (Regex r : regex) {
            output = output.replaceAll(r.getCbio(), r.getHis());
        }
        return output;
    }
}
