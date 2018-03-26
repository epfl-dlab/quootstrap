# ProtoStream to JSON Converter (ProtoToJson)
This utility converts Spinn3r ProtoBuffer archives to JSON format, and provides some additional features such as tokenization and HTML cleaning.

The main disadvantage of Spinn3r's dataset is that it is compressed as multiple .tar.gz archives, rendering parallel processing difficult. Moreover, its use requires Spinn3r's proprietary library. The aim of this library is to convert the dataset to a format/file layout more suitable for big data processing (e.g. using Spark), as well as to prepare the content for NLP tasks.

The library is written in Java and is intended to be used with Spark 1.6.

# Usage
The utility can be launched either normally (by specifying the master as a command-line argument) or via spark-submit. The entry class is ``ch.epfl.dlab.spinn3r.converter.ProtoToJson``. It takes the following arguments:

#### Mandatory
- ``InputPath``: the input file(s) to convert. You can specify a Hadoop/HDFS path with wildcards or multiple files (in fact, you are encouraged to do so in order to increase parallelism).
- ``OutputPath``: the HDFS output path.
#### Optional
- ``--master=<master>``: specify the master to use. This attribute is not necessary if spark-submit is used (as you can specify the master in spark-submit).
- ``--sample=<sample>``: specify a fraction of the data to sample (e.g. 0.1). Useful for local processing.
- ``--merge``: merge HDFS output files into one single file (useful if the application is run locally).
- ``--partitions=<n>``: repartition the data prior to saving (or, in other words, the number of chunks to save). This attribute is useful if a non-splittable compression codec is used.
- ``--compress=<codec>``: compress the output chunks using the given codec from **org.apache.hadoop.io.compress** . Suitable choices are GzipCodec, Lz4Codec, BZip2Codec, SnappyCodec. See below.
- ``--source-type=<type(s)>``: the source type(s) to extract from Spinn3r's dataset. Separate with a semicolon (;). For instance, **MAINSTREAM_NEWS** corresponds to news articles.
- ``--clean=<true/false>``: clean HTML tags (see below). Default: true.
- ``--tokenize=<true/false>``: tokenize the output using Stanford PTBTokenizer. Default: true.
- ``--remove-duplicates``: remove articles with duplicate contents (costly operation).

#### Examples
```
ProtoToJson dir/*.tar.gz out.json --master=local[*] --sample=0.1 --merge
```
```
ProtoToJson dir/*.tar.gz out.json --master=yarn --partitions=1000 --compress=GzipCodec
```

# Compression
Since the dataset is very large and JSON format is highly redundant, compression is expected to reduce the output size, as well as to increase I/O speed (at the cost of a slightly increased CPU usage). Some codecs are discussed here, along with their advantages/disadvantages:
- **BZip2Codec**: excellent compression ratio, but very slow (and therefore, its use is not recommended). Splittable. Some decompressor implementations are bugged and crash when more than one core per executor is used (as of Hadoop 2.7).
- **GzipCodec**: good compression ratio and speed. Non-splittable, and for this reason it is a good idea to repartition the data.
- **Lz4Codec**: low compression ratio, but excellent speed. Non-splittable. Requires native Hadoop library.

# HTML cleanup
The dataset contains raw HTML code from web pages, thereby rendering some NLP tasks very difficult. In an attempt to simplify the job, the content is cleaned in the following way:
- The following tags are whitelisted: "a", "b", "blockquote", "br", "caption",
			"cite", "code", "col", "colgroup", "dd", "div", "dl", "dt", "em", "h1", "h2",
			"h3", "h4", "h5", "h6", "i", "center", "font", "abbr", "li", "ol", "p", "pre",
			"q", "small", "span", "strike", "strong", "sub", "sup", "table", "tbody", "td",
			"tfoot", "th", "thead", "tr", "u", "ul".
This means that all other tags are removed entirely (both the tags and their content). This should get rid of scripts, ads, forms, images, and other stuff that is not needed for text processing.
- Of the whitelisted tags, the following are preserved: "a", "blockquote", "br", "caption", "cite",
			"h1", "h2", "h3", "h4", "h5", "h6", "p", "q". All other tags not included in this list are removed, but their content is kept. This step should remove all structural elements (e.g. <div>) without affecting the content. The tags in the list are retained (along with their attributes) because they are relevant for some tasks.

Of course, these lists can be modified in ``ch.epfl.dlab.spinn3r.converter.EntryWrapperBuilder``.

