# How to contribute to a Solace Project

We'd love for you to contribute and welcome your help. Here are some guidelines to follow:

- [Issues and Bugs](#issue)
- [Submitting a fix](#submitting)
- [Feature Requests](#features)
- [Questions](#questions) 

## <a name="issue"></a> Did you find a issue?

* **Ensure the bug was not already reported** by searching on GitHub under [Issues](https://github.com/SolaceSamples/solace-samples-template/issues).

* If you're unable to find an open issue addressing the problem, [open a new one](https://github.com/SolaceSamples/solace-samples-template/issues/new). Be sure to include a **title and clear description**, as much relevant information as possible, and a **code sample** or an **executable test case** demonstrating the expected behavior that is not occurring.

## <a name="submitting"></a> Did you write a patch that fixes a bug?

Open a new GitHub pull request with the patch following the steps outlined below. Ensure the PR description clearly describes the problem and solution. Include the relevant issue number if applicable.

Before you submit your pull request consider the following guidelines:

* Search [GitHub](https://github.com/SolaceSamples/solace-samples-template/pulls) for an open or closed Pull Request
  that relates to your submission. You don't want to duplicate effort.

### Submitting a Pull Request

Please follow these steps for all pull requests.

#### Step 1: Fork

Fork the project [on GitHub](https://github.com/SolaceSamples/solace-samples-template) and clone your fork
locally.

#### Step 2: Branch

Make your changes on a new git branch in your fork of the repository.

     ```shell
     git checkout -b my-fix-branch master
     ```

#### Step 3: Commit

Commit your changes using a descriptive commit message.

     ```shell
     git commit -a
     ```
  Note: the optional commit `-a` command line option will automatically "add" and "rm" edited files.

#### Step 4: Rebase 

Use `git rebase` (not `git merge`) to synchronize your work with the main
repository.

```shell
$ git fetch upstream
$ git rebase upstream/master
```

If you have not set the upstream, do so as follows:

```shell
$ git remote add upstream https://github.com/SolaceSamples/solace-samples-template
```

#### Step 5: Push

Push your branch to your fork in GitHub:

    ```shell
    git push origin my-fix-branch
    ```

#### Step 6: Pull Request

In GitHub, send a pull request to `solace-samples-template:master`.

* If we suggest changes then:
  * Make the required updates.
  * Commit these changes to your branch (ex: my-fix-branch)

That's it! Thank you for your contribution!

## <a name="features"></a> **Do you have an ideas for a new feature or a change to an existing one?**

* Open a GitHub [enhancement request issue](https://github.com/SolaceSamples/solace-samples-template/issues/new) and describe the new functionality.

##  <a name="features"></a> Do you have questions about the source code?

* Ask any question about the code or how to use Solace messaging in the [Solace community](http://dev.solace.com/community/).
