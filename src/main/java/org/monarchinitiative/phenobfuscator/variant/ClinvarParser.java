package org.monarchinitiative.phenobfuscator.variant;

import com.google.common.collect.ImmutableSet;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;

public class ClinvarParser {

    Set<Variant> variantSet;

    public ClinvarParser(String path) {
        parse(path);
    }

    private void parse(String path) {
        ImmutableSet.Builder<Variant> builder = new ImmutableSet.Builder<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#"))
                    continue;
                Variant var = Variant.fromVcfLine(line);
                builder.add(var);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        variantSet = builder.build();
    }

    public Set<Variant> getVariantSet() {
        return variantSet;
    }
}