# Tokenization
The output is tokenized using Stanford PTBTokenizer ( https://nlp.stanford.edu/software/tokenizer.shtml ). HTML tags are kept as single tokens, and can be easily detected or removed. For example:
```
This is <a href="https://www.google.com">Google</a>.
```
is tokenized as
```
[This] [is] [<a href="https://www.google.com">] [Google] [</a>] [.]
```

With regard to quotations, the sentence
```
"Hi," said John. «Hi», Mark replied.
```
would be tokenized as
```
`` Hi , '' said John . `` Hi '' , Mark replied .
```

If you don't need the tokenized output, you can simply join the tokens with a whitespace (although the result is not always equivalent to the original string), use the method `ptb2Text` of PTBTokenizer, or disable tokenization entirely.

# Remarks
- There is a dependency conflict issue with Spark 1.6. Spark includes Protobuf 2.5.0, whereas Spinn3r's library requires Protobuf 2.3.0. There is no way to make the two co-exist, and therefore the only solution was to modify the bytecode of Spinn3r's library and rename all references to **com.google** -> **com.gxxgle**. This issue is not present with Spark 2.1 .
- The original Spinn3r dataset includes two content types for each article: **content**, which contains the entire HTML web page of the article, and **contentExtract**, which in theory contains only the portion corresponding to the article. Only the latter is converted (and the former is discarded). In practice, the extraction mechanism is not perfect, and this should be kept in mind. Nonetheless, working with the whole HTML page would be a very challenging task.


# How to run the utility on a cluster
Upload ``ProtoToJson.jar``, ``spinn3r-client-3.4.05-edit.jar``, ``stanford-corenlp-3.8.0.jar``, ``jsoup-1.10.3.jar`` (you can find them in the Release section on GitHub) to the same directory. Then, run **spark-submit** and specify ``ch.epfl.dlab.spinn3r.converter.ProtoToJson`` as entry point.

For example, the following command converts all news articles, and saves them as 1000 chunks of Gzip-compressed files (which is convenient, as Gzip is non-splittable). It is important to allocate enough memory for the task.
```
spark-submit \
    --jars spinn3r-client-3.4.05-edit.jar,stanford-corenlp-3.8.0.jar,jsoup-1.10.3.jar \
    --num-executors 4 \
    --executor-cores 24 \
    --driver-memory 4g \
    --executor-memory 8g \
    --conf "spark.yarn.executor.memoryOverhead=2048" \
    --class ch.epfl.dlab.spinn3r.converter.ProtoToJson --master yarn \
    ProtoToJson.jar /datasets/Spinn3r/icwsm2011/*-OTHER.tar.gz dataset.json \
    --compress=GzipCodec --partitions=1000 \
    --source-type=MAINSTREAM_NEWS --remove-duplicates
```

# Sample JSON document (converted)
```
  
   "source":{  
      "url":"http://bizjournals.com/boston/news/2011/01/13/evergreen-solar-gets-new-cfo.html?ana=RSS&s=article_search",
      "title":"Seattle Business News - Local Seattle News | The Puget Sound Business Journal",
      "language":"en",
      "description":"View Breaking Local News Headlines in Seattle from the Puget Sound Business Journal. Access business resources, company profiles, business advice columns, local jobs and more.",
      "lastPosted":"",
      "lastPublished":"2010-12-22T19:41:32Z",
      "dateFound":"2008-03-11T18:26:10Z",
      "publisherType":"MAINSTREAM_NEWS"
   },
   "feed":{  
      "url":"http://feeds.bizjournals.com/bizj_boston",
      "title":"Boston Business News - Local Boston News | Boston Business Journal",
      "language":"en",
      "lastPosted":"2011-01-13T05:00:00Z",
      "lastPublished":"2011-01-13T21:16:42Z",
      "dateFound":"2009-07-10T18:11:04Z",
      "channelUrl":"http://www.bizjournals.com/rss/feed/daily/boston"
   },
   "feedEntry":{  
      "identifier":"1294953538155058195",
      "url":"http://bizjournals.com/boston/news/2011/01/13/evergreen-solar-gets-new-cfo.html?ana=RSS&s=article_search",
      "title":"Evergreen Solar gets new CFO",
      "language":"en",
      "authorName":"",
      "authorEmail":"",
      "lastPublished":"2011-01-13T05:00:00Z",
      "dateFound":"2011-01-13T05:00:00Z",
      "tokenizedContent":[  
         "Evergreen",
         "Solar",
         "Inc.",
        [...]
         "</a>",
         "<a>",
         "</a>"
      ]
   },
   "permalinkEntry":{  
      "identifier":"1294953538155058195",
      "url":"http://bizjournals.com/boston/news/2011/01/13/evergreen-solar-gets-new-cfo.html?ana=RSS&s=article_search",
      "title":"Evergreen Solar gets new CFO | Boston Business Journal",
      "language":"en",
      "authorName":"",
      "authorEmail":"",
      "lastPublished":"2011-01-13T05:00:00Z",
      "dateFound":"2011-01-13T21:18:58Z",
      "tokenizedContent":[  
         "<h4>",
         "Boston",
         "Business",
         "Journal",
         "-",
         "by",
         [...]
         "account",
         "from",
         "Boston",
         "Business",
         "Journal",
         ":",
         "</p>"
      ]
   }
}
```