#
# THIS IS JUST FOR CONVENIENCE.
# THE BOOKLETS ARE BUILT BY GRADLE IN THE NIGHTLY BUILD.
#
# See h2o-3/h2o-docs/build.gradle
#
# (From root dir) ./gradlew booklets
#

build:
	pdflatex -halt-on-error GLM_Vignette
	bibtex GLM_Vignette
	pdflatex -halt-on-error GLM_Vignette
	pdflatex -halt-on-error GLM_Vignette

clean:
	rm -f *.bbl *.blg *.log *.aux *.toc *.synctex *.out *.pdf
	rm -f TSWLatexianTemp*

