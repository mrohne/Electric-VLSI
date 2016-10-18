git stash
git checkout master
git svn rebase
git branch | grep / | sort | while read b; do echo Rebasing $b; git rebase master $b; done
git branch -D merged.old
git branch -M merged merged.old
git checkout -b merged master
git branch | grep / | sort | while read b; do echo Merging $b; git merge $b; done
git stash pop
git commit -am"Mirror only, please undo"
git push --mirror mirror
git push --mirror mirror
git push --mirror mirror
git reset --soft HEAD~1
