assign("fr",NULL)
assign("x",NULL)
assign("fr",structure(new.env(), class="Fr"))
fr$id <- "iris.hex"
fr$op <- "from_file_iris.csv"
fr$refcnt <- 1L

Ops.Fr <- function(x,y) {
  assign("node", structure(new.env(), class="Fr"))
  node$op <- .Generic
  node$refcnt <- 0L
  node$l <- x
  node$r <- y
  node
}

.refdown <- function(x,xsub) {
  stopifnot(x$refcnt > 0 )
  print("refcnt adjust RHS--")
  assign("refcnt",x$refcnt - 1,envir=x)
  if( x$refcnt == 0 ) {
    if( is.environment(x$l) ) .refdown(x$l,paste0(xsub,"$l"))
    if( is.environment(x$r) ) .refdown(x$r,paste0(xsub,"$r"))
    print(paste("Time to remove",xsub,"from H2O"))
  }
}

assign("<-", function(x,y) {
  assign("xsub",substitute(x))
  assign("ysub",substitute(y))
  assign("e", try(eval(xsub,parent.frame()),silent=TRUE))
  assign("old", if(inherits(e,"try-error")) NULL else e)

  if( class(old) == "Fr" ) {
    .refdown(old,xsub);
  }
  if( class(y) == "Fr" ) {
    print("refcnt adjust RHS++ ")
    stopifnot(y$refcnt >= 0 )
    assign("refcnt",y$refcnt + 1,envir=y)
  }
  # "$<-" method and "[<-"
  if( !is.symbol(xsub) && xsub[[1]]=="$" ) 
    assign(as.character(xsub[[3]]),y,envir=get(as.character(xsub[[2]]),parent.frame()))
  else assign(as.character(xsub),y,envir=parent.frame())
})

.pfr <- function(x){
  l <- if( is.environment(x$l) ) .pfr(x$l) else x$l
  r <- if( is.environment(x$r) ) .pfr(x$r) else x$r
  paste0(x$id," <- (",x$op," ",l," ",r,")#",x$refcnt)
}

"print.Fr" <- function(x) { print(.pfr(x)); invisible() }

print(y)
x <- 3+fr
x <- NULL
y <- x*x
x*x
3+fr+3
3*fr
gc()
rm(list=ls())
