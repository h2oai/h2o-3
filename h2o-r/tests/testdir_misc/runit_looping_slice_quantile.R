setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test exercises slices and quantiles from R.
#----------------------------------------------------------------------

options(error=traceback, warn=1)
# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_misc")

options(echo=TRUE)


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Binary columns start with 'b' and are 0/1.
# Distribution columns start with 'd' and are reals.

num_binary_cols = 3
num_rows = 100000

# Uniform distribution variable.
max_price = 1000000

# Normal distribution variable.
typical_price = 20
typical_price_stddev = 5

# Long tail distribution variable combines first two variables.
# combine_ratio comes from uniform variable and
# (1-combine_ratio) comes from the normal variable.
combine_ratio = 0.2


#----------------------------------------------------------------------
# Initialize H2O.
#----------------------------------------------------------------------

h2oTest.heading("BEGIN TEST")
conn <- new("H2OConnection", ip=myIP, port=myPort)


#----------------------------------------------------------------------
# Generate dataset.
#----------------------------------------------------------------------

h2oTest.heading("Generating the dataset...")

df.gen <- function() {
    lst = list()
    
    # Create binary columns.
    for (i in 1:num_binary_cols) {
        col_dat = rbinom(num_rows, 1, 0.5)
        col_name = sprintf("b%d", i)
        statement = sprintf("lst$%s <- col_dat", col_name)
        
        # Add new binary column to list.
        eval(parse(text=statement))
    }

    # Create distribution columns.
    d1 = runif(n = num_rows) * max_price

    d2_raw = rnorm(n = num_rows, mean = typical_price, sd = typical_price_stddev)
    d2 = pmax(d2_raw, 0)

    f = function(a, b) { c = runif(1); if (c < combine_ratio) return (a) else return (b); }
    d3 = mapply(f, d1, d2)
    
    # Add distribution columns to list.
    lst$d1 <- d1
    lst$d2 <- d2
    lst$d3 <- d3

    # Create data frame from list.
    df <- as.data.frame(lst)
    
    # Return the data frame.
    return (df)
}

set.seed(556677)
df = df.gen()
df = as.h2o(df, key = "orig.hex")


#----------------------------------------------------------------------
# Calculate quantile slices table (in surely the slowest possible way
# since R doesn't allow side-effects).
#----------------------------------------------------------------------

h2oTest.heading("Generating the slices table...")

slices.gen = function() {
    lst = list()

    num_slices = (2 ** num_binary_cols)
    
    # Create binary columns initialized to -1.
    for (i in 1:num_binary_cols) {
        col_name = sprintf("b%d", i)
        statement = sprintf("%s <- seq(from = -1, to = -1, length.out = %d)", col_name, num_slices)
        eval(parse(text=statement))
    }
    
    # Fill in values for binary columns.
    for (i in 1:num_slices) {
        value = i - 1
        
        for (j in 1:num_binary_cols) {
            bit = bitwAnd(bitwShiftR(value, (j-1)), 0x1)
            col_name = sprintf("b%d", j)
            statement = sprintf("%s[%d] <- %d", col_name, i, bit)
            eval(parse(text=statement))
        }        
    }

    # Add new binary column to list.
    for (i in 1:num_binary_cols) {
        col_name = sprintf("b%d", i)
        statement = sprintf("lst$%s <- %s", col_name, col_name)
        eval(parse(text=statement))
    }

    lst$d1_quantile_result = seq(from = -1, to = -1, length.out = num_slices)
    lst$d2_quantile_result = seq(from = -1, to = -1, length.out = num_slices)
    lst$d3_quantile_result = seq(from = -1, to = -1, length.out = num_slices)
    lst$wallclock_sec <- seq(from = -1, to = -1, length.out = num_slices)
    
    # Create data frame from list.
    df <- as.data.frame(lst)
    
    # Return the data frame.
    return (df)
}

slices = slices.gen()


#----------------------------------------------------------------------
# Do slices and calculate quantile.
#----------------------------------------------------------------------

h2oTest.heading("Doing each slice and calculating the quantile...")

h2o.removeLastValues <- function() {
    df <- h2o.ls()
    keys_to_remove <- grep("^Last\\.value\\.", perl=TRUE, x=df$Key, value=TRUE)
    # TODO: Why are there duplicates?  Probably a bug.
    h2o.rm(unique(keys_to_remove))
}

for (i in 1:nrow(slices)) {
    slice = slices[i,]
    print(slice)

    predicate = ""
    for (j in 1:num_binary_cols) {
        col_name = sprintf("b%d", j)
        statement = sprintf("value <- slice$%s", col_name)
        eval(parse(text=statement))
        if (value > 0) {
            if (nchar(predicate) == 0) {
                predicate = sprintf("(df$%s == 1)", col_name)
            }
            else {
                predicate = sprintf("%s & (df$%s == 1)", predicate, col_name)
            }
        }
    }

    # Create the slice.
    statement = sprintf("slice.df = df[%s,]", predicate)
    cat("SLICE: ", statement, "\n")
    
    start_sec = Sys.time()
    eval(parse(text=statement))
    print(dim(df))
    print(dim(slice.df))

    # Calculate the quantiles.
    q1 = quantile(x = slice.df$d1, probs = c(0.999))
    q2 = quantile(x = slice.df$d2, probs = c(0.999))
    q3 = quantile(x = slice.df$d3, probs = c(0.999))
    end_sec = Sys.time()
    delta_sec = end_sec - start_sec
    
    slices[i,"d1_quantile_result"] = q1
    slices[i,"d2_quantile_result"] = q2
    slices[i,"d3_quantile_result"] = q3
    slices[i,"wallclock_sec"] = delta_sec
    print(slices[i,])
    
    cat("Removing last values...\n")
    slice.df = 0
    gc()
    h2o.removeLastValues()
}

print(slices)


#----------------------------------------------------------------------
# Clean up.
#----------------------------------------------------------------------

h2oTest.PassBanner()


# library(debug)
# mtrace(.h2o.__remoteSend)
