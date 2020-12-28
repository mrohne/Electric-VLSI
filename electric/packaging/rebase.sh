git stash
git checkout master
git svn rebase
git branch | grep / | sort | while read b; do echo Rebasing $b; git rebase --onto master master $b || break; done
git branch -D merged.old
git branch -M merged merged.old
git checkout -b merged master
git branch | grep / | sort | while read b; do echo Merging $b; git merge $b || break; done
git stash pop

