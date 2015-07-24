
# S3 Overload all standard operators.
# Just build a lazy-eval structure
Ops.Fr <- function(x,y) {
  assign("node", structure(new.env(), class="Fr"))
  node$op <- .Generic
  node$refcnt <- 0L
  node$l <- x
  node$r <- y
  node
}

# Ref-count down-count.  If it goes to zero, recursively ref-down-count the
# left & right, plus also remove the backing H2O store
.refdown <- function(x,xsub) {
  stopifnot(x$refcnt > 0 )
  assign("refcnt",x$refcnt - 1,envir=x)
  if( x$refcnt == 0 ) {
    if( is.environment(x$l) ) .refdown(x$l,paste0(xsub,"$l"))
    if( is.environment(x$r) ) .refdown(x$r,paste0(xsub,"$r"))
    print(paste("Time to remove",xsub,"from H2O"))
  }
}

# Overload Assignment!
# *f*ugly* - sorry!
assign("<-", function(x,y) {
  # Get a symbol for 'x'
  assign("xsub",substitute(x))
  # Evaluate complex LHS arguments, attempting to get an OLD value
  assign("e", try(eval(xsub,parent.frame()),silent=TRUE))
  # If the OLD value is a Frame, down the ref-cnt
  if( class(e) == "Fr" ) .refdown(e,xsub);
  # If the NEW value is a Frame, up the ref-cnt
  if( class(y) == "Fr" ) assign("refcnt",y$refcnt + 1,envir=y)

  # Dispatch to the original assign method.  Very ugly determine if the assign
  # is to a complex LHS or a simple LHS, and do either the simple or complex
  # Dispatch
  if( !is.symbol(xsub) && xsub[[1]]=="$" )   # "$<-" method and "[<-"
       assign(as.character(xsub[[3]]),y,envir=get(as.character(xsub[[2]]),parent.frame()))
  else assign(as.character(xsub     ),y,envir=                            parent.frame() )
})

# Internal recursive printer.  No smarts on repeated visitation
.pfr <- function(x){
  l <- if( is.environment(x$l) ) .pfr(x$l) else x$l
  r <- if( is.environment(x$r) ) .pfr(x$r) else x$r
  paste0(x$id," <- (",x$op," ",l," ",r,")#",x$refcnt)
}

# S3 overload print
"print.Fr" <- function(x) { print(.pfr(x)); invisible() }

#### 
# Cleanup prior fun temps
assign("fr",NULL)
assign("x",NULL)

# Build a "as-if from CSV" parsed file backed by H2O cluster
assign("fr",structure(new.env(), class="Fr"))
fr$id <- "iris.hex"
fr$op <- "from_file_iris.csv"
fr$refcnt <- 1L

print(fr)
x <- 3+fr
print(x)
y <- x*x
print(y)
x <- NULL
y <- NULL
print(y)

# Some sample exprs
x*x
3+fr+3
3*fr
gc()
rm(list=ls())
