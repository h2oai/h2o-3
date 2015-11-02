# Bring master branch up-to-date locally
git checkout master
# My changes on top
git rebase
# This is what I want to test in private jenkins
git status

# Wipe out old private jenkins both locally and remotely
git branch -D cliffc_jenkins
git push origin :cliffc_jenkins
# Make the current stuff as a new cliffc_jenkins branch
git checkout -b cliffc_jenkins
# Push back to the origin the current stuff
git push --set-upstream origin cliffc_jenkins
