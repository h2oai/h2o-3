job.titles.path = "https://raw.githubusercontent.com/h2oai/sparkling-water/rel-1.6/examples/smalldata/craigslistJobTitles.csv"

job.titles <- h2o.importFile(job.titles.path, destination_frame = "jobtitles",
                             col.names = c("category", "jobtitle"), col.types = c("Enum", "String"), header = TRUE)

STOP_WORDS = c("ax","i","you","edu","s","t","m","subject","can","lines","re","what",
               "there","all","we","one","the","a","an","of","or","in","for","by","on",
               "but","is","in","a","not","with","as","was","if","they","are","this","and","it","have",
               "from","at","my","be","by","not","that","to","from","com","org","like","likes","so")

tokenize <- function(sentences, stop.words = STOP_WORDS) {
    tokenized <- h2o.tokenize(sentences, "\\\\W+")

    # convert to lower case
    tokenized.lower <- h2o.tolower(tokenized)
    # remove short words (less than 2 characters)
    tokenized.lenghts <- h2o.nchar(tokenized.lower)
    tokenized.filtered <- tokenized.lower[is.na(tokenized.lenghts) || tokenized.lenghts >= 2,]
    # remove words that contain numbers
    tokenized.words <- tokenized.filtered[h2o.grep("[0-9]", tokenized.filtered, invert = TRUE, output.logical = TRUE),]

    # remove stop words
    tokenized.words[is.na(tokenized.words) || (! tokenized.words %in% STOP_WORDS),]
}

aggregate2vecs <- function(word.vectors, words) {
    word.vectors$C0 <- ! is.na(word.vectors$C1)
    word.vectors[word.vectors$C0 != 1,] <- 0.0

    word.sums <- h2o.cumsum(word.vectors)

    r <- word.sums[is.na(words$C1),]
    if (nrow(r) > 1) {
        r[2:nrow(r),] <- r[2:nrow(r),] - r[1:(nrow(r)-1),]
    }
    r / r$C0
}

predict <- function(job.title, w2v, gbm) {
    words <- tokenize(as.character(as.h2o(job.title)))
    word.vectors <- h2o.transform(w2v, words)
    job.title.vec <- aggregate2vecs(word.vectors, words)
    h2o.predict(gbm, job.title.vec)
}

print("Break job titles into sequence of words")
words <- tokenize(job.titles$jobtitle)

print("Build word2vec model")
w2v.model <- h2o.word2vec(words, sent_sample_rate = 0, epochs = 10)

print("Sanity check - find synonyms for the word 'teacher'")
print(h2o.findSynonyms(w2v.model, "teacher", count = 5))

print("Calculate a vector for each job title")
word.vectors <- h2o.transform(w2v.model, words)
job.title.vecs <- aggregate2vecs(word.vectors, words)

print("Prepare training&validation data (keep only job titles made of known words)")
valid.job.titles <- ! is.na(job.title.vecs$C0)
data <- h2o.cbind(job.titles[valid.job.titles, "category"], job.title.vecs[valid.job.titles, ])
data.split <- h2o.splitFrame(data, ratios = 0.8)

print("Build a basic GBM model")
gbm.model <- h2o.gbm(x = names(word.vectors), y = "category",
                     training_frame = data.split[[1]], validation_frame = data.split[[2]])

print("Predict!")
print(predict("school teacher having holidays every month", w2v.model, gbm.model))
print(predict("developer with 3+ Java experience, jumping", w2v.model, gbm.model))
print(predict("Financial accountant CPA preferred", w2v.model, gbm.model))