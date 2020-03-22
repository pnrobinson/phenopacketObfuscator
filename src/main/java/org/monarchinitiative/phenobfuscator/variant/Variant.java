package org.monarchinitiative.phenobfuscator.variant;



import org.phenopackets.schema.v1.core.VcfAllele;

import java.util.Objects;

/**
 * Simple representation of a VCF-encoded variant that we can use to check whether a phenopacket references
 * a ClinVar pathogenic variant or not.
 */
public class Variant {
    private final String chr;
    private final int pos;
    private final String ref;
    private final String alt;
    private final ClinvarSignificance cvsig;


     Variant(String chr, int p, String r, String a, ClinvarSignificance sig) {
        this.chr = chr;
        this.pos = p;
        this.ref = r;
        this.alt = a;
        this.cvsig = sig;
    }


    public static Variant fromPhenopacketVariant(org.phenopackets.schema.v1.core.Variant var) {
         VcfAllele allele = var.getVcfAllele();
         return new Variant(allele.getChr(), allele.getPos(), allele.getRef(), allele.getAlt(), ClinvarSignificance.OTHER);
    }


    public static Variant fromVcfLine(String line) {
        String [] fields = line.split("\t");
        String chr = fields[0];
        int pos = Integer.parseInt(fields[1]);
        String ref = fields[3];
        String alt = fields[4];
        String info = fields[7];
        ClinvarSignificance cvsig = extractClinVarSig(info);
        return new Variant(chr, pos, ref, alt, cvsig);
    }

    private static ClinvarSignificance extractClinVarSig(String info) {
        String [] fields = info.split(";");
        for (String f : fields) {
            if (f.startsWith("CLNSIG=")) {
                String sg = f.substring(7);
                switch (sg.toUpperCase()) {
                    case "BENIGN":
                        return ClinvarSignificance.BENIGN;
                    case "LIKELY_BENIGN":
                    case "BENIGN/LIKELY_BENIGN":
                        return ClinvarSignificance.LIKELY_BENIGN;
                    case "PATHOGENIC":
                    case "PATHOGENIC,_RISK_FACTOR":
                        return ClinvarSignificance.PATHOGENIC;
                    case "LIKELY_PATHOGENIC":
                    case "PATHOGENIC/LIKELY_PATHOGENIC":
                        return ClinvarSignificance.LIKELY_PATHOGENIC;
                    case "UNCERTAIN_SIGNIFICANCE":
                    case "CONFLICTING_INTERPRETATIONS_OF_PATHOGENICITY":
                    case "NOT_PROVIDED":
                        return ClinvarSignificance.VUS;
                    case "RISK_FACTOR":
                        return ClinvarSignificance.RISK_FACTOR;
                    case "AFFECTS":
                    case "ASSOCIATION":
                        return ClinvarSignificance.AFFECTS;

                    default:
                        System.err.println("Could not id constant:"+ sg);
                        return ClinvarSignificance.OTHER;
                }
            }
        }
        return ClinvarSignificance.BENIGN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Variant variant = (Variant) o;
        return pos == variant.pos &&
                Objects.equals(chr, variant.chr) &&
                Objects.equals(ref, variant.ref) &&
                Objects.equals(alt, variant.alt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chr, pos, ref, alt);
    }
}
