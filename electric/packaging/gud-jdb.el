;; gud-jdb
(add-hook 'jdb-mode-hook
	  (lambda ()
	    (add-to-list 'gud-jdb-sourcepath
			 "/opt/Electric/electric/electric-java")))

