package org.monarchinitiative.phenobfuscator.phenopacket;

import com.google.common.collect.ImmutableList;
import org.monarchinitiative.phenol.ontology.algo.OntologyAlgorithm;
import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.phenopackets.schema.v1.Phenopacket;
import org.phenopackets.schema.v1.core.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.monarchinitiative.phenol.ontology.algo.OntologyAlgorithm.getDescendents;

public class PhenopacketObfuscator {

    private Individual subject;
    private Disease simulatedDiagnosis;
    private List<PhenotypicFeature> hpoIdList;
    private List<Variant> variants;
    private Gene gene;
    private Ontology ontology;

    private final OntologyClass HOMOZYGOUS = OntologyClass.newBuilder().setId("GENO:0000136").setLabel("homozygous").build();
    private final OntologyClass HETEROZYGOUS = OntologyClass.newBuilder().setId("GENO:0000135").setLabel("heterozygous").build();

    private int n_alleles = 1;
    /** use imprecision (move terms to a parent term */
    private boolean imprecision = false;
    /**  use double imprecision (moves terms to a grandparent term */
    private boolean double_imprecision = false;
    /** number of n_noise terms to add */
    private int n_noise = 0;
    /** add an equal number of n_noise terms */
    private boolean matchNoise = false;

    /** Root term id in the phenotypic abnormality subontology. */
    private final static TermId PHENOTYPIC_ABNORMALITY = TermId.of("HP:0000118");



    /** This constructor should be used for all usages except the biallelic analysis. */
    public PhenopacketObfuscator(String path, Ontology o, int n_alleles, boolean imprecision, boolean double_imprecision, int noise, boolean matchNoise){
        this(path,o);
        this.n_alleles = n_alleles;
        this.imprecision = imprecision;
        this.double_imprecision = double_imprecision;
        this.matchNoise = matchNoise;
        this.n_noise = noise;
        if (imprecision && double_imprecision) {
            throw new RuntimeException("Cannot use --impression and --double_imprecision options at the same time");
        }
        if (matchNoise && n_alleles>0) {
            throw new RuntimeException("Cannot use --match_noise and --n_noise options at the same time");
        }

    }


    public PhenopacketObfuscator(String path, Ontology o) {
        File f = new File(path);
        if (! f.exists()) {
            throw new RuntimeException("Could not find phenopacket file at " + f.getAbsolutePath());
        }
        this.ontology = o;
        PhenopacketImporter importer = PhenopacketImporter.fromJson(path, this.ontology);
        this.subject = importer.getSubject();
        this.simulatedDiagnosis = importer.getDiagnosis();
        this.hpoIdList = importer.getPhenotypicFeatureList();
        this.variants = importer.getVariantList();
        this.gene = importer.getGene();
    }

    /** @return An obfuscated Phenopacket -- obfuscated according to the parameters. */
    public Phenopacket getObfuscation() {
        List<PhenotypicFeature> newHpoIdList;
        if (imprecision) {
            newHpoIdList = impreciseHpos();
        } else if (double_imprecision) {
            newHpoIdList = doubleImpreciseHpos();
        } else {
            newHpoIdList = new ArrayList<>(this.hpoIdList);
        }
        if (matchNoise) {
            n_noise = newHpoIdList.size();
        }
        if (n_noise>0) {
            List<PhenotypicFeature> noiseTerms = getNoiseTerms(n_noise);
            newHpoIdList.addAll(noiseTerms);
        }
        if (n_alleles == 2) {
            variants = ImmutableList.of();
        } else if (n_alleles == 1) {
            Variant v = extractHeterozygousVariant(this.variants);
            this.variants = new ArrayList<>();
            this.variants.add(v);
        }
        return Phenopacket.newBuilder().
                setSubject(subject).
                addDiseases(simulatedDiagnosis).
                addAllPhenotypicFeatures(newHpoIdList).
                addGenes(gene).
                addAllVariants(this.variants).
                build();
    }


