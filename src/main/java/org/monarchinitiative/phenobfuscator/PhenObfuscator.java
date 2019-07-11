package org.monarchinitiative.phenobfuscator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.protobuf.util.JsonFormat;
import org.monarchinitiative.phenobfuscator.io.PhenopacketImporter;
import org.monarchinitiative.phenol.base.PhenolRuntimeException;
import org.monarchinitiative.phenol.io.OntologyLoader;
import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.phenopackets.schema.v1.Phenopacket;
import org.phenopackets.schema.v1.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * Output all of the
 */
public class PhenObfuscator {
    private static final Logger logger = LoggerFactory.getLogger(PhenObfuscator.class);
    @Parameter(names = {"-h", "--help"}, help = true, arity = 0,description = "display this help message")
    private boolean usageHelpRequested;
    @Parameter(names = {"--hpo"},description = "path to hp.obo file",required = true)
    private String hpoPath;
    @Parameter(names = {"-p", "--phenopacket"}, description = "path to directory with phenopacket files",required = true)
    private String phenopacketDirectoryPath=null;
    @Parameter(names = {"--biallelic"}, description = "Output only recessive diseases and output one pathogenic allele only")
    private boolean biallelic = false;

    private Ontology ontology=null;


    private List<File> phenopacketFiles;


    private final OntologyClass HOMOZYGOUS = OntologyClass.newBuilder().setId("GENO:0000136").setLabel("homozygous").build();
    private final OntologyClass HETEROZYGOUS = OntologyClass.newBuilder().setId("GENO:0000135").setLabel("heterozygous").build();
    private final String OUTPUT_DIRECTORY = "obfuscated";



    public static void main(String []args){
        PhenObfuscator obfuscator = new PhenObfuscator();
        JCommander jc = JCommander.newBuilder().addObject(obfuscator).build();
        try {
            jc.parse(args);
        } catch (ParameterException e) {
            System.err.println("[ERROR] "+e.getMessage());
            System.err.println("[ERROR] enter java -jar phenobfuscator.jar -h for more information.");
            System.exit(1);
        }

        obfuscator.getListOfPhenopacketFiles();
        obfuscator.ingestHpo();
        obfuscator.obfuscate();


    }


    private void ingestHpo() {
        this.ontology = OntologyLoader.loadOntology(new File(this.hpoPath));
    }

    /**
     * Extract a signle heterozygous variant.
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



    private Phenopacket getObfuscatedPhenopacket(String path) {
        PhenopacketImporter importer = PhenopacketImporter.fromJson(path, this.ontology);
        Individual subject = importer.getSubject();
        Disease simulatedDiagnosis = importer.getDiagnosis();
        List<PhenotypicFeature> hpoIdList = importer.getPhenotypicFeatureList();
        List<Variant> variants = importer.getVariantList();
        Gene gene = importer.getGene();
        // if we are doing biallelic, then we keep one mutant allele
        // this might be the case if there is a single homozygous variant,
        // then, chenge the zygosity to heterzygous
        // or if there are compound het variants, just keep one
        // if biallelic==false, get rid of all variants.

        if (biallelic) {
            if (variants.size()==1) {
                Variant var = variants.get(0);
                if (!var.getZygosity().equals(HOMOZYGOUS)) {
                    return null;
                }
            }
            Variant hetvar = extractHeterozygousVariant(variants);
            return Phenopacket.newBuilder().
                    setSubject(subject).
                    addDiseases(simulatedDiagnosis).
                    addAllPhenotypicFeatures(hpoIdList).
                    addGenes(gene).
                    addVariants(hetvar).
                    build();
        } else {
            return Phenopacket.newBuilder().
                    setSubject(subject).
                    addDiseases(simulatedDiagnosis).
                    addAllPhenotypicFeatures(hpoIdList).
                    addGenes(gene).
                    build();
        }
    }

    private static String toJson(Phenopacket phenoPacket) throws IOException {
        return JsonFormat.printer().print(phenoPacket);
    }

    private void obfuscate() {
        new File(OUTPUT_DIRECTORY).mkdir();
        for (File file: this.phenopacketFiles) {
            String phenopacketAbsolutePath = file.getAbsolutePath();
            Phenopacket obfuscated = getObfuscatedPhenopacket(phenopacketAbsolutePath);
            if (obfuscated == null) continue;
            String basename = file.getName();
            String path2 = String.format("%s%s%s",OUTPUT_DIRECTORY,File.separator,basename);
            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(path2));
                bw.write(toJson(obfuscated));
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }




    private void getListOfPhenopacketFiles() {
        phenopacketFiles = new ArrayList<>();
        final File folder = new File(phenopacketDirectoryPath);
        if (! folder.isDirectory()) {
            throw new PhenolRuntimeException("Could not open Phenopackets directory at "+phenopacketDirectoryPath);
        }
        int counter=0;
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isFile() && fileEntry.getAbsolutePath().endsWith(".json")) {
                logger.info("\tPhenopacket: \"{}\"", fileEntry.getAbsolutePath());
                System.out.println(++counter + ") "+ fileEntry.getName());
                this.phenopacketFiles.add(fileEntry);
            }
        }
    }

}
