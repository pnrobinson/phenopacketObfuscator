package org.monarchinitiative.phenobfuscator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.protobuf.util.JsonFormat;
import org.monarchinitiative.phenobfuscator.phenopacket.PhenopacketObfuscator;
import org.monarchinitiative.phenol.base.PhenolRuntimeException;
import org.monarchinitiative.phenol.io.OntologyLoader;
import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.phenopackets.schema.v1.Phenopacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Output all of the
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    @Parameter(names = {"-h", "--help"}, help = true, arity = 0,description = "display this help message")
    private boolean usageHelpRequested;
    @Parameter(names = {"--hpo"},description = "path to hp.obo file",required = true)
    private String hpoPath;
    @Parameter(names = {"-p", "--phenopacket"}, description = "path to directory with phenopacket files",required = true)
    private String phenopacketDirectoryPath=null;
    @Parameter(names = {"--biallelic"}, description = "Output only recessive diseases and output one pathogenic allele only")
    private boolean biallelic = false;
    @Parameter(names = {"--n_alleles"}, description = "number of alleles to remove from phenopacket (1 or 2)")
    private
    int n_alleles = 1;
    @Parameter(names = {"--imprecision"}, description = "use imprecision (move terms to a parent term")
    private
    boolean imprecision = false;
    @Parameter(names = {"--double_imprecision"}, description = "use double imprecision (moves terms to a grandparent term")
    private
    boolean double_imprecision = false;
    @Parameter(names = {"--n_noise"}, description = "number of noise terms to add")
    private
    int noise = 0;
    @Parameter(names = {"--match_noise"}, description = "add an equal number of noise terms")
    private
    boolean matchNoise = false;

    private Ontology ontology=null;


    private List<File> phenopacketFiles;


    private final String OUTPUT_DIRECTORY = "obfuscated";


    public static void main(String []args){
        Main obfuscator = new Main();
        JCommander jc = JCommander.newBuilder().addObject(obfuscator).build();
        try {
            jc.parse(args);
        } catch (ParameterException e) {
            System.err.println("[ERROR] "+e.getMessage());
            System.err.println("[ERROR] enter java -jar phenobfuscator.jar -h for more information.");
            System.exit(1);
        }
        if (obfuscator.usageHelpRequested) {
            jc.usage();
            System.exit(1);
        }
        obfuscator.checkInputData();
        obfuscator.getListOfPhenopacketFiles();
        obfuscator.ingestHpo();
        obfuscator.obfuscate();
    }



    private void checkInputData() {
        if (hpoPath == null) {
            throw new RuntimeException("Path to HPO obo file not initialized (--hpo option)");
        }
        File f = new File(this.hpoPath);
        if (! f.exists()) {
            throw new RuntimeException("Could not find HPO obo file (--hpo option)");
        }
        if (phenopacketDirectoryPath == null ) {
            throw new RuntimeException("phenopacketDirectoryPath was not initialized! (-p/--phenopacket option)");
        }
    }


    private void ingestHpo() {
        this.ontology = OntologyLoader.loadOntology(new File(this.hpoPath));
    }




    private static String toJson(Phenopacket phenoPacket) throws IOException {
        return JsonFormat.printer().print(phenoPacket);
    }



    private void obfuscate() {
        new java.io.File(OUTPUT_DIRECTORY).mkdir();
        /*

        */
        if (biallelic) {
            obfuscateBiallelic();
        } else {
            obfuscateParams();
        }
    }


    /**
     * Transform e.g., Arora-2019-COG8-proband.json to Arora-2019-COG8-proband_biallelic_obfuscated.json
     * @param bname original basename
     * @return obfuscated basename
     */
    private String getBiallelicObfuscatedBasename(String bname) {
        String[] A = bname.split(".");
        if (A.length != 2) {
            throw new RuntimeException("Unexpected phenopacket basename: " + bname);
        }
        return String.format("%s_biallelic_obfuscated.%s", A[0],A[1]);
    }



    private void obfuscateBiallelic() {
        for (java.io.File file: this.phenopacketFiles) {
            String phenopacketAbsolutePath = file.getAbsolutePath();
            PhenopacketObfuscator pobfuscator = new PhenopacketObfuscator(phenopacketAbsolutePath, this.ontology);
            if (pobfuscator.diseaseIsAutosomalRecessive()) {
                Phenopacket obfuscated = pobfuscator.getBiallelicObfuscation();
                String basename = getBiallelicObfuscatedBasename(file.getName());
                String path2 = String.format("%s%s%s", OUTPUT_DIRECTORY, File.separator, basename);
                try {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(path2));
                    bw.write(toJson(obfuscated));
                    bw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private void obfuscateParams() {
        for (java.io.File file: this.phenopacketFiles) {
            String phenopacketAbsolutePath = file.getAbsolutePath();
            PhenopacketObfuscator pobfuscator = new PhenopacketObfuscator(phenopacketAbsolutePath, this.ontology, n_alleles, imprecision, double_imprecision, noise, matchNoise);
            Phenopacket obfuscated = pobfuscator.getObfuscation();
            String basename = file.getName();
            String path2 = String.format("%s%s%s",OUTPUT_DIRECTORY, File.separator, basename);
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