    /**
     *  This is a term that was observed in the simulated patient (note that it should not be a HpoTermId, which
     *  contains metadata about the term in a disease entity, such as overall frequency. Instead, we are simulating an
     *  individual patient and this is a definite observation.
     * @param n_noise number of noise terms
     * @param negated if true, negate the noise term, i.e., "NOT"
     * @return a random term from the phenotype subontology.
     */
      private  List<PhenotypicFeature> getNoiseTerms(int n_noise, boolean negated) {
        Set<TermId> descendents=getDescendents(ontology,PHENOTYPIC_ABNORMALITY);
        ImmutableList.Builder<TermId> builder = new ImmutableList.Builder<>();
        for (TermId t: descendents) {
            builder.add(t);
        }
        final ImmutableList<TermId> phenotypeterms = builder.build();
        int n = phenotypeterms.size();
        List<PhenotypicFeature> pfl = new ArrayList<>();

        for (int i=0;i<n_noise;i++) {
            int r = new Random().nextInt(n);
            TermId tid = phenotypeterms.get(r);
            OntologyClass oc = OntologyClass.newBuilder().
                    setId(tid.getValue()).
                    setLabel(this.ontology.getTermMap().get(tid).getName()).build();
            if (negated) {
                PhenotypicFeature pf = PhenotypicFeature.newBuilder()
                        .setType(oc).setNegated(true).build();
                pfl.add(pf);
            } else {
                PhenotypicFeature pf = PhenotypicFeature.newBuilder()
                        .setType(oc).build();
                pfl.add(pf);
            }
        }
        return pfl;
    }

    public Phenopacket getObfuscationWithNotTermsRemoved() {
        List<PhenotypicFeature> newHpoIdList =
                this.hpoIdList.stream().filter( pf -> ! pf.getNegated()).collect(Collectors.toList());

        return Phenopacket.newBuilder().
                setSubject(subject).
                addDiseases(simulatedDiagnosis).
                addAllPhenotypicFeatures(newHpoIdList).
                addGenes(gene).
                addAllVariants(this.variants).
                build();
    }

    /**
     * This is a term that was observed in the simulated patient (note that it should not be a HpoTermId, which
     * contains metadata about the term in a disease entity, such as overall frequency. Instead, we are simulating an
     * individual patient and this is a definite observation.
     * @return a random "observed" term from the phenotype subontology.
     */
    private  List<PhenotypicFeature> getNoiseTerms(int n_noise) {
        return getNoiseTerms(n_noise, false);
    }




    private PhenotypicFeature getParentPhenotypicFeature(PhenotypicFeature pf) {
        OntologyClass oc = pf.getType();
        TermId tid = TermId.of(oc.getId());
        if (! ontology.getTermMap().containsKey(tid)) {
            throw new RuntimeException("Could not find phenopacket term " + oc.getId() + " : " + oc.getLabel());
        }
        Set<TermId> parents = OntologyAlgorithm.getParentTerms(ontology,tid,false);
        if (parents.isEmpty()) {
            throw new RuntimeException("Could not find parent term for " + oc.getId() + " : " + oc.getLabel());
        }
        int idx = new Random().nextInt(parents.size());
        TermId newTid = new ArrayList<>(parents).get(idx);
        OntologyClass oc2 = OntologyClass.newBuilder().
                setId(newTid.getValue()).
                setLabel(this.ontology.getTermMap().get(newTid).getName()).build();
        return PhenotypicFeature.newBuilder()
                .setType(oc2).build();
    }

    private PhenotypicFeature getGrandparentPhenotypicFeature(PhenotypicFeature pf) {
        OntologyClass oc = pf.getType();
        TermId tid = TermId.of(oc.getId());
        if (! ontology.getTermMap().containsKey(tid)) {
            throw new RuntimeException("Could not find phenopacket term " + oc.getId() + " : " + oc.getLabel());
        }
        Set<TermId> parents = OntologyAlgorithm.getParentTerms(ontology,tid,false);
        if (parents.isEmpty()) {
            throw new RuntimeException("Could not find parent term for " + oc.getId() + " : " + oc.getLabel());
        }
        Set<TermId> grandparents = OntologyAlgorithm.getParentTerms(ontology,parents,false);
        if (grandparents.isEmpty()) {
            grandparents = parents; // allowed to take only one generation if no other way
        }
        int idx = new Random().nextInt(grandparents.size());
        TermId newTid = new ArrayList<>(grandparents).get(idx);
        OntologyClass oc2 = OntologyClass.newBuilder().
                setId(newTid.getValue()).
                setLabel(this.ontology.getTermMap().get(newTid).getName()).build();
        return PhenotypicFeature.newBuilder()
                .setType(oc2).build();
    }





