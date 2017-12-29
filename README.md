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
	quotation.jar
```

## How to build
Clone the repository and import it as an Eclipse project. All dependencies are downloaded through Maven. To build the application, generate a .jar file with all source files and run it as explained in the previous section. Alternatively, you can use Spark in local mode for experimenting.

## Configuration
The first configuration file is `config.properties`. The most important fields in order to get the application running are:
- `NEWS_DATASET_PATH=dataset.json` specifies the path of the Spinn3r news dataset, converted to JSON format. A copy can be found in `hdfs://user/pavllo/dataset.json`.
- `PEOPLE_DATASET_PATH=names.ALL.UNAMBIGUOUS.tsv` specifies the path of the FreeBase people list. A copy can be found in `hdfs://user/pavllo/names.ALL.UNAMBIGUOUS.tsv`.
- `NUM_ITERATIONS=5` specifies the number of iterations of the extraction algorithm. Set it to 1 if you want to run the algorithm only on the seed patterns (iteration 0). A number of iterations between 3 and 5 is more than enough.
- `LANGUAGE_FILTER=en|uk` specifies the languages to select (as obtained by the Spinn3r dataset). Separate multiple languages by |.

The second configuration file is `seedPatterns.txt`, which, as the name suggests, contains the seed patterns that are used in the first iteration.

## Evaluation
If either `ENABLE_FINAL_EVALUATION` or `ENABLE_INTERMEDIATE_EVALUATION` are enabled in the configuration, the application will produce a report by comparing the output against the ground truth. For each iteration **X**, the following files are generated:
- `evaluationX.txt`: reports the precision and recall for each speaker in the ground truth, as well as aggregate statistics.
```
speaker             precision           recall              num_extracted
Angela Merkel       0.921875            0.4013605442176871	64
Vladimir Putin      0.7272727272727273  0.5263157894736842	55
Guido Westerwelle   0.9315068493150684  0.5964912280701754	73
Mark Zuckerberg     0.8095238095238095  0.3148148148148148	21
Julian Assange      0.9069767441860465  0.33620689655172414	43
John McCain         0.8407079646017699  0.39094650205761317	113
Mohamed ElBaradei   0.8476190476190476  0.3236363636363636	105
Charlie Sheen       0.7647058823529411  0.2549019607843137	17
John Boehner        0.8326996197718631  0.47300215982721383	263
Mahmoud Ahmadinejad 0.7954545454545454  0.32710280373831774	44
Benjamin Netanyahu  0.782258064516129   0.5078534031413613	124
Sarah Palin         0.734375            0.16433566433566432	64
Roger Federer       0.6875              0.3235294117647059	16
-----------------
Precision: 0.8273453093812375
Recall: 0.38433008808530367
F0.5 score: 0.6723438767234386
F1 score: 0.5248496359607472
-----------------
Attributed and labeled correctly: 829
Not attributed: 1328
Attributed to the wrong person: 1
Attributed incorrectly: 172
```

- `errorsX.txt`: lists all wrong results.
```
Expected Optional.of([Sarah, Palin]); Got Optional.absent(); certainly i agree with the idea of being civil
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