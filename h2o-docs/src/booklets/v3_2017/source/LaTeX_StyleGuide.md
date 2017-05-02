Booklet Workflow 
---------------------- 

- Get latest version of .tex file from h2o-3 repo 
- Make revisions (please refer to "Notes" below before making changes) 
- Make a PDF to test for errors - if errors, please message me to troubleshoot, don't push until fixed 
- Delete baggage files (please refer to "Notes" below) 
- Push changes to master 

Notes 
------- 

- Please search for %% - these contain questions about sections that need more work. (% = explanation of function) 
- Please don't change any formatting or \ operators - the doc should generate without any errors, if this is not the case please let me know before changing anything. 
- Please use the following syntax: 
      - \texttt{} for parameter references 
      - \begin{lstlisting}[breaklines,basicstyle=\ttfamily]  & \end{lstlisting} for code blocks (lstlisting prevents margin overrun and places line breaks so that users can copy/paste code) 
      - {\url{}} for links. Please use this format so that it will display the link for print and break the lines nicely. 
- Please remember to use a \ before a _ if you are using \texttt - if you don't add a \ before the _, it can show up as italicized. I'll try to fix these if I find them but this is especially relevant for parameter names using underscores. 
- If you are trying to get a character to display that LaTeX thinks is code/math (for example, # or ~), try surrounding the character with $ or adding a \ before it. 
- Please try not to save if you have errors - they can be time-consuming to find & fix. If something's generating a ! by the line number, please let me know so I can help troubleshoot. 
- LaTeX can generate a lot of baggage files (.aux, .log, .out, etc) - please clean these out before pushing changes so that the folder stays clean.