    private  List<PhenotypicFeature> impreciseHpos() {
        List<PhenotypicFeature> hpos = new ArrayList<>();
        for (PhenotypicFeature pf : this.hpoIdList) {
            PhenotypicFeature parent = getParentPhenotypicFeature(pf);
            hpos.add(parent);
        }
        return hpos;
    }


    private  List<PhenotypicFeature> doubleImpreciseHpos() {
        List<PhenotypicFeature> hpos = new ArrayList<>();
        for (PhenotypicFeature pf : this.hpoIdList) {
            PhenotypicFeature parent = getGrandparentPhenotypicFeature(pf);
            hpos.add(parent);
        }
        return hpos;
    }

    public boolean diseaseIsAutosomalRecessive() {
        if (variants.size()==2) {
            return true;
        } else if (variants.size() == 1) {
            Variant var = variants.get(0);
            if (var.getZygosity().equals(HOMOZYGOUS)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Extract a single heterozygous variant.
     * Assumption. There is either one homozygous variants or multiple het/hom variants
     * @param variants A list of one or more variant of arbitrary genotype
     * @return A single heterozygous Variant
     */
    private Variant extractHeterozygousVariant(List<Variant> variants) {
        Variant obfuvar;
        Variant currentVar;
        if (variants.size()==1) {
            currentVar = variants.get(0);
        } else {
            Random rand = new Random();
            int rand_int = rand.nextInt(variants.size());
            currentVar = variants.get(rand_int);
        }
        VcfAllele currentAllele = currentVar.getVcfAllele();
        VcfAllele obfuAlle = VcfAllele.newBuilder().
                setChr(currentAllele.getChr()).
                setPos(currentAllele.getPos()).
                setGenomeAssembly(currentAllele.getGenomeAssembly()).
                setRef(currentAllele.getRef()).
                setAlt(currentAllele.getAlt()).
                build();
        obfuvar = Variant.newBuilder().
                setVcfAllele(obfuAlle).
                setZygosity(HETEROZYGOUS).
                build();
        return obfuvar;
    }

    /**
     *  we keep one mutant allele
     *  this might be the case if there is a single homozygous variant,
     *  then, change the zygosity to heterzygous
     *  or if there are compound het variants, just keep one
     * @return a biallelic-ly obfuscated phenopacket
     */
    public Phenopacket getBiallelicObfuscation() {
        Variant hetvar = extractHeterozygousVariant(variants);
        return Phenopacket.newBuilder().
                setSubject(subject).
                addDiseases(simulatedDiagnosis).
                addAllPhenotypicFeatures(hpoIdList).
                addGenes(gene).
                addVariants(hetvar).
                build();
    }


    private Phenopacket getObfuscatedPhenopacket(String path) {

        // if we are doing biallelic, then we keep one mutant allele
        // this might be the case if there is a single homozygous variant,
        // then, chenge the zygosity to heterzygous
        // or if there are compound het variants, just keep one
        // if biallelic==false, get rid of all variants.


            return Phenopacket.newBuilder().
                    setSubject(subject).
                    addDiseases(simulatedDiagnosis).
                    addAllPhenotypicFeatures(hpoIdList).
                    addGenes(gene).
                    addAllVariants(variants).
                    build();

    }

    /**
     * Exchange all "original" HPO terms with random HPO terms.
     * @return a phenopacket in which all HPO terms have beeen replaced by random terms
     */
    public Phenopacket getObfuscationByReplacement() {
        int n_observed = 0;
        int n_negated = 0;
        for (PhenotypicFeature pf : this.hpoIdList) {
            if (pf.getNegated()) {
                n_negated++;
            } else {
                n_observed ++;
            }
        }
        List<PhenotypicFeature> pfl = new ArrayList<>();
        List<PhenotypicFeature> randomObserved = getNoiseTerms(n_observed);
        List<PhenotypicFeature> randomNegated = getNoiseTerms(n_negated, true);
        pfl.addAll(randomObserved);
        pfl.addAll(randomNegated);
        return Phenopacket.newBuilder().
                setSubject(subject).
                addDiseases(simulatedDiagnosis).
                addAllPhenotypicFeatures(pfl).
                addGenes(gene).
                build();

    }
}
