# How to contribute to a Solace Project

We'd love for you to contribute and welcome your help. Here are some guidelines to follow:

- [Issues and Bugs](#issue)
- [Submitting a fix](#submitting)
- [Feature Requests](#features)
- [Add a feature](#addFeature)
- [Questions](#questions)

## <a name="issue"></a> Did you find a issue?

* **Ensure the bug was not already reported** by searching on GitHub under [Issues](https://github.com/SolaceSamples/solace-samples-java/issues).

* If you're unable to find an open issue addressing the problem, [open a new one](https://github.com/SolaceSamples/solace-samples-java/issues/new). Be sure to include a **title and clear description**, as much relevant information as possible, and a **code sample** or an **executable test case** demonstrating the expected behavior that is not occurring.

## <a name="submitting"></a> Did you write a patch that fixes a bug?

Open a new GitHub pull request with the patch following the steps outlined below. Ensure the PR description clearly describes the problem and solution. Include the relevant issue number if applicable.

Before you submit your pull request consider the following guidelines:

* Search [GitHub](https://github.com/SolaceSamples/solace-samples-java/pulls) for an open or closed Pull Request
  that relates to your submission. You don't want to duplicate effort.

### Submitting a Pull Request

Please follow these steps for all pull requests. These steps are derived from the [GitHub flow](https://help.github.com/articles/github-flow/).

#### Step 1: Fork

Fork the project [on GitHub](https://github.com/SolaceSamples/solace-samples-java) and clone your fork
locally.

```sh
git clone https://github.com/<my-github-repo>/solace-samples-java
```

#### Step 2: Branch

Make your changes on a new git branch in your fork of the repository.

```sh
git checkout -b my-fix-branch master
```

#### Step 3: Commit

Commit your changes using a descriptive commit message.

```sh
git commit -a -m "Your Commit Message"
```

Note: the optional commit `-a` command line option will automatically "add" and "rm" edited files.

#### Step 4: Rebase (if possible)

Assuming you have not yet pushed your branch to origin, use `git rebase` (not `git merge`) to synchronize your work with the main
repository.

```sh
$ git fetch upstream
$ git rebase upstream/master
```

If you have not set the upstream, do so as follows:

```sh
$ git remote add upstream https://github.com/SolaceSamples/solace-samples-java
```

If you have already pushed your fork, then do not rebase. Instead merge any changes from master that are not already part of your branch.

#### Step 5: Push

Push your branch to your fork in GitHub:

```sh
git push origin my-fix-branch
```

#### Step 6: Pull Request

In GitHub, send a pull request to `solace-samples-java:master`.

When fixing an existing issue, use the [commit message keywords](https://help.github.com/articles/closing-issues-via-commit-messages/) to close the associated GitHub issue.

* If we suggest changes then:
  * Make the required updates.
  * Commit these changes to your branch (ex: my-fix-branch)

That's it! Thank you for your contribution!

## <a name="features"></a> **Do you have an ideas for a new feature or a change to an existing one?**

* Open a GitHub [enhancement request issue](https://github.com/SolaceSamples/solace-samples-java/issues/new) and describe the new functionality.

## <a name="addFeature"></a> **Steps to add a new feature**

* Add your feature source file under src/main/java/com/solace/samples/features/newFeature.java
* Update the build.gradle file by appending your source file name to the list of scripts  

        def scripts = [ 'topicPublisher':'com.solace.samples.TopicPublisher',  
                        'topicSubscriber':'com.solace.samples.TopicSubscriber',  
                        'newFeature':'com.solace.samples.features.newFeature')  
* Add your feature markdown file under \_docs/feature\_newFeature.md
* Update the \_docs/tutorials.yml to include the new feature by appending your feature name to the features list

        features:  
        - feature-1  
        - feature_newFeature  

##  <a name="questions"></a> Do you have questions about the source code?

* Ask any question about the code or how to use Solace messaging in the [Solace community](http://dev.solace.com/community/).
