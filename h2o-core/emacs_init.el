;; Set the debug option to enable a backtrace when a problem occurs.
;;(setq debug-on-error t)

;; TAGS!!!
;;rm TAGS; ctags -e --recurse=yes --extra=+q --fields=+fksaiS h2o-*/src/main/java h2o-*/src/test/java
(setq confirm-kill-emacs 'yes-or-no-p)

;; JavaDoc help on F1
(require 'javadoc-help)
(global-set-key [(f1)]          'javadoc-lookup)  ; F1 to lookup
(global-set-key [(shift f1)]    'javadoc-help)    ; Shift-F1 to bring up menu

;; JDEE.  For me: mostly the debugger
(add-to-list 'load-path (substitute-in-file-name "$DESK/Dropbox/Programs/Emacs/jdee-2.4.0.1/lisp"))

;; CEDET 
(add-to-list 'load-path (substitute-in-file-name "$DESK/Dropbox/Programs/Emacs/cedet-1.0.1/"))
(add-to-list 'load-path (substitute-in-file-name "$DESK/Dropbox/Programs/Emacs/cedet-1.0.1/common"))
(add-to-list 'load-path (substitute-in-file-name "$DESK/Dropbox/Programs/Emacs/cedet-1.0.1/eieio"))
(add-to-list 'load-path (substitute-in-file-name "$DESK/Dropbox/Programs/Emacs/cedet-1.0.1/ede"))
(add-to-list 'load-path (substitute-in-file-name "$DESK/Dropbox/Programs/Emacs/cedet-1.0.1/speedbar"))
(add-to-list 'load-path (substitute-in-file-name "$DESK/Dropbox/Programs/Emacs/cedet-1.0.1/semantic"))
(add-to-list 'load-path (substitute-in-file-name "$DESK/Dropbox/Programs/Emacs/cedet-1.0.1/srecode"))
(load-file (substitute-in-file-name "$DESK/Dropbox/Programs/Emacs/cedet-1.0.1/common/cedet.el"))

;;(global-ede-mode t) ;; Turn on EDE

;; Pretty-print Java (ok, overkill pretty)
;;(semantic-load-enable-excessive-code-helpers)
(semantic-load-enable-gaudy-code-helpers)

;; TAGs; symbol find and completion
(require 'semantic-ia)

;;Start inline completion
;; M-x semantic-complete-analyze-inline
;; This is a command that does completion inline (underlining the target symbol) and allows TAB to be used for completion purposes.

;; Automatically starting inline completion in idle time
;;   M-x global-semantic-idle-completions-mode
;; This is a minor mode which runs semantic-complete-analyze-inline-idle during idle time. Instead of trying to complete the symbol immediately, it will just display the possible completions, and underline the current symbol the cursor is on.

;; Starting for inline completion when "." is pressed
;;  (define-key your-mode-map-here "." 'semantic-complete-self-insert)
;; Binding semantic-complete-self-insert to a key will insert that key's text, as per self-insert-command, and then run the inline completion engine if there is appropriate context nearby.

;; Speedbar completion mode
;;   M-x semantic-speedbar-analysis
;; This will start Speedbar in a special mode. In this mode it will analyze the cursor location, and provide intelligent references. Unlike inline completion, a raw list of options is provided and you just need to click on the one you want. Sometimes you need to press g to force an update. 

;; Sets the basic indentation for Java source files to two spaces.
(defun my-jde-mode-hook ()
  (setq c-basic-offset 2))
(add-hook 'jde-mode-hook 'my-jde-mode-hook)

;; Finally drag in all of JDEE
(require 'jde)

;; BASH
(setq binary-process-input t)
(setq w32-quote-process-args ?\")
(setq shell-file-name "bash") ;; or sh if you rename your bash executable to sh.
(setenv "SHELL" shell-file-name)
(setq explicit-shell-file-name shell-file-name)
(setq explicit-sh-args '("-login" "-i"))
(setenv '"PS1" '"[\w] ")

;; eshell clear
(defun eshell/clear ()
  "04Dec2001 - sailor, to clear the eshell buffer."
  (interactive)
  (let ((inhibit-read-only t))
    (erase-buffer)))

;; Follow stack java stack traces
(defvar java-stack-trace-dir "src/")
(defun java-stack-trace-regexp-to-filename ()
  "Generates a relative filename from java-stack-trace regexp match data."
  (concat java-stack-trace-dir
          (replace-regexp-in-string "\\." "/" (match-string 1))
          (match-string 2)))

(add-to-list 'compilation-error-regexp-alist 'java-stack-trace)
(add-to-list 'compilation-error-regexp-alist-alist
  '(java-stack-trace .
    ("^[[:space:]]*at \\(\\(?:[[:lower:]]+\\.\\)+\\)[^(]+(\\([[:alnum:]]+\\.java\\):\\([[:digit:]]+\\))"
     java-stack-trace-regexp-to-filename 3)))

(custom-set-variables
  ;; custom-set-variables was added by Custom.
  ;; If you edit it by hand, you could mess it up, so be careful.
  ;; Your init file should contain only one such instance.
  ;; If there is more than one, they won't work right.
 '(jde-compile-option-debug (quote ("all" (t t t))))
 '(jde-compile-option-source (quote ("1.6")))
 '(jde-compile-option-sourcepath (quote ("./src")))
 '(jde-debugger (quote ("JDEbug")))
 '(jde-global-classpath (quote (".")))
 '(jde-javadoc-gen-destination-directory "./doc" t)
 '(jde-jdk-doc-url "c:/Program Files (x86)/Java/jdk1.7.0_03/jdk-6-doc/docs")
 '(jde-jdk-registry (quote (("1.6" . "$JAVA_HOME"))))
 '(show-paren-mode t)
 '(text-mode-hook (quote (text-mode-hook-identify)))
 '(transient-mark-mode t))
(custom-set-faces
  ;; custom-set-faces was added by Custom.
  ;; If you edit it by hand, you could mess it up, so be careful.
  ;; Your init file should contain only one such instance.
  ;; If there is more than one, they won't work right.
 )
