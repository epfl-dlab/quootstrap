# Extracting quotations from a large news corpus
## How to run
Go to the [Release](https://github.com/epfl-dlab/quotation-extraction/releases) section and download the .zip archive, which contains the executable `quotation.jar` as well as all necessary dependencies and configuration files. You can also find a convenient script `extraction_quotations.sh` that can be used to run the application on the DLAB cluster. The script essentially runs this command:
```
spark-submit --jars spinn3r-client-3.4.05-edit.jar,stanford-corenlp-3.8.0.jar,jsoup-1.10.3.jar,guava-14.0.1.jar \
	--num-executors 8 \
	--executor-cores 38 \
	--driver-memory 192g \
	--executor-memory 192g \
	--conf "spark.yarn.executor.memoryOverhead=32768" \
	--class ch.epfl.dlab.quotation.QuotationExtraction \
	--master yarn \
	quotation.jar $1
```
where `$1` represents the log/output directory. You can run the command on a shell as:
```
./extraction_quotations.sh outputDirectory
```

## How to build
Clone the repository and import it as an Eclipse project. All dependencies are downloaded through Maven. To build the application, generate a .jar file with all source files and run it as explained in the previous section. Alternatively, you can use Spark in local mode for experimenting. Additional instructions on how to extend the project with new functionalities (e.g. support for new datasets) are reported in one of the following sections.

## Configuration
The first configuration file is `config.properties`. The most important fields in order to get the application running are:
- `NEWS_DATASET_PATH=dataset.json` specifies the path of the Spinn3r news dataset, converted to JSON format. A copy can be found in `hdfs://user/pavllo/dataset.json`.
- `PEOPLE_DATASET_PATH=names.ALL.UNAMBIGUOUS.tsv` specifies the path of the FreeBase people list. A copy can be found in `hdfs://user/pavllo/names.ALL.UNAMBIGUOUS.tsv`.
- `NUM_ITERATIONS=5` specifies the number of iterations of the extraction algorithm. Set it to 1 if you want to run the algorithm only on the seed patterns (iteration 0). A number of iterations between 3 and 5 is more than enough.
- `LANGUAGE_FILTER=en|uk` specifies the languages to select (as obtained by the Spinn3r dataset). Separate multiple languages by |.

The second configuration file is `seedPatterns.txt`, which, as the name suggests, contains the seed patterns that are used in the first iteration, one by line.

## Evaluation
If either `ENABLE_FINAL_EVALUATION` or `ENABLE_INTERMEDIATE_EVALUATION` are enabled in the configuration, the application produces a report by comparing the output against the ground truth. Note that this is a costly operation: if you don't need it, you are advised to disable it (or enable just the final evaluation).
For each iteration **X**, the following files are generated:
- `evaluationX.txt`: reports the precision and recall for each speaker in the ground truth, as well as aggregate statistics. The last section provides an estimate of the recall as a function of the redundancy (frequency) of a quotation.
```
speaker             precision           recall              num_extracted
Angela Merkel       0.9090909090909091  0.2898550724637681  44
Vladimir Putin      0.7954545454545454  0.5072463768115942  44
Guido Westerwelle   0.9230769230769231  0.5882352941176471  65
Mark Zuckerberg     0.8333333333333334  0.2127659574468085  12
Julian Assange      0.8484848484848485  0.27450980392156865 33
John McCain         0.7787610619469026  0.4467005076142132  113
Mohamed ElBaradei   0.9397590361445783  0.35454545454545455 83
Charlie Sheen       0.8333333333333334  0.14705882352941177 6
John Boehner        0.8080808080808081  0.5144694533762058  198
Mahmoud Ahmadinejad 0.825               0.34375             40
Benjamin Netanyahu  0.8241758241758241  0.5067567567567568  91
Sarah Palin         0.7307692307692307  0.16964285714285715 52
Roger Federer       0.6428571428571429  0.28125             14
-----------------
Precision:  0.8289308176100629
Recall:     0.38313953488372093
F0.5 score: 0.6724489795918367
F1 score:   0.5240556660039761
-----------------
Unique valid quotations in ground truth:    1721
Attributed and labeled correctly:           659
Not attributed:                             1061
Attributed to the wrong person:             1
Attributed incorrectly:                     135
-----------------
frequency   num_quotations  recall
1           1130            0.2761061946902655
2           267             0.5056179775280899
3           132             0.6060606060606061
4           57              0.6666666666666666
5           29              0.7241379310344828
6           29              0.5862068965517241
7           16              0.6875
8           14              0.7857142857142857
9           9               0.5555555555555556
10          4               0.5
11          6               0.5
12          7               0.8571428571428571
13          1               1.0
14          1               1.0
15          1               1.0
16          3               0.6666666666666666
17          1               1.0
18          1               1.0
20          2               1.0
21          1               0.0
23          1               1.0
24          1               1.0
30          1               1.0
36          1               1.0
38          1               1.0
43          1               1.0
44          2               1.0
46          1               1.0
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
You can export the retrieved quotation-speaker pairs by setting `EXPORT_RESULTS` to `true` and setting the HDFS output PATH on `EXPORT_PATH`. Again, this is a costly operation, so we recommend you to disable it if it's not needed. The results are saved as a HDFS text file formatted in JSON, with one record per line. For each record, the full quotation is exported, as well as the full name of the speaker (as reported in the article), his/her unique Freebase ID, the confidence value of the tuple, and the occurrences in which the quotation was found. As for the latter, we report the article ID, an incremental offset within the article (which is useful for linking together split quotations), the pattern that extracted the tuple and its confidence.
```json
{
    "quotation": "Now, going forward, this moment of volatility has to be turned into a moment of promise. The United States has a close partnership with Egypt and we've cooperated on many issues, including working together to advance a more peaceful region. But we've also been clear that there must be reform -- political, social and economic reforms that meet aspirations of the Egyptian people,",
    "speaker": "Barack Obama",
    "speakerID": "http://rdf.freebase.com/ns/m.02mjmr",
    "confidence": 1.0,
    "occurrences": [
        {"articleUID": "1296272315046514180", "articleOffset": 0, "extractedBy": "$Q , $S said", "patternConfidence": 1.0},
        {"articleUID": "1296259645046202892", "articleOffset": 4, "extractedBy": "$Q , $S said", "patternConfidence": 1.0},
        {"articleUID": "1296282770057311272", "articleOffset": 7, "extractedBy": "$Q , $S said", "patternConfidence" :1.0}
    ]
}

```
Note that (1) the articleUID is a string (because the JSON standard does not allow for 64-bit integers) and (2) the articleOffset can have gaps (but quotations are guaranteed to be ordered correctly).

## Adding support for new datasets/formats
If you want to add support for other datasets/formats, you can provide a concrete implementation for the Java interface `DatasetLoader` and specify its full class name in the `NEWS_DATASET_LOADER` field of the configuration. For each article, you must supply a unique ID (int64/long) and its content in tokenized format, i.e. as a list of strings. We provide an implementation for our JSON Spinn3r dataset in `ch.epfl.dlab.quotation.Spinn3rDatasetLoader`.
