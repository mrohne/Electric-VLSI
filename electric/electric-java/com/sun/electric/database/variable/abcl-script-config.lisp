;; Add user's systems
(push (merge-pathnames #p".abcl/systems/" (user-homedir-pathname))
      asdf:*central-registry*)
