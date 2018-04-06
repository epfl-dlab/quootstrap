# Quootstrap
This is the reference implementation of Quootstrap, as described in the paper:
> Dario Pavllo, Tiziano Piccardi, Robert West. *Quootstrap: Scalable Unsupervised Extraction of Quotation-Speaker Pairs from Large News Corpora via Bootstrapping*. In *Proceedings of the 12th International Conference on Web and Social Media (ICWSM),* 2018.

#### Abstract
We propose Quootstrap, a method for extracting quotations, as well as the names of the speakers who uttered them, from large news corpora. Whereas prior work has addressed this problem primarily with supervised machine learning, our approach follows a bootstrapping paradigm and is therefore fully unsupervised. It leverages the redundancy present in large news corpora, more precisely, the fact that the same quotation often appears across multiple news articles in slightly different contexts. Starting from a few seed patterns, such as ["Q", said S.], our method extracts a set of quotation-speaker pairs (Q,S), which are then used for discovering new patterns expressing the same quotations; the process is then repeated with the larger pattern set. Our algorithm is highly scalable, which we demonstrate by running it on the large ICWSM 2011 Spinn3r corpus. Validating our results against a crowdsourced ground truth, we obtain 90% precision at 40% recall using a single seed pattern, with significantly higher recall values for more frequently reported (and thus likely more interesting) quotations. Finally, we showcase the usefulness of our algorithm's output for computational social science by analyzing the sentiment expressed in our extracted quotations.

## Dataset
We release our dataset of quotations as a JSON-formatted file. This is the output of our algorithm on the ICWSM 2011 Spinn3r dataset, which spans one month (from January 13th, 2011 to February 14th, 2011) and contains relevant events such as the Egyptian protests, the Tunisian revolution, and the Super Bowl XLV. The collection consists of 170k quotation-speaker pairs.
For more information about the dataset (such as the row format), refer to the **"Exporting results"** section. The conditions for using the dataset are described in the **License** section.

