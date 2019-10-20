####################################
Phenobfuscator: phenopacketObfuscator
####################################


This app is intended to simplify the generation of modified phenopackets
for testing/validation of algorithms for genomic diagnostics of Mendelian 
disease. The app takes as input the path of a directory that contains one
or multiple phenopacket case reports. It alters the genotypes of the phenopackets and
writes the altered phenopackets to a new directory (*obfuscated*).


There are several modes to run this app in. All modes start with a collection of phenopackets
in some directory. All phenopackets are read, and obfuscated phenopackets are output to
a new directory.

Biallelic
~~~~~~~~~

For lack of a better name. This mode filter out phenopackets from cases that are autosomal recessive
diseases (the assumption is that these cases have two pathogenic allelics). All other phenopackets
are skipped. The only difference is that phenopackets with one homozygous variant now have one heterozygous variant.
Phenopackets with compound het variants are output with one of the two variants (at random).

To run this mode, enter ::

    java -jar phenobfuscator.jar -p /home/user/wherever/ppacket \\
        --hpo /home/user/wherever/data/hp.obo \\
        --biallelic

Adjust the paths according to your system! ``/home/user/wherever/ppacket`` is a directory that contains
at least one phenopacket (assumption: version 1.0.0RC3 or better).



If the `--biallelic` flag is used, then the app will only output
phenopackets that originally had two pathogenic alleles (i.e., autosomal recessive disease).
Instead of removing the variants entirely, it will output only a single
heterozygous variant. If the original phenopacket had two compound heterozygous
variants, it will choose one of them at random. If the original phenopacket
had one homozygous variant, it will output the same allele as
a heterozygous  variant.

Replace
~~~~~~~

This mode replaces all HPO terms in the phenopacket with random HPO terms. Negation is maintained (i.e.,
any NOT term is replaced by a random NOT term). Nothing else is changed. To run this mode, enter ::


    java -jar phenobfuscator.jar -p /home/user/wherever/ppacket \\
        --hpo /home/user/wherever/data/hp.obo \\
        --replace


Not
~~~

This mode is intended to be used to test the effect of negated (excluded) disease annotations. Some diseases in the
HPOA have negative annotations, meaning that they are not characterized by a certain HPO feature. Often there are other
similar diseases that have the feature. LIRICAL exploits these to weight the differential diagnosis if the queries
explicitly negate the term in question. This obfuscation removes the negated query terms but otherwise does not
change anything.  To run this mode, enter ::



    java -jar phenobfuscator.jar -p /home/user/wherever/ppacket \\
        --hpo /home/user/wherever/data/hp.obo \\
        --no_not



Parameterized
~~~~~~~~~~~~~

This mode ingests and outputs all phenopackets. There are a number of parameters that determine the
behavior of the obfuscation.


+----------------------+--------------------------------------------------------+
| Option               | Explanation                                            |
+======================+========================================================+
| --imprecision        | replace HPO terms by a parent term                     |
+----------------------+--------------------------------------------------------+
| --double_imprecision | replace HPO terms by a grandparent term                |
+----------------------+--------------------------------------------------------+
| --n_noise            | number of noise (random) terms to add                  |
+----------------------+--------------------------------------------------------+
| --match_noise        | add equal number of noise terms as original number     |
+----------------------+--------------------------------------------------------+
| --n_alleles          | number of alleles to remove  (0, 1 or 2)               |
+----------------------+--------------------------------------------------------+


These options can be combined, but it is not possible to use both
``--impression`` and ``--double_imprecision`` options at the same time, and it is not possible to
use both ``--match_noise`` and ``--n_noise options`` at the same time.



Other options
~~~~~~~~~~~~~

The ``--out`` option controls the name of the output directory (default = "obfuscated").
The **required** ``--hpo`` option indicates the path to the Human Phenotype Ontology obo file.


Output directories
~~~~~~~~~~~~~~~~~~
Running PhenopacketObfuscator as follows will output a series of directories. ::

    java -jar phenobfuscator.jar --output_all_obfuscations

The following categories can be used to assess the influence of various kinds of noise

* ALLTERMS_RANDOMIZED.
All HPO terms (positive and negative) are replaced by random terms
* NOISE_2
This uses the constructor ``PhenopacketObfuscator(phenopacketAbsolutePath, this.ontology, n_alleles, imprecision, double_imprecision, noise, matchNoise);``
with
  -- n_alleles = 0; (do not obfuscate pathogenic alleles)
  -- imprecision = false; (do not replace terms by parents)
  -- double_imprecision = false; (do not replace terms by grandparents)
  -- matchNoise = false; (do not add an equal number of noise terms)
  -- noise = 2; (add two noise terms -- i.e., random HPO terms)
