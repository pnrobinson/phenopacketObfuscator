# Phenofuscator: phenopacketObfuscator

This app is intended to simplify the generation of modified phenopackets
for testing/validation of algorithms for genomic diagnostics of Mendelian 
disease. The app takes as input the path of a directory that contains one
or multiple phenopacket case reports. It alters the genotypes of the phenopackets and
writes the altered phenopackets to a new directory (*obfuscated*).

For instance, imagine we have two phenoppackets with he following genotypes

1. 
```
"genes": [{
    "id": "ENTREZ:2200",
    "symbol": "FBN1"
  }],
  "variants": [{
    "vcfAllele": {
      "genomeAssembly": "GRCh37",
      "chr": "15",
      "pos": 48808561,
      "ref": "T",
      "alt": "A"
    },
    "zygosity": {
      "id": "GENO:0000135",
      "label": "heterozygous"
    }
  }],
  ```
and

2.
```aidl
 "genes": [{
    "id": "ENTREZ:583",
    "symbol": "BBS2"
  }],
  "variants": [{
    "vcfAllele": {
      "genomeAssembly": "GRCh37",
      "chr": "16",
      "pos": 56530925,
      "ref": "G",
      "alt": "A"
    },
    "zygosity": {
      "id": "GENO:0000135",
      "label": "heterozygous"
    }
  }, {
    "vcfAllele": {
      "genomeAssembly": "GRCh37",
      "chr": "16",
      "pos": 56519631,
      "ref": "A",
      "alt": "T"
    },
    "zygosity": {
      "id": "GENO:0000135",
      "label": "heterozygous"
    }
  }],
```
  
By default, the app will remove the variants entirely.

If the `--biallelic` flag is used, then the app will only output
phenopackets that originally had two pathogenic alleles (i.e., autosomal recessive disease).
Instead of removing the variants entirely, it will output only a single
heterozygous variant. If the original phenopacket had two compound heterozygous
variants, it will choose one of them at random. If the original phenopacket
had one homozygous variant, it will output the same allele as
a heterozygous  variant.