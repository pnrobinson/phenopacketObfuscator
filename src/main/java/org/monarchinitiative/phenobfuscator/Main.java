package org.monarchinitiative.phenobfuscator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.protobuf.util.JsonFormat;
import org.monarchinitiative.phenobfuscator.phenopacket.PhenopacketObfuscator;
import org.monarchinitiative.phenobfuscator.variant.ClinvarParser;
import org.monarchinitiative.phenobfuscator.variant.Variant;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


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
    @Parameter(names = {"--replace"}, description = "replace all terms with noise terms")
    private boolean replaceTerms = false;
    @Parameter(names = {"--no_not"}, description = "remove all negated query terms")
    private boolean noNot = false;
    @Parameter(names ={"--output_all_obfuscations"}, description = "Output all forms of obfuscation")
    private boolean outputAllObfuscations = false;
    @Parameter(names = {"--sort-by-moi"}, description = "sort into autosomal and recessive mode of inheritance")
    private boolean sortByMoi = false;
    @Parameter(names = {"--clinvar"}, description = "path to clinvar VCF file")
    String clinVarPath = null;


    private final static String MOI_RECESSIVE_CLINVAR = "MOI_RECESSIVE_CLINVAR";
    private final static String MOI_RECESSIVE_NO_CLINVAR = "MOI_RECESSIVE_NO_CLINVAR";
    private final static String MOI_DOMINANT_CLINVAR = "MOI_DOMINANT_CLINVAR";
    private final static String MOI_DOMINANT_NO_CLINVAR = "MOI_DOMINANT_NO_CLINVAR";

    private Ontology ontology=null;


    private List<File> phenopacketFiles;

    @Parameter(names = {"--out"}, description = "name of output directory")
    private String OUTPUT_DIRECTORY = "obfuscated";


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

    private String createOutputDirectory(String path) {
        Path p = Paths.get(path);
        if (Files.exists(p)) {
            new File(path).delete();
        }
        if (! new File(path).mkdir() ) {
            throw new PhenolRuntimeException("Could not create directory at " + path);
        }
        return path;
    }



    private void obfuscate() {
        new java.io.File(OUTPUT_DIRECTORY).mkdir();
        if (sortByMoi) {
            if (this.clinVarPath == null) {
                throw new PhenolRuntimeException("Need to pass path to ClinVar VCF file for MOI option");
            }
            ClinvarParser parser = new ClinvarParser(this.clinVarPath);
            OUTPUT_DIRECTORY = createOutputDirectory(MOI_RECESSIVE_CLINVAR);
            OUTPUT_DIRECTORY = createOutputDirectory(MOI_RECESSIVE_NO_CLINVAR);
            OUTPUT_DIRECTORY = createOutputDirectory(MOI_DOMINANT_CLINVAR);
            OUTPUT_DIRECTORY = createOutputDirectory(MOI_DOMINANT_NO_CLINVAR);
            outputByMoi();
            return;
        }
        if (outputAllObfuscations) {
            OUTPUT_DIRECTORY = createOutputDirectory("BIALLELIC");
            obfuscateBiallelic();
            OUTPUT_DIRECTORY = createOutputDirectory("ALLTERMS_RANDOMIZED");
            obfuscateByReplacement();
            OUTPUT_DIRECTORY = createOutputDirectory("NO_NOT");
            obfuscateByRemovingNotQueryTerms();
            OUTPUT_DIRECTORY = createOutputDirectory("NOISE_2");
            n_alleles = 0;
            imprecision = false;
            double_imprecision = false;
            matchNoise = false;
            noise = 2;
            obfuscateParams();
            OUTPUT_DIRECTORY = createOutputDirectory("NOISE_2_IMPRECISION");
            imprecision = true;
            double_imprecision = false;
            matchNoise = false;
            noise = 2;
            obfuscateParams();
            OUTPUT_DIRECTORY = createOutputDirectory("NOISE_2_DOUBLEIMPRECISION");
            imprecision = false;
            double_imprecision = true;
            matchNoise = false;
            noise = 2;
            obfuscateParams();
            OUTPUT_DIRECTORY = createOutputDirectory("REMOVE_2_ALLELES");
            n_alleles = 2;
            imprecision = false;
            double_imprecision = false;
            matchNoise = false;
            noise = 0;
            obfuscateParams();
            OUTPUT_DIRECTORY = createOutputDirectory("REMOVE_2_ALLELE_NOISE_2_IMPRECISION");
            n_alleles = 2;
            imprecision = true;
            double_imprecision = false;
            matchNoise = false;
            noise = 2;
            obfuscateParams();
        }


        if (biallelic && replaceTerms) {
            throw new RuntimeException("[ERROR] Cannot use both --replace and --biallelic options at the same time");
        }
        if (biallelic) {
            obfuscateBiallelic();
        } else if (replaceTerms) {
            obfuscateByReplacement();
        } else if (noNot) {
            obfuscateByRemovingNotQueryTerms();
        } else {
            obfuscateParams();
        }
    }

    private boolean hasClinvarPathogenic(Phenopacket originalPhenopacket,
                                         Set<Variant> variantSet) {
        List<org.phenopackets.schema.v1.core.Variant> variants =  originalPhenopacket.getVariantsList();
        for (org.phenopackets.schema.v1.core.Variant v : variants) {
            Variant var = Variant.fromPhenopacketVariant(v);
            if (variantSet.contains(var)) {
                return true;
            }
        }
        return false;
    }


    private void outputByMoi() {
        ClinvarParser parser = new ClinvarParser(this.clinVarPath);
        Set<Variant> variantSet = parser.getVariantSet();
        for (java.io.File file: this.phenopacketFiles) {
            String phenopacketAbsolutePath = file.getAbsolutePath();
            PhenopacketObfuscator pobfuscator = new PhenopacketObfuscator(phenopacketAbsolutePath, this.ontology);
            Phenopacket originalPhenopacket = pobfuscator.getOriginal();
            String path2;
            if (pobfuscator.diseaseIsAutosomalRecessive()) {
                String basename = getNoNotObfuscatedBasename(file.getName());
                if (hasClinvarPathogenic(originalPhenopacket, variantSet)) {
                    path2 = String.format("%s%s%s", MOI_RECESSIVE_CLINVAR, File.separator, basename);
                } else {
                    path2 = String.format("%s%s%s", MOI_RECESSIVE_NO_CLINVAR, File.separator, basename);
                }
            } else if (pobfuscator.diseaseIsAutosomalDominant()) {
                String basename = getNoNotObfuscatedBasename(file.getName());
                if (hasClinvarPathogenic(originalPhenopacket, variantSet)) {
                    path2 = String.format("%s%s%s", MOI_DOMINANT_CLINVAR, File.separator, basename);
                } else {
                    path2 = String.format("%s%s%s", MOI_DOMINANT_NO_CLINVAR, File.separator, basename);
                }
            } else {
                System.out.println("[INFO] Skipping phenopacket " + file.getName());
                continue;
            }
            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(path2));
                bw.write(toJson(originalPhenopacket));
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void obfuscateByRemovingNotQueryTerms() {
        for (java.io.File file: this.phenopacketFiles) {
            String phenopacketAbsolutePath = file.getAbsolutePath();
            PhenopacketObfuscator pobfuscator = new PhenopacketObfuscator(phenopacketAbsolutePath, this.ontology);
            Phenopacket obfuscated = pobfuscator.getObfuscationWithNotTermsRemoved();
            String basename = getNoNotObfuscatedBasename(file.getName());
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

    /**
     * Transform e.g., Arora-2019-COG8-proband.json to Arora-2019-COG8-proband_biallelic_obfuscated.json
     * @param bname original basename
     * @return obfuscated basename
     */
    private String getNoNotObfuscatedBasename(String bname) {
        String[] A = getFileNameComponents(bname);
        return String.format("%s_nots_removed.%s", A[0],A[1]);
    }

    /**
     * Transform e.g., Arora-2019-COG8-proband.json to Arora-2019-COG8-proband_biallelic_obfuscated.json
     * @param bname original basename
     * @return obfuscated basename
     */
    private String getBiallelicObfuscatedBasename(String bname) {
        String[] A = getFileNameComponents(bname);
        return String.format("%s_biallelic_obfuscated.%s", A[0],A[1]);
    }



    private void obfuscateBiallelic() {
        String dir = "BIALLELIC_NON_OBFUSCATED";
        File directory = new File(dir);
        if (! directory.exists()) {
            directory.mkdir();
        }
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
                // Now copy oringal file to the NON_OBFUSCATED directory so we can compare
                String pathOrigFileCopy = String.format("%s%s%s", directory.getAbsolutePath(),File.separator,file.getName() );
                try {
                    java.nio.file.Files.copy(new File(phenopacketAbsolutePath).toPath(),
                            new File(pathOrigFileCopy).toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.COPY_ATTRIBUTES,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS);
                } catch(IOException e) {
                    e.printStackTrace();
                    System.err.println("Could not copy files to " + directory.getAbsolutePath());
                    System.exit(1);
                }
            }
        }
    }

    /**
     * Divide the file name up into the first part and then the suffix
     * @return 2-dimensional array with file name components.
     */
    private String [] getFileNameComponents(String bname){
        int i = bname.lastIndexOf('.');
        if (i < 0 ) {
            String e = String.format("Malformed phenopacket basename (no \".\"): %s",bname );
            throw new RuntimeException(e);
        }
        String[] A = new String[2];
        A[0] = bname.substring(0,i);
        A[1] = bname.substring(i+1);
        return A;
    }

    /**
     * Transform e.g., Arora-2019-COG8-proband.json to Arora-2019-COG8-proband_terms_replaced_obfuscated.json
     * @param bname original basename
     * @return obfuscated basename
     */
    private String getReplacementObfuscatedBasename(String bname) {
        String[] A = getFileNameComponents(bname);
        return String.format("%s_terms_replaced_obfuscated.%s", A[0],A[1]);
    }

    /**
     * Replace all of the original HPO terms by random terms but leave everything else unchanged.
     */
    private void obfuscateByReplacement() {
        for (java.io.File file: this.phenopacketFiles) {
            String phenopacketAbsolutePath = file.getAbsolutePath();
            PhenopacketObfuscator pobfuscator = new PhenopacketObfuscator(phenopacketAbsolutePath, this.ontology);
            Phenopacket obfuscated = pobfuscator.getObfuscationByReplacement();
            String basename = getReplacementObfuscatedBasename(file.getName());
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
                //System.out.println(++counter + ") "+ fileEntry.getName());
                this.phenopacketFiles.add(fileEntry);
                counter++;
            }
        }
        System.out.printf("[INFO] Added %d phenopackets.\n",counter);
    }

}
