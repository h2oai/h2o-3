;; Set the debug option to enable a backtrace when a problem occurs.
;;(setq debug-on-error t)

;; TAGS!!!
;;rm TAGS; ctags -e --recurse=yes --extra=+q --fields=+fksaiS h2o-*/src/main/java h2o-*/src/test/java
(setq confirm-kill-emacs 'yes-or-no-p)

;; JavaDoc help on F1
(add-to-list 'load-path (substitute-in-file-name "$DESK/Dropbox/Programs/Emacs/elisp"))
(require 'javadoc-help)
(global-set-key [(f1)]          'javadoc-lookup)  ; F1 to lookup
(global-set-key [(shift f1)]    'javadoc-help)    ; Shift-F1 to bring up menu

;;(global-ede-mode t) ;; Turn on EDE

;; Finally drag in all of JDEE
(require 'jdee)

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
 '(jde-compile-option-source (quote ("1.7")))
 '(jde-compile-option-sourcepath (quote ("./src")))
 '(jde-debugger (quote ("JDEbug")))
 '(jde-global-classpath (quote (".")))
 '(jde-javadoc-gen-destination-directory "./doc" t)
 '(jde-jdk-doc-url "c:/Program Files (x86)/Java/jdk1.7.0_03/jdk-6-doc/docs")
 '(jde-jdk-registry (quote (("1.7.0_65" . "C:/Program Files/Java/jdk1.7.0_65"))))
 '(jde-jdk (quote ("1.7.0_17")))
 '(show-paren-mode t)
 '(text-mode-hook (quote (text-mode-hook-identify)))
 '(transient-mark-mode t))
(custom-set-faces
  ;; custom-set-faces was added by Custom.
  ;; If you edit it by hand, you could mess it up, so be careful.
  ;; Your init file should contain only one such instance.
  ;; If there is more than one, they won't work right.
 )