> Download URL (25 MB compressed, 140 MB decompressed):
[https://TODO](https://TODO)

## How to run
Go to the **Release** section and download the .zip archive, which contains the executable `quootstrap.jar` as well as all necessary dependencies and configuration files. You can also find a convenient script `extraction_quotations.sh` that can be used to run the application on a Yarn cluster. The script runs this command:
```
spark-submit --jars spinn3r-client-3.4.05-edit.jar,stanford-corenlp-3.8.0.jar,jsoup-1.10.3.jar,guava-14.0.1.jar \
	--num-executors 8 \
	--executor-cores 38 \
	--driver-memory 192g \
	--executor-memory 192g \
	--conf "spark.yarn.executor.memoryOverhead=32768" \
	--class ch.epfl.dlab.quootstrap.QuotationExtraction \
	--master yarn \
	quootstrap.jar $1
```
where `$1` represents the log/output directory. After tuning the settings to suit your particular configuration, you can run the command as:
```
./extraction_quotations.sh outputDirectory
```

## Setup
To run our code, you need:
- Java 8
- Spark 1.6
- The [ICWSM 2011 Spinn3r dataset](http://www.icwsm.org/data/)
- Our dataset of people extracted from Freebase. You can download it from [https://TODO](https://TODO) .

The Spinn3r dataset must be converted to JSON format using the tool that we provide. You can find more details in the README in `src\main\java\ch\epfl\dlab\spinn3r\converter`. The reason for this requirement is that our format is more suitable for distributed processing, whereas the original dataset is stored in large non-splittable archives.

For reference, this is the command that we have used. It creates an output file `dataset.json`, stored on HDFS and split into 500 gzip-compressed chunks. Only news articles are kept, and duplicates are removed.
```
spark-submit \
    --jars spinn3r-client-3.4.05-edit.jar,stanford-corenlp-3.8.0.jar,jsoup-1.10.3.jar \
    --num-executors 8 \
    --executor-cores 38 \
    --driver-memory 128g \
    --executor-memory 128g \
    --conf "spark.yarn.executor.memoryOverhead=16384" \
    --class ch.epfl.dlab.spinn3r.converter.ProtoToJson --master yarn \
    quootstrap.jar /datasets/Spinn3r/icwsm2011/*-OTHER.tar.gz dataset.json \
    --compress=GzipCodec --partitions=500 --source-type=MAINSTREAM_NEWS --remove-duplicates
```

## How to build
Clone the repository and import it as an Eclipse project. All dependencies are downloaded through Maven. To build the application, generate a .jar file with all source files and run it as explained in the previous section. Alternatively, you can use Spark in local mode for experimenting. Additional instructions on how to extend the project with new functionalities (e.g. support for new datasets) are reported later.

## Configuration
The first configuration file is `config.properties`. The most important fields in order to get the application running are:
- `NEWS_DATASET_PATH=dataset.json` specifies the HDFS path of the Spinn3r news dataset, converted to JSON format.
- `PEOPLE_DATASET_PATH=names.ALL.UNAMBIGUOUS.tsv` specifies the HDFS path of the Freebase people list.
- `NUM_ITERATIONS=5` specifies the number of iterations of the extraction algorithm. Set it to 1 if you want to run the algorithm only on the seed patterns (iteration 0). A number of iterations between 3 and 5 is more than enough.
- `LANGUAGE_FILTER=en|uk` specifies the languages to select (as obtained by the Spinn3r dataset). Separate multiple languages by |.

The second configuration file is `seedPatterns.txt`, which, as the name suggests, contains the seed patterns that are used in the first iteration, one by line.

## Evaluation
If either `ENABLE_FINAL_EVALUATION` or `ENABLE_INTERMEDIATE_EVALUATION` are enabled in the configuration, the application produces a report by comparing the output against the ground truth. Note that this is a costly operation: if you don't need it, you are advised to disable it (or enable just the final evaluation).
For each iteration **X**, the following files are generated:
- `evaluationX.txt`: reports the precision and recall for each speaker in the ground truth, as well as aggregate statistics. The last section provides an estimate of the recall as a function of the redundancy (frequency) of a quotation.
```
speaker             precision           recall              num_extracted
Angela Merkel       0.9772727272727273  0.3049645390070922  44
Vladimir Putin      0.8372093023255814  0.5070422535211268  43
Guido Westerwelle   0.9846153846153847  0.6037735849056604  65
Mark Zuckerberg     0.9166666666666666  0.22916666666666666 12
Julian Assange      0.96875             0.29245283018867924 32
John McCain         0.8620689655172413  0.4830917874396135  116
Mohamed ElBaradei   0.9879518072289156  0.36607142857142855 83
Charlie Sheen       1.0                 0.14705882352941177 5
John Boehner        0.8282828282828283  0.5206349206349207  198
Mahmoud Ahmadinejad 0.9736842105263158  0.36633663366336633 38
Benjamin Netanyahu  0.9111111111111111  0.5256410256410257  90
Sarah Palin         0.8653846153846154  0.19480519480519481 52
Roger Federer       1.0                 0.3783783783783784  14
-----------------
Precision:  0.9015151515151515
Recall:     0.40180078784468204
F0.5 score: 0.7219413549039434
F1 score:   0.555858310626703
-----------------
Unique valid quotations in ground truth: 1778
Attributed and labeled correctly: 714
Not attributed: 1063
Attributed to the wrong person: 1
Attributed incorrectly: 77
-----------------
frequency   num_quotations  recall
1           1200            0.30833333333333335
2           259             0.5135135135135135
3           133             0.6240601503759399
4           54              0.6666666666666666
5           30              0.7
6           30              0.6333333333333333
7           12              0.5833333333333334
8           13              0.8461538461538461
9           11              0.6363636363636364
10          3               0.3333333333333333
11          6               0.5
12          6               0.8333333333333334
13          2               1.0
14          1               1.0
15          1               1.0
16          3               0.6666666666666666
18          1               1.0
19          1               1.0
20          1               1.0
21          1               0.0
23          2               1.0
29          1               1.0
33          1               1.0
38          1               1.0
43          2               1.0
44          2               1.0
```

- `errorsX.txt`: lists all wrong results, including lineage information if available (tuple confidence, patterns that extracted the tuple, contexts in which the quotation occurs, frequency of the quotation)
```
Expected Optional.absent(); Got Optional.of([John, McCain]);
thats not mine to decide;
Lineage: {
    Confidence: 1.0,
    Patterns: [["Senator $S $* $* $* $* $* . $Q": 1.0], ["Senator $S $* $* $* $* $* . $Q": 1.0]],
    Sentences: [
        <(1296861701060604421,2)> Mullen was asked about a possible freeze to the vast U.S. military
        and economic aid to Egypt , a move that Republican Senator John McCain has said is under
        consideration . [thats not mine to decide] , said Mullen . *QUOT*,
        <(1296834853069533764,1)> Mullen was asked about a possible freeze to the vast US military
        and economic aid to Egypt , a move that Republican Senator John McCain has said is under
        consideration . [thats not mine to decide] , said Mullen . *QUOT*]}
```
```
Expected Optional.of([Sarah, Palin]); Got Optional.absent();
went right to the answering machine;
Redundancy: 3
```

By enabling `DEBUG_DUMP_PATTERNS`, three additional files will be generated at each iteration:

- `nextPatternsPreClusteringX.txt` contains the raw patterns that have been discovered in the current iteration, prior to the clustering/inference step.
```
$Q , said senior $S ,
$Q , said senior Caltech research fellow $S .
$Q , said senior technology consultant , $S ,
$Q , said series creator $S .
$Q , said show creator $S .
$Q , said show executive producer $S ,
$Q , said son and actor $S to
$Q , said spokesman $S .
$Q , said state Attorney General $S .
$Q , said state Rep. $S ,
$Q , said state Sen. $S ,
$Q , said team co-owner $S .
$Q , said team president $S .
$Q , said the $S ,
```
- `nextPatternsPostClusteringX.txt`: contains the patterns after the clustering step, along with their estimated confidence metric (i.e. precision).
- `nextPatternsX.txt`: contains the patterns that will be used in the next iteration, i.e. those that have a confidence value above `PATTERN_CONFIDENCE_THRESHOLD`.
```
["$Q , added $S .": 0.751151183913851]
["$Q , attorney $S said": 1.0]
["$Q , center $S said": 0.8050782191354693]
["$Q , chairman $S said": 1.0]
["$Q , chief executive $S said": 1.0]
["$Q , co-founder $S said": 1.0]
["$Q , coach $S said": 0.9748467635486808]
["$Q , coach $S told": 1.0]
["$Q , commissioner $S said": 1.0]
["$Q , cornerback $S said": 0.8759333380969695]
["$Q , defensive $* $S said": 0.7539409834577577]
["$Q , director $S said": 1.0]
["$Q , explained $S .": 1.0]
["$Q , former $* $* $S said": 1.0]
```

Note that a valid pattern:
- Must contain exactly one speaker `$S` and one quotation `$Q`. They can both match a variable number of contiguous tokens.
- Must not start or end with `$S` or `$*` (to avoid side effects).
- Can contain any number of `$*` placeholders, which are matched to exactly one token.

For instance, if we have the pattern `$Q said $* $* $S .` and the sentence `"We are called here to mourn an unspeakable act of violence" said House Speaker John Boehner.`, the resulting tuple is (`We are called here to mourn an unspeakable act of violence`, `John Boehner`).

## Exporting results
You can export the retrieved quotation-speaker pairs by setting `EXPORT_RESULTS` to `true` and setting the HDFS output PATH on `EXPORT_PATH`. Again, this is a costly operation, so we recommend you to disable it if not needed. The results are saved as a HDFS text file formatted in JSON, with one record per line. For each record, the full quotation is exported, as well as the full name of the speaker (as reported in the article), his/her unique Freebase ID, the confidence value of the tuple, and the occurrences in which the quotation was found. As for the latter, we report the article ID, an incremental offset within the article (which is useful for linking together split quotations), the pattern that extracted the tuple along with its confidence, the website, and the date the article appeared.
```json
{
  "quotation": "Now, going forward, this moment of volatility has to be turned into a moment of promise. The United States has a close partnership with Egypt and we've cooperated on many issues, including working together to advance a more peaceful region. But we've also been clear that there must be reform -- political, social and economic reforms that meet aspirations of the Egyptian people,",
  "canonicalQuotation": "now going forward this moment of volatility has to be turned into a moment of promise the united states has a close partnership with egypt and weve cooperated on many issues including working together to advance a more peaceful region but weve also been clear that there must be reform political social and economic reforms that meet aspirations of the egyptian people",
  "speaker": "Barack Obama",
  "speakerID": "http://rdf.freebase.com/ns/m.02mjmr",
  "confidence": 1,
  "occurrences": [
    {
      "articleUID": "1296259645046202892",
      "articleOffset": 4,
      "extractedBy": "$Q , $S said",
      "patternConfidence": 1,
      "quotation": "This moment of volatility has to be turned into a moment of promise,",
      "website": "www.daytondailynews.com",
      "date": "2011-01-28T23:58:06Z"
    },
    {
      "articleUID": "1296272315046514180",
      "articleOffset": 0,
      "extractedBy": "$Q , $S said",
      "patternConfidence": 1,
      "quotation": "This moment of volatility has to be turned into a moment of promise,",
      "website": "www.guardian.co.uk",
      "date": "2011-01-29T00:36:25Z"
    },
    {
      "articleUID": "1296282767057311234",
      "articleOffset": 7,
      "extractedBy": "$Q , $S said",
      "patternConfidence": 1,
      "quotation": "Going forward, this moment of volatility has to be turned into a moment of promise,",
      "website": "www.theledger.com",
      "date": "2011-01-29T05:19:02Z"
    }
  ]
}
```
Remarks:
- *articleUID* is a string, although it represents a 64-bit integer (because the JSON standard does not allow for enough precision).
- *articleOffset* can have gaps (but quotations are guaranteed to be ordered correctly).
- *canonicalQuotation* is the internal representation of a particular quotation, used for pattern matching purposes. The string is converted to lower case and punctuation marks are removed.
- As in the example above, the full quotation might differ from the one(s) found in *occurrences* due to the quotation merging mechanism. We always report the longest (and most likely useful) quotation when multiple choices are possible.

## Adding support for new datasets/formats
If you want to add support for other datasets/formats, you can provide a concrete implementation for the Java interface `DatasetLoader` and specify its full class name in the `NEWS_DATASET_LOADER` field of the configuration. For each article, you must supply a unique ID (int64/long), the website in which it can be found, and its content in tokenized format, i.e. as a list of strings. We provide an implementation for our JSON Spinn3r dataset in `ch.epfl.dlab.quootstrap.Spinn3rDatasetLoader`.

## Replacing the tokenizer
If, for any reason (e.g. license, language other than English), you do not want to depend on Stanford PTBTokenizer, you can provide your own implementation of the `ch.epfl.dlab.spinn3r.Tokenizer` interface. You only have to implement two methods: `tokenize` and `untokenize`. Tokenization is one of the least critical steps in our pipeline, and does not impact the final result significantly.

## License
We release our work under the MIT license. Third-party components, such as Stanford CoreNLP, are subject to their respective licenses.

If you use our code and/or data in your research, please cite our paper:
```
@inproceedings{quootstrap2018,
  title={Quootstrap: Scalable Unsupervised Extraction of Quotation-Speaker Pairs from Large News Corpora via Bootstrapping},
  author={Pavllo, Dario and Piccardi, Tiziano and West, Robert},
  booktitle={Proceedings of the 12th International Conference on Web and Social Media (ICWSM)},
  year={2018}
}
```
