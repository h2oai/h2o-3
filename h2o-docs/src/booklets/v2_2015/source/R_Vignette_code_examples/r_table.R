# Counts of the ages of all patients
head(as.data.frame(h2o.table(prostate.hex[,"AGE"])))


# Two-way table of ages (rows) and race (cols) of all patients
# Example: For the first row there is one count of a 43 year old that's labeled as RACE = 0
h2o.table(prostate.hex[,c("AGE","RACE")])
